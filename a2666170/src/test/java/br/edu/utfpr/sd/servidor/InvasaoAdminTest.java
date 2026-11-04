package br.edu.utfpr.sd.servidor;

import static br.edu.utfpr.sd.servidor.ServidorDeTeste.assertResposta;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Plano de testes da EP-2, seguranca: um cliente tenta usar as funcionalidades de admin
 * sem ter um token da whitelist de admin, com JSONs maliciosos ou informacoes falsas.
 * Em nenhum caso a operacao pode ser executada; o servidor responde o erro do protocolo e segue funcionando.
 */
class InvasaoAdminTest {

    private static final String PERMISSAO = "Permissao insuficiente";
    private static final String TOKEN_INVALIDO = "Token invalido ou expirado";
    private static final String DADOS = "Dados em formato invalido";
    private static final String DADOS_SALA = "Dados da sala em formato invalido";
    private static final String TOKEN_FORJADO = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @TempDir
    Path pasta;

    private ServidorDeTeste servidor;
    private ServidorDeTeste.Cliente admin;
    private ServidorDeTeste.Cliente intruso;
    private String tokenAdmin;
    private String tokenIntruso;

    @BeforeEach
    void iniciar() throws Exception {
        servidor = new ServidorDeTeste(pasta);
        admin = servidor.conectar();
        intruso = servidor.conectar();
        tokenAdmin = admin.logarAdmin();
        tokenIntruso = intruso.cadastrarELogar("intruso");
        admin.enviar("{\"op\":\"create_room\",\"token\":\"" + tokenAdmin
                + "\",\"name\":\"Sala A\",\"capacity\":\"10\",\"location\":\"Bloco A\",\"resources\":[]}");
    }

    @AfterEach
    void encerrar() throws Exception {
        intruso.close();
        admin.close();
        servidor.close();
    }

    /** Declarar-se admin no JSON (user, role, email, is_admin...) nao muda nada: so o token conta (regra 3.14). */
    @Test
    void usuarioComumDeclaraSerAdmin() throws Exception {
        String falsos = ",\"user\":\"admin\",\"role\":\"admin\",\"email\":\"admin@admin.com\",\"is_admin\":\"true\","
                + "\"admin\":true,\"permissions\":[\"admin\"]";
        assertResposta(intruso.enviar("{\"op\":\"admin_list_users\",\"token\":\"" + tokenIntruso + "\"" + falsos + "}"),
                "admin_list_users_response", "403", PERMISSAO);
        assertResposta(intruso.enviar("{\"op\":\"admin_read_user\",\"token\":\"" + tokenIntruso
                + "\",\"target_user\":\"admin\",\"role\":\"admin\"}"), "admin_read_user_response", "403", PERMISSAO);
        assertResposta(intruso.enviar("{\"op\":\"admin_update_user\",\"token\":\"" + tokenIntruso
                        + "\",\"target_user\":\"intruso\",\"user\":\"\",\"password\":\"\",\"role\":\"admin\"}"),
                "admin_update_user_response", "403", PERMISSAO);
        assertResposta(intruso.enviar("{\"op\":\"admin_delete_user\",\"token\":\"" + tokenIntruso
                + "\",\"target_user\":\"admin\",\"user\":\"admin\"}"), "admin_delete_user_response", "403", PERMISSAO);
        assertResposta(intruso.enviar("{\"op\":\"create_room\",\"token\":\"" + tokenIntruso + "\",\"role\":\"admin\","
                        + "\"name\":\"Sala X\",\"capacity\":\"5\",\"location\":\"Bloco X\",\"resources\":[]}"),
                "create_room_response", "403", PERMISSAO);
        assertResposta(intruso.enviar("{\"op\":\"update_room\",\"token\":\"" + tokenIntruso + "\",\"role\":\"admin\","
                        + "\"room_id\":\"1\",\"name\":\"\",\"capacity\":\"\",\"location\":\"\",\"resources\":[],\"room_status\":\"inactive\"}"),
                "update_room_response", "403", PERMISSAO);
        assertResposta(intruso.enviar("{\"op\":\"delete_room\",\"token\":\"" + tokenIntruso + "\",\"role\":\"admin\","
                + "\"room_id\":\"1\"}"), "delete_room_response", "403", PERMISSAO);
        assertResposta(intruso.enviar("{\"op\":\"list_reservations\",\"token\":\"" + tokenIntruso + "\",\"role\":\"admin\","
                + "\"scope\":\"all\"}"), "list_reservations_response", "403", PERMISSAO);

        // Nada mudou: o intruso continua user, a sala continua active e o admin existe
        assertEquals("user", intruso.comToken("read_user", tokenIntruso).get("role").getAsString());
        assertEquals("active", lerSala().get("room_status").getAsString());
        assertEquals("2", admin.comToken("admin_list_users", tokenAdmin).get("count").getAsString());
    }

    /** O role enviado no register, login ou update_user e ignorado; a conta continua "user". */
    @Test
    void roleEnviadoPeloClienteEIgnorado() throws Exception {
        assertResposta(intruso.enviar("{\"op\":\"register\",\"email\":\"falso@email.com\",\"user\":\"falso\","
                + "\"password\":\"senha123\",\"role\":\"admin\"}"), "register_response", "201", "Usuario cadastrado com sucesso");
        JsonObject login = intruso.enviar("{\"op\":\"login\",\"email\":\"falso@email.com\",\"password\":\"senha123\","
                + "\"role\":\"admin\"}");
        assertEquals("user", login.get("role").getAsString());
        String tokenFalso = login.get("token").getAsString();
        assertResposta(intruso.comToken("admin_list_users", tokenFalso), "admin_list_users_response", "403", PERMISSAO);

        assertResposta(intruso.enviar("{\"op\":\"update_user\",\"token\":\"" + tokenIntruso
                        + "\",\"user\":\"\",\"password\":\"outra123\",\"role\":\"admin\"}"),
                "update_user_response", "200", "Dados atualizados com sucesso");
        assertEquals("user", intruso.comToken("read_user", tokenIntruso).get("role").getAsString());
        assertResposta(intruso.comToken("admin_list_users", tokenIntruso), "admin_list_users_response", "403", PERMISSAO);
    }

    /** Assumir o nome ou o email do admin nao da poder de admin: o cadastro e a troca de nome sao recusados. */
    @Test
    void usuarioComumTentaAssumirIdentidadeDoAdmin() throws Exception {
        String cadastrado = "Usuario ou email ja cadastrado";
        assertResposta(intruso.registrar("admin", "outro@email.com", "senha123"), "register_response", "409", cadastrado);
        assertResposta(intruso.registrar("outro", "admin@admin.com", "senha123"), "register_response", "409", cadastrado);
        // Maiusculas sao convertidas (regra 2.12): "ADMIN" e o mesmo user
        assertResposta(intruso.registrar("ADMIN", "outro@email.com", "senha123"), "register_response", "409", cadastrado);
        assertResposta(intruso.enviar("{\"op\":\"update_user\",\"token\":\"" + tokenIntruso
                + "\",\"user\":\"admin\",\"password\":\"\"}"), "update_user_response", "409", "Usuario ja esta em uso");
        // Senha errada do admin
        assertResposta(intruso.enviar("{\"op\":\"login\",\"email\":\"admin@admin.com\",\"password\":\"admin123\"}"),
                "login_response", "401", "Email ou senha incorretos");
    }

    /** delete_user so apaga a conta do dono do token; um target_user enviado junto e ignorado. */
    @Test
    void deleteUserNaoApagaOutraConta() throws Exception {
        assertResposta(intruso.enviar("{\"op\":\"delete_user\",\"token\":\"" + tokenIntruso
                + "\",\"target_user\":\"admin\",\"password\":\"admin\"}"), "delete_user_response", "403", "Senha incorreta");
        assertResposta(intruso.enviar("{\"op\":\"delete_user\",\"token\":\"" + tokenIntruso
                + "\",\"target_user\":\"admin\",\"password\":\"senha123\"}"), "delete_user_response", "200", "Usuario removido com sucesso");
        // Quem saiu foi o intruso; o admin continua
        assertResposta(admin.comToken("read_user", tokenAdmin), "read_user_response", "200", "Consulta realizada com sucesso");
        assertEquals("1", admin.comToken("admin_list_users", tokenAdmin).get("count").getAsString());
    }

    /** Tokens ausentes, forjados ou em formato malicioso nunca liberam a operacao. */
    @Test
    void tokensFalsosOuMalFormados() throws Exception {
        assertResposta(intruso.enviar("{\"op\":\"admin_list_users\"}"), "admin_list_users_response", "401", TOKEN_INVALIDO);
        assertResposta(intruso.comToken("admin_list_users", TOKEN_FORJADO), "admin_list_users_response", "401", TOKEN_INVALIDO);
        assertResposta(intruso.comToken("admin_list_users", ""), "admin_list_users_response", "400", "Token em formato invalido");
        assertResposta(intruso.comToken("admin_list_users", tokenAdmin.toUpperCase()),
                "admin_list_users_response", "400", "Token em formato invalido");
        assertResposta(intruso.comToken("admin_list_users", tokenAdmin.substring(1)),
                "admin_list_users_response", "400", "Token em formato invalido");
        assertResposta(intruso.comToken("admin_list_users", tokenAdmin + "0"),
                "admin_list_users_response", "400", "Token em formato invalido");
        assertResposta(intruso.comToken("admin_list_users", " " + tokenAdmin.substring(1)),
                "admin_list_users_response", "400", "Token em formato invalido");
        for (String valor : new String[] {"null", "123", "true", "[\"" + tokenAdmin + "\"]", "{\"$ne\":\"\"}", "{}"}) {
            assertResposta(intruso.enviar("{\"op\":\"admin_list_users\",\"token\":" + valor + "}"),
                    "admin_list_users_response", "400", "Token em formato invalido");
            assertResposta(intruso.enviar("{\"op\":\"delete_room\",\"token\":" + valor + ",\"room_id\":\"1\"}"),
                    "delete_room_response", "400", DADOS);
        }
        // Coringas de SQL no lugar do token
        assertResposta(intruso.comToken("admin_list_users", "' OR '1'='1"), "admin_list_users_response", "400",
                "Token em formato invalido");
        assertEquals("active", lerSala().get("room_status").getAsString());
    }

    /** O poder de admin acaba na hora: logout, rebaixamento, remocao da conta ou reinicio. */
    @Test
    void tokenDeAdminRevogadoNaoVolta() throws Exception {
        try (ServidorDeTeste.Cliente maria = servidor.conectar()) {
            String tokenMaria = maria.cadastrarELogar("maria");
            promover("maria");
            assertResposta(maria.comToken("admin_list_users", tokenMaria), "admin_list_users_response", "200",
                    "Consulta realizada com sucesso");

            // Rebaixada: 403 na hora, sem esperar logout
            admin.enviar("{\"op\":\"admin_update_user\",\"token\":\"" + tokenAdmin
                    + "\",\"target_user\":\"maria\",\"user\":\"\",\"password\":\"\",\"role\":\"user\"}");
            assertResposta(maria.comToken("admin_list_users", tokenMaria), "admin_list_users_response", "403", PERMISSAO);
            assertResposta(maria.enviar("{\"op\":\"delete_room\",\"token\":\"" + tokenMaria + "\",\"room_id\":\"1\"}"),
                    "delete_room_response", "403", PERMISSAO);

            // Promovida de novo e depois removida: o token morre junto com a conta
            promover("maria");
            admin.enviar("{\"op\":\"admin_delete_user\",\"token\":\"" + tokenAdmin + "\",\"target_user\":\"maria\"}");
            assertResposta(maria.comToken("admin_list_users", tokenMaria), "admin_list_users_response", "401", TOKEN_INVALIDO);
        }
        // Depois do logout, o token antigo do admin nao vale mais (regra 3.4)
        admin.comToken("logout", tokenAdmin);
        assertResposta(intruso.comToken("admin_list_users", tokenAdmin), "admin_list_users_response", "401", TOKEN_INVALIDO);
        assertResposta(intruso.enviar("{\"op\":\"create_room\",\"token\":\"" + tokenAdmin + "\",\"name\":\"Sala Z\","
                + "\"capacity\":\"5\",\"location\":\"Z\",\"resources\":[]}"), "create_room_response", "401", TOKEN_INVALIDO);
    }

    /** JSON com chave repetida: vale o ultimo valor, e a validacao de token continua valendo. */
    @Test
    void chavesRepetidasNaoBurlamAValidacao() throws Exception {
        assertResposta(intruso.enviar("{\"op\":\"admin_list_users\",\"token\":\"" + TOKEN_FORJADO + "\",\"token\":\""
                + tokenIntruso + "\"}"), "admin_list_users_response", "403", PERMISSAO);
        assertResposta(intruso.enviar("{\"op\":\"read_user\",\"op\":\"admin_list_users\",\"token\":\"" + tokenIntruso + "\"}"),
                "admin_list_users_response", "403", PERMISSAO);
        assertResposta(intruso.enviar("{\"op\":\"admin_update_user\",\"token\":\"" + tokenIntruso + "\",\"target_user\":\"intruso\","
                        + "\"user\":\"\",\"password\":\"\",\"role\":\"user\",\"role\":\"admin\"}"),
                "admin_update_user_response", "403", PERMISSAO);
    }

    /** Injecao de SQL, homoglifos e caracteres de controle param na regex (400) e o banco fica intacto. */
    @Test
    void injecaoNosCamposDoAdmin() throws Exception {
        String[] alvos = {"admin' OR '1'='1", "admin\\\"--", "admin;DROP TABLE usuarios", "аdmin", "admin\\u0000",
                "adm in", "*", "%"};
        for (String alvo : alvos) {
            assertResposta(admin.enviar("{\"op\":\"admin_read_user\",\"token\":\"" + tokenAdmin + "\",\"target_user\":\""
                    + alvo + "\"}"), "admin_read_user_response", "400", DADOS);
            assertResposta(admin.enviar("{\"op\":\"admin_delete_user\",\"token\":\"" + tokenAdmin + "\",\"target_user\":\""
                    + alvo + "\"}"), "admin_delete_user_response", "400", DADOS);
        }
        assertResposta(admin.enviar("{\"op\":\"create_room\",\"token\":\"" + tokenAdmin + "\",\"name\":\"Sala'); DROP TABLE salas;--\","
                + "\"capacity\":\"5\",\"location\":\"B\",\"resources\":[]}"), "create_room_response", "400", DADOS_SALA);
        assertResposta(admin.enviar("{\"op\":\"read_room\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"1 OR 1=1\"}"),
                "read_room_response", "400", DADOS);
        assertResposta(admin.enviar("{\"op\":\"delete_room\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"-1\"}"),
                "delete_room_response", "400", DADOS);
        assertResposta(admin.enviar("{\"op\":\"list_rooms\",\"token\":\"" + tokenAdmin + "\",\"min_capacity\":\"1; DELETE FROM salas\"}"),
                "list_rooms_response", "400", "Filtros em formato invalido");
        assertResposta(admin.enviar("{\"op\":\"list_reservations\",\"token\":\"" + tokenAdmin + "\",\"scope\":\"all' --\"}"),
                "list_reservations_response", "400", "Filtros em formato invalido");
        // Um recurso com aspas e so texto: gravado e devolvido como veio, sem efeito no banco
        assertResposta(admin.enviar("{\"op\":\"create_room\",\"token\":\"" + tokenAdmin + "\",\"name\":\"Sala Q\","
                        + "\"capacity\":\"5\",\"location\":\"B\",\"resources\":[\"tv'); DROP TABLE salas;--\"]}"),
                "create_room_response", "201", "Sala cadastrada com sucesso");
        assertEquals("[\"tv'); DROP TABLE salas;--\"]", admin.enviar("{\"op\":\"read_room\",\"token\":\"" + tokenAdmin
                + "\",\"room_id\":\"2\"}").get("resources").toString());
        assertEquals("2", admin.comToken("list_rooms", tokenAdmin).get("count").getAsString());
        assertEquals("2", admin.comToken("admin_list_users", tokenAdmin).get("count").getAsString());
    }

    /** Variacoes de op e linhas fora do protocolo recebem "error" e a conexao continua utilizavel. */
    @Test
    void opsFalsificadasELinhasMaliciosas() throws Exception {
        for (String op : new String[] {"ADMIN_LIST_USERS", "admin_list_users ", "admin-list-users", "admin_list_users\\u0000"}) {
            assertResposta(intruso.enviar("{\"op\":\"" + op + "\",\"token\":\"" + tokenIntruso + "\"}"),
                    "error", "400", "Operacao desconhecida");
        }
        assertResposta(intruso.enviar("{\"op\":[\"admin_list_users\"],\"token\":\"" + tokenIntruso + "\"}"),
                "error", "400", "Requisicao invalida");
        assertResposta(intruso.enviar("{\"op\":\"admin_list_users\",\"token\":\"" + tokenIntruso + "\"}{\"op\":\"x\"}"),
                "error", "400", "Requisicao invalida");
        assertResposta(intruso.enviar("{\"op\":\"admin_list_users\",\"token\":\"" + tokenIntruso + "\",\"lixo\":\""
                + "a".repeat(9000) + "\"}"), "error", "400", "Mensagem excede o tamanho maximo");
        // Depois de tudo isso a sessao segue normal
        assertResposta(intruso.comToken("read_user", tokenIntruso), "read_user_response", "200", "Consulta realizada com sucesso");
    }

    /** Dois admins tentando se rebaixar ao mesmo tempo: so um consegue e sempre sobra um admin. */
    @Test
    void rebaixamentoSimultaneoNaoDeixaSistemaSemAdmin() throws Exception {
        try (ServidorDeTeste.Cliente maria = servidor.conectar()) {
            String tokenMaria = maria.cadastrarELogar("maria");
            promover("maria");

            CountDownLatch largada = new CountDownLatch(1);
            ExecutorService threads = Executors.newFixedThreadPool(2);
            List<Callable<JsonObject>> tarefas = List.of(
                    () -> {
                        largada.await();
                        return admin.enviar(rebaixar(tokenAdmin, "maria"));
                    },
                    () -> {
                        largada.await();
                        return maria.enviar(rebaixar(tokenMaria, "admin"));
                    });
            List<Future<JsonObject>> respostas = new ArrayList<>();
            for (Callable<JsonObject> tarefa : tarefas) {
                respostas.add(threads.submit(tarefa));
            }
            largada.countDown();
            int sucessos = 0;
            for (Future<JsonObject> resposta : respostas) {
                JsonObject r = resposta.get();
                if ("200".equals(r.get("status").getAsString())) {
                    sucessos++;
                } else {
                    assertEquals("403", r.get("status").getAsString(), r.toString());
                }
            }
            threads.shutdown();
            assertEquals(1, sucessos);
        }
        // Algum dos dois ainda e admin
        JsonObject admins = admin.enviar("{\"op\":\"admin_read_user\",\"token\":\"" + tokenAdmin + "\",\"target_user\":\"admin\"}");
        if (!"200".equals(admins.get("status").getAsString())) {
            assertResposta(admins, "admin_read_user_response", "403", PERMISSAO);
        }
    }

    /** O ultimo admin nao pode sair por nenhum caminho: delete_user, admin_delete_user ou rebaixamento. */
    @Test
    void ultimoAdminNaoSaiPorNenhumCaminho() throws Exception {
        assertResposta(admin.enviar("{\"op\":\"delete_user\",\"token\":\"" + tokenAdmin + "\",\"password\":\"admin\"}"),
                "delete_user_response", "403", "Nao e possivel remover o ultimo administrador");
        assertResposta(admin.enviar("{\"op\":\"admin_delete_user\",\"token\":\"" + tokenAdmin + "\",\"target_user\":\"ADMIN\"}"),
                "admin_delete_user_response", "403", "Nao e possivel remover o ultimo administrador");
        assertResposta(admin.enviar(rebaixar(tokenAdmin, "admin")),
                "admin_update_user_response", "403", "Nao e possivel remover o ultimo administrador");
        assertResposta(admin.comToken("admin_list_users", tokenAdmin), "admin_list_users_response", "200",
                "Consulta realizada com sucesso");
    }

    /**
     * Comportamento a conhecer: o token vale em qualquer conexao (regra 1.11), e pela decisao do projeto
     * a queda de qualquer conexao que o usou encerra a sessao. Quem tiver o token de outra pessoa
     * consegue usa-lo e, ao desconectar, derruba a sessao do dono.
     */
    @Test
    void tokenUsadoEmOutraConexaoCaiJuntoComEla() throws Exception {
        try (ServidorDeTeste.Cliente outra = servidor.conectar()) {
            assertResposta(outra.comToken("read_user", tokenAdmin), "read_user_response", "200", "Consulta realizada com sucesso");
        }
        JsonObject resp = null;
        for (int i = 0; i < 30; i++) {
            resp = admin.comToken("read_user", tokenAdmin);
            if ("401".equals(resp.get("status").getAsString())) {
                break;
            }
            Thread.sleep(100);
        }
        assertResposta(resp, "read_user_response", "401", TOKEN_INVALIDO);
        assertFalse(resp.has("user"));
    }

    private void promover(String alvo) throws Exception {
        assertResposta(admin.enviar("{\"op\":\"admin_update_user\",\"token\":\"" + tokenAdmin + "\",\"target_user\":\""
                + alvo + "\",\"user\":\"\",\"password\":\"\",\"role\":\"admin\"}"), "admin_update_user_response", "200",
                "Dados atualizados com sucesso");
    }

    private static String rebaixar(String token, String alvo) {
        return "{\"op\":\"admin_update_user\",\"token\":\"" + token + "\",\"target_user\":\"" + alvo
                + "\",\"user\":\"\",\"password\":\"\",\"role\":\"user\"}";
    }

    private JsonObject lerSala() throws Exception {
        return admin.enviar("{\"op\":\"read_room\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"1\"}");
    }
}
