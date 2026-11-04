package br.edu.utfpr.sd.servidor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.edu.utfpr.sd.comum.ConexaoJson;
import br.edu.utfpr.sd.comum.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Testa as operacoes de administrador da Entrega Parcial 2.1 contra um servidor real numa porta local. */
class AdminTest {

    private static final String ADMIN_EMAIL = "admin@admin.com";
    private static final String TOKEN_FALSO = "c0fc3c713f09a43384ac08f7d91fca430dcbc6466fff9284ce4571bdc2c8f9f9";
    private static final String PERMISSAO = "Permissao insuficiente";
    private static final String DADOS = "Dados em formato invalido";
    private static final String ULTIMO_ADMIN = "Nao e possivel remover o ultimo administrador";

    @TempDir
    Path pasta;

    private ServerSocket serverSocket;
    private ConexaoJson conexao;
    private String tokenAdmin;

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
        conexao = new ConexaoJson(new Socket("127.0.0.1", serverSocket.getLocalPort()), "Teste");
        tokenAdmin = token(logar(ADMIN_EMAIL, "admin"));
    }

    @AfterEach
    void encerrar() throws IOException {
        conexao.close();
        serverSocket.close();
    }

    @Test
    void adminPadraoExisteECadastroNaoRepeteEmailOuUser() throws IOException {
        JsonObject resp = comToken("read_user", tokenAdmin);
        assertEquals("admin", resp.get("user").getAsString());
        assertEquals(ADMIN_EMAIL, resp.get("email").getAsString());
        assertEquals("admin", resp.get("role").getAsString());

        // A busca de duplicidade cobre usuarios comuns e administradores
        String cadastrado = "Usuario ou email ja cadastrado";
        assertResposta(registrar("outro", ADMIN_EMAIL, "senha123"), "register_response", "409", cadastrado);
        assertResposta(registrar("admin", "outro@email.com", "senha123"), "register_response", "409", cadastrado);
    }

    @Test
    void usuarioComumNaoUsaOperacoesDeAdmin() throws IOException {
        String tokenJoao = cadastrarELogar("joao", "joao@email.com");

        assertResposta(comToken("admin_list_users", tokenJoao), "admin_list_users_response", "403", PERMISSAO);
        assertResposta(lerAlvo(tokenJoao, "admin"), "admin_read_user_response", "403", PERMISSAO);
        assertResposta(atualizarAlvo(tokenJoao, "joao", "", "", "admin"), "admin_update_user_response", "403", PERMISSAO);
        assertResposta(apagarAlvo(tokenJoao, "admin"), "admin_delete_user_response", "403", PERMISSAO);
        // O role enviado pelo cliente e ignorado (protocolo 3.14)
        assertResposta(enviar("{\"op\":\"admin_list_users\",\"token\":\"" + tokenJoao + "\",\"role\":\"admin\"}"),
                "admin_list_users_response", "403", PERMISSAO);
        // O 403 nao derruba a sessao
        assertResposta(comToken("read_user", tokenJoao), "read_user_response", "200", "Consulta realizada com sucesso");

        String invalido = "Token invalido ou expirado";
        assertResposta(comToken("admin_list_users", TOKEN_FALSO), "admin_list_users_response", "401", invalido);
        assertResposta(lerAlvo(TOKEN_FALSO, "joao"), "admin_read_user_response", "401", invalido);
        assertResposta(atualizarAlvo(TOKEN_FALSO, "joao", "ana", "", ""), "admin_update_user_response", "401", invalido);
        assertResposta(apagarAlvo(TOKEN_FALSO, "joao"), "admin_delete_user_response", "401", invalido);
    }

    @Test
    void adminListUsers() throws IOException {
        registrar("joao", "joao@email.com", "senha123");

        JsonObject resp = comToken("admin_list_users", tokenAdmin);
        assertResposta(resp, "admin_list_users_response", "200", "Consulta realizada com sucesso");
        assertEquals("2", resp.get("count").getAsString());
        JsonArray users = resp.getAsJsonArray("users");
        assertEquals(2, users.size());
        JsonObject joao = users.get(1).getAsJsonObject();
        assertEquals("joao", joao.get("user").getAsString());
        assertEquals("joao@email.com", joao.get("email").getAsString());
        assertEquals("user", joao.get("role").getAsString());
        assertFalse(joao.has("password"));

        assertResposta(comToken("admin_list_users", "xyz"), "admin_list_users_response", "400", "Token em formato invalido");
    }

    @Test
    void adminReadUser() throws IOException {
        registrar("joao", "joao@email.com", "senha123");

        JsonObject resp = lerAlvo(tokenAdmin, "joao");
        assertResposta(resp, "admin_read_user_response", "200", "Consulta realizada com sucesso");
        assertEquals("joao", resp.get("user").getAsString());
        assertEquals("joao@email.com", resp.get("email").getAsString());
        assertEquals("user", resp.get("role").getAsString());
        assertTrue(resp.get("created_at").getAsString().matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$"));
        assertFalse(resp.has("password"));

        assertResposta(lerAlvo(tokenAdmin, "ninguem"), "admin_read_user_response", "404", "Usuario nao encontrado");
        assertResposta(lerAlvo(tokenAdmin, "joao1"), "admin_read_user_response", "400", DADOS);
        assertResposta(lerAlvo("abc", "joao"), "admin_read_user_response", "400", DADOS);
        assertResposta(enviar("{\"op\":\"admin_read_user\",\"token\":\"" + tokenAdmin + "\"}"),
                "admin_read_user_response", "400", DADOS);
    }

    @Test
    void adminUpdateUserAlteraOutroUsuario() throws IOException {
        registrar("joao", "joao@email.com", "senha123");
        registrar("maria", "maria@email.com", "senha123");

        assertResposta(atualizarAlvo(tokenAdmin, "joao", "joana", "nova123", ""),
                "admin_update_user_response", "200", "Dados atualizados com sucesso");
        assertEquals("joana", lerAlvo(tokenAdmin, "joana").get("user").getAsString());
        assertResposta(logar("joao@email.com", "nova123"), "login_response", "200", "Login realizado com sucesso");

        assertResposta(atualizarAlvo(tokenAdmin, "joana", "maria", "", ""),
                "admin_update_user_response", "409", "Usuario ja esta em uso");
        assertResposta(atualizarAlvo(tokenAdmin, "ninguem", "", "", "admin"),
                "admin_update_user_response", "404", "Usuario nao encontrado");
        assertResposta(atualizarAlvo(tokenAdmin, "joana", "", "", ""),
                "admin_update_user_response", "400", "Nenhum dado para atualizar");
        assertResposta(atualizarAlvo(tokenAdmin, "joana", "", "", "Admin"), "admin_update_user_response", "400", DADOS);
        assertResposta(atualizarAlvo(tokenAdmin, "joana", "ana1", "", ""), "admin_update_user_response", "400", DADOS);
        assertResposta(atualizarAlvo(tokenAdmin, "joana", "", "sen ha", ""), "admin_update_user_response", "400", DADOS);
        assertResposta(enviar("{\"op\":\"admin_update_user\",\"token\":\"" + tokenAdmin
                        + "\",\"target_user\":\"joana\",\"user\":\"\",\"password\":\"\",\"role\":\"admin\",\"email\":\"x@y.com\"}"),
                "admin_update_user_response", "400", DADOS);
        assertResposta(enviar("{\"op\":\"admin_update_user\",\"token\":\"" + tokenAdmin
                        + "\",\"target_user\":\"joana\",\"user\":\"\",\"password\":\"\"}"),
                "admin_update_user_response", "400", DADOS);
        assertResposta(enviar("{\"op\":\"admin_update_user\",\"token\":\"" + tokenAdmin
                        + "\",\"target_user\":\"joana\",\"user\":null,\"password\":\"\",\"role\":\"admin\"}"),
                "admin_update_user_response", "400", DADOS);
    }

    @Test
    void mudarRoleAtualizaListasDeTokensNaHora() throws IOException {
        String tokenJoao = cadastrarELogar("joao", "joao@email.com");
        assertResposta(comToken("admin_list_users", tokenJoao), "admin_list_users_response", "403", PERMISSAO);

        // Promover coloca o token ativo de joao na whitelist de admin, sem novo login
        assertResposta(atualizarAlvo(tokenAdmin, "joao", "", "", "admin"),
                "admin_update_user_response", "200", "Dados atualizados com sucesso");
        assertResposta(comToken("admin_list_users", tokenJoao), "admin_list_users_response", "200", "Consulta realizada com sucesso");
        assertEquals("admin", comToken("read_user", tokenJoao).get("role").getAsString());

        // Rebaixar tira da whitelist na hora, e a sessao continua como usuario comum
        assertResposta(atualizarAlvo(tokenAdmin, "joao", "", "", "user"),
                "admin_update_user_response", "200", "Dados atualizados com sucesso");
        assertResposta(comToken("admin_list_users", tokenJoao), "admin_list_users_response", "403", PERMISSAO);
        assertResposta(comToken("read_user", tokenJoao), "read_user_response", "200", "Consulta realizada com sucesso");
    }

    @Test
    void ultimoAdminNaoPodeSerRebaixadoNemRemovido() throws IOException {
        assertResposta(atualizarAlvo(tokenAdmin, "admin", "", "", "user"), "admin_update_user_response", "403", ULTIMO_ADMIN);
        assertResposta(apagarAlvo(tokenAdmin, "admin"), "admin_delete_user_response", "403", ULTIMO_ADMIN);
        // Manter o role de admin nao e rebaixar
        assertResposta(atualizarAlvo(tokenAdmin, "admin", "", "", "admin"),
                "admin_update_user_response", "200", "Dados atualizados com sucesso");

        // Com outro admin, o admin padrao pode ser rebaixado
        String tokenMaria = cadastrarELogar("maria", "maria@email.com");
        atualizarAlvo(tokenAdmin, "maria", "", "", "admin");
        assertResposta(atualizarAlvo(tokenMaria, "admin", "", "", "user"),
                "admin_update_user_response", "200", "Dados atualizados com sucesso");
        assertResposta(comToken("admin_list_users", tokenAdmin), "admin_list_users_response", "403", PERMISSAO);
    }

    @Test
    void adminDeleteUserApagaCadastroESessao() throws IOException {
        String tokenJoao = cadastrarELogar("joao", "joao@email.com");

        assertResposta(apagarAlvo(tokenAdmin, "joao"), "admin_delete_user_response", "200", "Usuario removido com sucesso");
        assertResposta(comToken("read_user", tokenJoao), "read_user_response", "401", "Token invalido ou expirado");
        assertResposta(lerAlvo(tokenAdmin, "joao"), "admin_read_user_response", "404", "Usuario nao encontrado");
        // O user e o email ficam livres para um novo cadastro (protocolo 3.13)
        assertResposta(registrar("joao", "joao@email.com", "senha123"), "register_response", "201", "Usuario cadastrado com sucesso");

        assertResposta(apagarAlvo(tokenAdmin, "ninguem"), "admin_delete_user_response", "404", "Usuario nao encontrado");
        assertResposta(apagarAlvo(tokenAdmin, "Jo4o"), "admin_delete_user_response", "400", DADOS);
        assertResposta(apagarAlvo("abc", "joao"), "admin_delete_user_response", "400", DADOS);
    }

    @Test
    void adminPodeRemoverAPropriaContaSeNaoForOUltimo() throws IOException {
        String tokenMaria = cadastrarELogar("maria", "maria@email.com");
        atualizarAlvo(tokenAdmin, "maria", "", "", "admin");

        assertResposta(apagarAlvo(tokenMaria, "maria"), "admin_delete_user_response", "200", "Usuario removido com sucesso");
        assertResposta(comToken("read_user", tokenMaria), "read_user_response", "401", "Token invalido ou expirado");
    }

    private String cadastrarELogar(String user, String email) throws IOException {
        registrar(user, email, "senha123");
        return token(logar(email, "senha123"));
    }

    private static String token(JsonObject login) {
        assertResposta(login, "login_response", "200", "Login realizado com sucesso");
        return login.get("token").getAsString();
    }

    private JsonObject registrar(String user, String email, String password) throws IOException {
        return enviar("{\"op\":\"register\",\"email\":\"" + email + "\",\"user\":\"" + user + "\",\"password\":\"" + password + "\"}");
    }

    private JsonObject logar(String email, String password) throws IOException {
        return enviar("{\"op\":\"login\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private JsonObject lerAlvo(String token, String alvo) throws IOException {
        return enviar("{\"op\":\"admin_read_user\",\"token\":\"" + token + "\",\"target_user\":\"" + alvo + "\"}");
    }

    private JsonObject atualizarAlvo(String token, String alvo, String user, String password, String role) throws IOException {
        return enviar("{\"op\":\"admin_update_user\",\"token\":\"" + token + "\",\"target_user\":\"" + alvo
                + "\",\"user\":\"" + user + "\",\"password\":\"" + password + "\",\"role\":\"" + role + "\"}");
    }

    private JsonObject apagarAlvo(String token, String alvo) throws IOException {
        return enviar("{\"op\":\"admin_delete_user\",\"token\":\"" + token + "\",\"target_user\":\"" + alvo + "\"}");
    }

    private JsonObject comToken(String op, String token) throws IOException {
        return enviar("{\"op\":\"" + op + "\",\"token\":\"" + token + "\"}");
    }

    private JsonObject enviar(String linha) throws IOException {
        conexao.enviarTexto(linha);
        return Json.parseObjeto(conexao.receber());
    }

    private static void assertResposta(JsonObject resposta, String op, String status, String message) {
        assertEquals(op, resposta.get("op").getAsString(), resposta.toString());
        assertEquals(status, resposta.get("status").getAsString(), resposta.toString());
        assertEquals(message, resposta.get("message").getAsString(), resposta.toString());
        // Protocolo 2.1: 'op' e sempre a primeira chave
        assertEquals("op", resposta.keySet().iterator().next());
    }
}
