package br.edu.utfpr.sd.servidor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.edu.utfpr.sd.comum.ConexaoJson;
import br.edu.utfpr.sd.comum.Json;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Testa as mensagens da Entrega Parcial 01 contra um servidor real numa porta local. */
class ServidorTest {

    private static final String EMAIL = "joao.silva@email.com";
    private static final String TOKEN_FALSO = "c0fc3c713f09a43384ac08f7d91fca430dcbc6466fff9284ce4571bdc2c8f9f9";

    @TempDir
    Path pasta;

    private ServerSocket serverSocket;
    private ConexaoJson conexao;

    @BeforeEach
    void iniciar() throws Exception {
        BancoDados banco = new BancoDados(pasta.resolve("teste.db").toString());
        serverSocket = new ServerSocket(0);
        Thread servidor = new Thread(() -> {
            try {
                Servidor.atender(serverSocket, banco);
            } catch (IOException fechado) {
                // Fim do teste
            }
        });
        servidor.setDaemon(true);
        servidor.start();
        conexao = novaConexao();
    }

    @AfterEach
    void encerrar() throws IOException {
        conexao.close();
        serverSocket.close();
    }

    @Test
    void register() throws IOException {
        assertResposta(registrar("joao", EMAIL, "senha123"), "register_response", "201", "Usuario cadastrado com sucesso");
        assertResposta(registrar("joao", "outro@email.com", "senha123"), "register_response", "409", "Usuario ou email ja cadastrado");
        assertResposta(registrar("maria", EMAIL, "senha123"), "register_response", "409", "Usuario ou email ja cadastrado");

        String invalido = "Dados de cadastro em formato invalido";
        assertResposta(registrar("joao1", "a@b.com", "senha123"), "register_response", "400", invalido);
        assertResposta(registrar("ana", "sem-arroba", "senha123"), "register_response", "400", invalido);
        assertResposta(registrar("ana", "ana@email.com", "senha 123"), "register_response", "400", invalido);
        assertResposta(registrar("ana", "ana@email.com", ""), "register_response", "400", invalido);
        assertResposta(enviar("{\"op\":\"register\",\"email\":\"ana@email.com\",\"user\":\"ana\"}"), "register_response", "400", invalido);
        assertResposta(enviar("{\"op\":\"register\",\"email\":\"ana@email.com\",\"user\":null,\"password\":\"a1\"}"), "register_response", "400", invalido);
        assertResposta(enviar("{\"op\":\"register\",\"email\":\"ana@email.com\",\"user\":\"ana\",\"password\":123}"), "register_response", "400", invalido);
    }

    @Test
    void loginELogout() throws IOException {
        registrar("joao", EMAIL, "senha123");

        JsonObject login = logar(EMAIL, "senha123");
        assertResposta(login, "login_response", "200", "Login realizado com sucesso");
        String token = login.get("token").getAsString();
        assertTrue(token.matches("^[a-f0-9]{64}$"));
        assertEquals("user", login.get("role").getAsString());

        assertResposta(logar(EMAIL, "senha123"), "login_response", "409", "Usuario ja possui sessao ativa");
        assertResposta(logar(EMAIL, "errada1"), "login_response", "401", "Email ou senha incorretos");
        assertResposta(logar("ninguem@email.com", "senha123"), "login_response", "401", "Email ou senha incorretos");
        assertResposta(logar(EMAIL, "sen@ha"), "login_response", "400", "Email ou senha em formato invalido");

        assertResposta(comToken("logout", token), "logout_response", "200", "Logout realizado com sucesso");
        assertResposta(comToken("logout", token), "logout_response", "401", "Token invalido ou expirado");
        assertResposta(comToken("logout", "abc"), "logout_response", "400", "Token em formato invalido");
        assertResposta(enviar("{\"op\":\"logout\"}"), "logout_response", "401", "Token invalido ou expirado");

        // Depois do logout um novo login e aceito e gera outro token
        JsonObject novoLogin = logar(EMAIL, "senha123");
        assertResposta(novoLogin, "login_response", "200", "Login realizado com sucesso");
        assertTrue(!token.equals(novoLogin.get("token").getAsString()));
    }

    @Test
    void readUser() throws IOException {
        String token = cadastrarELogar();

        JsonObject resp = comToken("read_user", token);
        assertResposta(resp, "read_user_response", "200", "Consulta realizada com sucesso");
        assertEquals("joao", resp.get("user").getAsString());
        assertEquals(EMAIL, resp.get("email").getAsString());
        assertEquals("user", resp.get("role").getAsString());
        assertTrue(resp.get("created_at").getAsString().matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$"));
        assertTrue(!resp.has("password"));

        assertResposta(comToken("read_user", TOKEN_FALSO), "read_user_response", "401", "Token invalido ou expirado");
        assertResposta(comToken("read_user", "xyz"), "read_user_response", "400", "Token em formato invalido");
    }

    @Test
    void updateUser() throws IOException {
        registrar("maria", "maria@email.com", "senha123");
        String token = cadastrarELogar();

        assertResposta(atualizar(token, "joana", ""), "update_user_response", "200", "Dados atualizados com sucesso");
        // O token continua valido depois de trocar o proprio user
        assertEquals("joana", comToken("read_user", token).get("user").getAsString());

        assertResposta(atualizar(token, "", "novasenha1"), "update_user_response", "200", "Dados atualizados com sucesso");
        assertResposta(atualizar(token, "maria", ""), "update_user_response", "409", "Usuario ja esta em uso");

        String invalido = "Dados em formato invalido";
        assertResposta(atualizar(token, "joana2", ""), "update_user_response", "400", invalido);
        assertResposta(enviar("{\"op\":\"update_user\",\"token\":\"" + token + "\",\"user\":null,\"password\":\"\"}"),
                "update_user_response", "400", invalido);
        assertResposta(enviar("{\"op\":\"update_user\",\"token\":\"" + token + "\",\"user\":\"\",\"password\":\"\",\"email\":\"x@y.com\"}"),
                "update_user_response", "400", invalido);
        assertResposta(atualizar(TOKEN_FALSO, "pedro", ""), "update_user_response", "401", "Token invalido ou expirado");

        // A nova senha vale no proximo login
        comToken("logout", token);
        assertResposta(logar(EMAIL, "novasenha1"), "login_response", "200", "Login realizado com sucesso");
    }

    @Test
    void deleteUser() throws IOException {
        String token = cadastrarELogar();

        assertResposta(apagar(token, "errada1"), "delete_user_response", "401", "Token invalido ou expirado");
        assertResposta(apagar(token, "sen ha"), "delete_user_response", "400", "Senha em formato invalido");
        assertResposta(apagar(token, "senha123"), "delete_user_response", "200", "Usuario removido com sucesso");

        // O token e invalidado junto e o cadastro deixa de existir
        assertResposta(comToken("read_user", token), "read_user_response", "401", "Token invalido ou expirado");
        assertResposta(logar(EMAIL, "senha123"), "login_response", "401", "Email ou senha incorretos");
    }

    @Test
    void errosDeProtocolo() throws IOException {
        assertResposta(enviar("isto nao e json"), "error", "400", "Requisicao invalida");
        assertResposta(enviar("{\"op\":\"login\""), "error", "400", "Requisicao invalida");
        assertResposta(enviar("{\"email\":\"a@b.com\"}"), "error", "400", "Requisicao invalida");
        assertResposta(enviar("{\"op\":\"voar\"}"), "error", "400", "Operacao desconhecida");
        assertResposta(enviar("{\"op\":\"x\",\"lixo\":\"" + "a".repeat(9000) + "\"}"),
                "error", "400", "Mensagem excede o tamanho maximo");

        // Campos desconhecidos sao ignorados e a conexao continua utilizavel
        assertResposta(enviar("{\"op\":\"register\",\"email\":\"" + EMAIL + "\",\"user\":\"joao\",\"password\":\"senha123\",\"extra\":\"1\"}"),
                "register_response", "201", "Usuario cadastrado com sucesso");
    }

    @Test
    void servidorAtendeVariosClientes() throws IOException {
        registrar("joao", EMAIL, "senha123");
        try (ConexaoJson outro = novaConexao()) {
            outro.enviarTexto("{\"op\":\"login\",\"email\":\"" + EMAIL + "\",\"password\":\"senha123\"}");
            assertResposta(Json.parseObjeto(outro.receber()), "login_response", "200", "Login realizado com sucesso");
        }
        // A sessao aberta pelo outro cliente continua ativa
        assertResposta(logar(EMAIL, "senha123"), "login_response", "409", "Usuario ja possui sessao ativa");
    }

    private String cadastrarELogar() throws IOException {
        registrar("joao", EMAIL, "senha123");
        return logar(EMAIL, "senha123").get("token").getAsString();
    }

    private JsonObject registrar(String user, String email, String password) throws IOException {
        return enviar("{\"op\":\"register\",\"email\":\"" + email + "\",\"user\":\"" + user + "\",\"password\":\"" + password + "\"}");
    }

    private JsonObject logar(String email, String password) throws IOException {
        return enviar("{\"op\":\"login\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private JsonObject atualizar(String token, String user, String password) throws IOException {
        return enviar("{\"op\":\"update_user\",\"token\":\"" + token + "\",\"user\":\"" + user + "\",\"password\":\"" + password + "\"}");
    }

    private JsonObject apagar(String token, String password) throws IOException {
        return enviar("{\"op\":\"delete_user\",\"token\":\"" + token + "\",\"password\":\"" + password + "\"}");
    }

    private JsonObject comToken(String op, String token) throws IOException {
        return enviar("{\"op\":\"" + op + "\",\"token\":\"" + token + "\"}");
    }

    private JsonObject enviar(String linha) throws IOException {
        conexao.enviarTexto(linha);
        return Json.parseObjeto(conexao.receber());
    }

    private ConexaoJson novaConexao() throws IOException {
        return new ConexaoJson(new Socket("127.0.0.1", serverSocket.getLocalPort()), "Teste");
    }

    private static void assertResposta(JsonObject resposta, String op, String status, String message) {
        assertEquals(op, resposta.get("op").getAsString(), resposta.toString());
        assertEquals(status, resposta.get("status").getAsString(), resposta.toString());
        assertEquals(message, resposta.get("message").getAsString(), resposta.toString());
        // Protocolo 2.1: 'op' e sempre a primeira chave
        assertEquals("op", resposta.keySet().iterator().next());
    }
}
