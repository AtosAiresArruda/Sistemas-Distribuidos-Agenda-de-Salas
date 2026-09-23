package br.edu.utfpr.sd.cliente;

import br.edu.utfpr.sd.comum.ConexaoJson;
import br.edu.utfpr.sd.comum.Json;
import br.edu.utfpr.sd.comum.ValidacaoCadastro;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Cliente de console para o CRUD do proprio cadastro (usuario comum).
 * Pode se conectar a qualquer servidor da turma que siga o protocolo.
 */
public class Cliente {

    private final BufferedReader teclado;
    private final String ip;
    private final int porta;

    private ConexaoJson conexao;
    // Protocolo 3.2: o cliente guarda o token em memoria durante a sessao
    private String token;

    public Cliente(BufferedReader teclado, String ip, int porta) {
        this.teclado = teclado;
        this.ip = ip;
        this.porta = porta;
    }

    public static void main(String[] args) throws IOException {
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        System.out.println("Qual o IP do servidor? ");
        String ip = teclado.readLine().trim();

        System.out.println("Qual a Porta do servidor? ");
        int porta;
        try {
            porta = Integer.parseInt(teclado.readLine().trim());
        } catch (NumberFormatException e) {
            System.err.println("Porta invalida. O valor deve ser um numero.");
            return;
        }

        new Cliente(teclado, ip, porta).executar();
    }

    public void executar() throws IOException {
        while (true) {
            System.out.println();
            System.out.println("===== Agenda de Salas - " + ip + ":" + porta + (token == null ? " (sem sessao)" : " (logado)") + " =====");
            System.out.println("1 - Cadastrar usuario (register)");
            System.out.println("2 - Login");
            System.out.println("3 - Ler meu cadastro (read_user)");
            System.out.println("4 - Atualizar meu cadastro (update_user)");
            System.out.println("5 - Apagar meu cadastro (delete_user)");
            System.out.println("6 - Logout");
            System.out.println("7 - Enviar JSON manual");
            System.out.println("0 - Sair");
            System.out.print("Opcao: ");

            String opcao = teclado.readLine();
            if (opcao == null) {
                break;
            }
            switch (opcao.trim()) {
                case "1" -> register();
                case "2" -> login();
                case "3" -> readUser();
                case "4" -> updateUser();
                case "5" -> deleteUser();
                case "6" -> logout();
                case "7" -> jsonManual();
                case "0" -> {
                    desconectar();
                    System.out.println("Saindo...");
                    return;
                }
                default -> System.out.println("Opcao invalida.");
            }
        }
        desconectar();
    }

    private void register() throws IOException {
        // O servidor grava user e email em minusculas (protocolo 2.12)
        String email = perguntar("Email: ").toLowerCase(Locale.ROOT);
        String user = perguntar("Usuario: ").toLowerCase(Locale.ROOT);
        String password = perguntar("Senha: ");
        if (!dadosValidos(ValidacaoCadastro.email(email), ValidacaoCadastro.user(user), ValidacaoCadastro.password(password))) {
            return;
        }

        JsonObject req = requisicao("register");
        req.addProperty("email", email);
        req.addProperty("user", user);
        req.addProperty("password", password);
        enviar(req);
    }

    private void login() throws IOException {
        JsonObject req = requisicao("login");
        req.addProperty("email", perguntar("Email: "));
        req.addProperty("password", perguntar("Senha: "));

        JsonObject resp = enviar(req);
        if (resp != null && "200".equals(texto(resp, "status"))) {
            token = texto(resp, "token");
            System.out.println("Perfil: " + texto(resp, "role"));
        }
    }

    private void readUser() throws IOException {
        if (!exigirSessao()) {
            return;
        }
        JsonObject resp = enviar(requisicaoComToken("read_user"));
        if (resp != null && "200".equals(texto(resp, "status"))) {
            System.out.println("Usuario:     " + texto(resp, "user"));
            System.out.println("Email:       " + texto(resp, "email"));
            System.out.println("Perfil:      " + texto(resp, "role"));
            System.out.println("Criado em:   " + texto(resp, "created_at"));
        }
    }

    private void updateUser() throws IOException {
        if (!exigirSessao()) {
            return;
        }
        System.out.println("Deixe em branco o que nao quiser alterar. O email nao pode ser alterado.");
        String user = perguntar("Novo usuario: ").toLowerCase(Locale.ROOT);
        String password = perguntar("Nova senha: ");
        // Campo vazio significa "nao alterar", entao so os preenchidos sao conferidos
        if (!dadosValidos(user.isEmpty() ? null : ValidacaoCadastro.user(user),
                password.isEmpty() ? null : ValidacaoCadastro.password(password))) {
            return;
        }

        JsonObject req = requisicaoComToken("update_user");
        req.addProperty("user", user);
        req.addProperty("password", password);
        enviar(req);
    }

    /** Mostra os problemas encontrados (null = campo valido) e diz se a requisicao pode ser enviada. */
    private boolean dadosValidos(String... problemas) {
        boolean valido = true;
        for (String problema : problemas) {
            if (problema != null) {
                System.out.println("!! " + problema);
                valido = false;
            }
        }
        if (!valido) {
            System.out.println("Requisicao nao enviada. Corrija os dados e tente novamente.");
        }
        return valido;
    }

    private void deleteUser() throws IOException {
        if (!exigirSessao()) {
            return;
        }
        JsonObject req = requisicaoComToken("delete_user");
        req.addProperty("password", perguntar("Confirme sua senha: "));

        JsonObject resp = enviar(req);
        if (resp != null && "200".equals(texto(resp, "status"))) {
            token = null;
        }
    }

    private void logout() throws IOException {
        if (!exigirSessao()) {
            return;
        }
        JsonObject resp = enviar(requisicaoComToken("logout"));
        if (resp != null && "200".equals(texto(resp, "status"))) {
            token = null;
        }
    }

    /** Envia uma linha digitada sem alteracoes, para testar como o servidor reage. */
    private void jsonManual() throws IOException {
        String linha = perguntar("JSON: ");
        try {
            conectarSeNecessario();
            conexao.enviarTexto(linha);
            receber();
        } catch (IOException e) {
            falhaDeConexao(e);
        }
    }

    /** Envia a requisicao e devolve a resposta, ou null se nao houve resposta valida. */
    private JsonObject enviar(JsonObject requisicao) {
        try {
            conectarSeNecessario();
            conexao.enviar(requisicao);
            return receber();
        } catch (IOException e) {
            falhaDeConexao(e);
            return null;
        }
    }

    private JsonObject receber() throws IOException {
        String linha = conexao.receber();
        if (linha == null) {
            System.out.println("O servidor encerrou a conexao. Ela sera reaberta na proxima operacao.");
            desconectar();
            return null;
        }

        JsonObject resposta;
        try {
            resposta = Json.parseObjeto(linha);
        } catch (JsonParseException e) {
            System.out.println("Resposta do servidor nao e um JSON valido.");
            return null;
        }

        String status = texto(resposta, "status");
        System.out.println(">> " + status + " - " + texto(resposta, "message"));
        // Aba "Codigos de Status": no 401 o cliente descarta o token e volta ao login
        if ("401".equals(status) && token != null) {
            token = null;
            System.out.println("Sessao descartada. Faca login novamente.");
        }
        return resposta;
    }

    private void conectarSeNecessario() throws IOException {
        if (conexao == null) {
            System.out.println("Conectando com " + ip + ":" + porta + "...");
            conexao = new ConexaoJson(new Socket(ip, porta), "Cliente");
        }
    }

    private void falhaDeConexao(IOException e) {
        if (e instanceof UnknownHostException) {
            System.err.println("Servidor " + ip + " nao encontrado.");
        } else {
            System.err.println("Erro de comunicacao com " + ip + ":" + porta + ": " + e.getMessage());
        }
        desconectar();
    }

    private void desconectar() {
        if (conexao != null) {
            try {
                conexao.close();
            } catch (IOException ignored) {
                // A conexao ja esta sendo descartada
            }
            conexao = null;
        }
    }

    private boolean exigirSessao() {
        if (token == null) {
            System.out.println("Faca login primeiro.");
            return false;
        }
        return true;
    }

    private JsonObject requisicao(String op) {
        JsonObject req = new JsonObject();
        req.addProperty("op", op);
        return req;
    }

    private JsonObject requisicaoComToken(String op) {
        JsonObject req = requisicao(op);
        req.addProperty("token", token);
        return req;
    }

    private String perguntar(String rotulo) throws IOException {
        System.out.print(rotulo);
        String linha = teclado.readLine();
        return linha == null ? "" : linha;
    }

    /** Valor string de um campo da resposta; tolera servidores que omitam o campo. */
    private static String texto(JsonObject obj, String chave) {
        JsonElement valor = obj.get(chave);
        return valor != null && valor.isJsonPrimitive() ? valor.getAsString() : "";
    }
}
