package br.edu.utfpr.sd.servidor;

import br.edu.utfpr.sd.comum.ConexaoJson;
import br.edu.utfpr.sd.comum.Json;
import br.edu.utfpr.sd.servidor.BancoDados.Usuario;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Thread que atende um cliente: le cada requisicao, processa e envia exatamente uma resposta. */
public class TratadorCliente extends Thread {

    // Protocolo 1.9: conexao encerrada apos 300 s de inatividade (o token continua valido)
    private static final int TIMEOUT_INATIVIDADE_MS = 300_000;

    // Aba "Dicionario" do protocolo
    private static final Pattern USER = Pattern.compile("^[a-z]{1,30}$");
    private static final Pattern PASSWORD = Pattern.compile("^[A-Za-z0-9]{1,20}$");
    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9.]+@[a-z0-9]+(\\.[a-z]+){1,2}$");
    private static final Pattern TOKEN = Pattern.compile("^[a-f0-9]{64}$");

    private static final String TOKEN_INVALIDO = "Token invalido ou expirado";

    /** Interrompe a operacao com uma resposta de erro (status e message da aba de mensagens). */
    private static final class Falha extends Exception {
        final String status;

        Falha(String status, String message) {
            super(message);
            this.status = status;
        }
    }

    private final Socket socket;
    private final BancoDados banco;

    public TratadorCliente(Socket socket, BancoDados banco) {
        this.socket = socket;
        this.banco = banco;
    }

    @Override
    public void run() {
        String remoto = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        System.out.println("Nova thread de comunicacao iniciada com cliente: " + remoto);

        try (ConexaoJson conexao = new ConexaoJson(socket, "Servidor")) {
            socket.setSoTimeout(TIMEOUT_INATIVIDADE_MS);
            while (true) {
                String linha;
                try {
                    linha = conexao.receber();
                } catch (ConexaoJson.MensagemMuitoGrandeException e) {
                    conexao.enviar(Json.resposta("error", "400", "Mensagem excede o tamanho maximo"));
                    continue;
                } catch (SocketTimeoutException e) {
                    System.out.println("Cliente " + remoto + " inativo por 300 s; encerrando a conexao.");
                    break;
                }
                if (linha == null) {
                    break;
                }
                conexao.enviar(processar(linha));
            }
        } catch (IOException e) {
            System.err.println("Problema de I/O com o cliente " + remoto + ": " + e.getMessage());
        }
        System.out.println("Conexao encerrada com cliente: " + remoto);
    }

    /** Processa uma requisicao e devolve a resposta; nunca lanca excecao (protocolo 4.5). */
    JsonObject processar(String linha) {
        JsonObject requisicao;
        try {
            requisicao = Json.parseObjeto(linha);
        } catch (JsonParseException e) {
            return Json.resposta("error", "400", "Requisicao invalida");
        }

        JsonElement opElemento = requisicao.get("op");
        if (!ehString(opElemento)) {
            return Json.resposta("error", "400", "Requisicao invalida");
        }
        String op = opElemento.getAsString();
        String opResposta = op + "_response";

        try {
            return switch (op) {
                case "register" -> register(requisicao);
                case "login" -> login(requisicao);
                case "logout" -> logout(requisicao);
                case "read_user" -> readUser(requisicao);
                case "update_user" -> updateUser(requisicao);
                case "delete_user" -> deleteUser(requisicao);
                default -> Json.resposta("error", "400", "Operacao desconhecida");
            };
        } catch (Falha f) {
            return Json.resposta(opResposta, f.status, f.getMessage());
        } catch (Exception e) {
            System.err.println("Erro interno ao processar '" + op + "': " + e);
            return Json.resposta(opResposta, "500", "Erro interno do servidor");
        }
    }

    private JsonObject register(JsonObject req) throws Exception {
        String formatoInvalido = "Dados de cadastro em formato invalido";
        String email = minusculas(texto(req, "email", formatoInvalido));
        String user = minusculas(texto(req, "user", formatoInvalido));
        String password = texto(req, "password", formatoInvalido);
        if (!confere(EMAIL, email) || !confere(USER, user) || !confere(PASSWORD, password)) {
            throw new Falha("400", formatoInvalido);
        }

        if (!banco.cadastrar(user, email, password)) {
            throw new Falha("409", "Usuario ou email ja cadastrado");
        }
        return Json.resposta("register_response", "201", "Usuario cadastrado com sucesso");
    }

    private JsonObject login(JsonObject req) throws Exception {
        String formatoInvalido = "Email ou senha em formato invalido";
        String email = minusculas(texto(req, "email", formatoInvalido));
        String password = texto(req, "password", formatoInvalido);
        if (!confere(EMAIL, email) || !confere(PASSWORD, password)) {
            throw new Falha("400", formatoInvalido);
        }

        // Usuario inexistente e senha errada tem a mesma resposta, para nao revelar quem existe
        Usuario usuario = banco.autenticar(email, password);
        if (usuario == null) {
            throw new Falha("401", "Email ou senha incorretos");
        }
        String token = banco.abrirSessao(usuario.id());
        if (token == null) {
            throw new Falha("409", "Usuario ja possui sessao ativa");
        }

        JsonObject resposta = Json.resposta("login_response", "200", "Login realizado com sucesso");
        resposta.addProperty("token", token);
        resposta.addProperty("role", usuario.role());
        return resposta;
    }

    private JsonObject logout(JsonObject req) throws Exception {
        String token = token(req, "Token em formato invalido");
        if (!banco.invalidarToken(token)) {
            throw new Falha("401", TOKEN_INVALIDO);
        }
        return Json.resposta("logout_response", "200", "Logout realizado com sucesso");
    }

    private JsonObject readUser(JsonObject req) throws Exception {
        Usuario usuario = sessao(token(req, "Token em formato invalido"));

        JsonObject resposta = Json.resposta("read_user_response", "200", "Consulta realizada com sucesso");
        resposta.addProperty("user", usuario.user());
        resposta.addProperty("email", usuario.email());
        resposta.addProperty("role", usuario.role());
        resposta.addProperty("created_at", usuario.createdAt());
        return resposta;
    }

    private JsonObject updateUser(JsonObject req) throws Exception {
        String formatoInvalido = "Dados em formato invalido";
        // O email nao pode ser alterado: a simples presenca da chave e rejeitada
        if (req.has("email")) {
            throw new Falha("400", formatoInvalido);
        }
        String token = token(req, formatoInvalido);
        String user = minusculas(texto(req, "user", formatoInvalido));
        String password = texto(req, "password", formatoInvalido);
        // String vazia significa "nao alterar"
        if ((!user.isEmpty() && !confere(USER, user)) || (!password.isEmpty() && !confere(PASSWORD, password))) {
            throw new Falha("400", formatoInvalido);
        }

        Usuario usuario = sessao(token);
        if (!banco.atualizar(usuario.id(), user.isEmpty() ? null : user, password.isEmpty() ? null : password)) {
            throw new Falha("409", "Usuario ja esta em uso");
        }
        return Json.resposta("update_user_response", "200", "Dados atualizados com sucesso");
    }

    private JsonObject deleteUser(JsonObject req) throws Exception {
        String formatoInvalido = "Senha em formato invalido";
        String token = token(req, formatoInvalido);
        String password = texto(req, "password", formatoInvalido);
        if (!confere(PASSWORD, password)) {
            throw new Falha("400", formatoInvalido);
        }

        Usuario usuario = sessao(token);
        // Senha incorreta tambem responde 401; o protocolo so define esta mensagem para o 401
        if (!banco.senhaConfere(usuario.id(), password)) {
            throw new Falha("401", TOKEN_INVALIDO);
        }
        if (!banco.removerUsuario(usuario.id())) {
            throw new Falha("403", "Nao e possivel remover o ultimo administrador");
        }
        return Json.resposta("delete_user_response", "200", "Usuario removido com sucesso");
    }

    /**
     * Le o token da requisicao. Token ausente responde 401 (aba "Codigos de Status");
     * nulo, de outro tipo ou fora do formato responde 400.
     */
    private static String token(JsonObject req, String formatoInvalido) throws Falha {
        if (!req.has("token")) {
            throw new Falha("401", TOKEN_INVALIDO);
        }
        String token = texto(req, "token", formatoInvalido);
        if (!confere(TOKEN, token)) {
            throw new Falha("400", formatoInvalido);
        }
        return token;
    }

    /** Dono do token, renovando sua validade; token inexistente, expirado ou invalidado responde 401. */
    private Usuario sessao(String token) throws Exception {
        Usuario usuario = banco.usuarioDoToken(token);
        if (usuario == null) {
            throw new Falha("401", TOKEN_INVALIDO);
        }
        return usuario;
    }

    /** Campo obrigatorio do tipo string; ausente, nulo ou de outro tipo responde 400 (protocolo 2.10). */
    private static String texto(JsonObject req, String chave, String formatoInvalido) throws Falha {
        JsonElement valor = req.get(chave);
        if (!ehString(valor)) {
            throw new Falha("400", formatoInvalido);
        }
        return valor.getAsString();
    }

    private static boolean ehString(JsonElement valor) {
        return valor != null && valor.isJsonPrimitive() && valor.getAsJsonPrimitive().isString();
    }

    private static boolean confere(Pattern formato, String valor) {
        return formato.matcher(valor).matches();
    }

    // Protocolo 2.12: 'user' e 'email' sao sempre gravados em minusculas
    private static String minusculas(String valor) {
        return valor.toLowerCase(Locale.ROOT);
    }
}
