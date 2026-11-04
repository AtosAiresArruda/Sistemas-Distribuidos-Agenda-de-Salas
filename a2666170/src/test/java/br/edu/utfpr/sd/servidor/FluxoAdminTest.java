package br.edu.utfpr.sd.servidor;

import static br.edu.utfpr.sd.servidor.ServidorDeTeste.assertResposta;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Plano de testes da EP-2, fluxo principal: o administrador usa corretamente cada funcionalidade.
 * Cada teste corresponde a um item avaliado do servidor (i a o) e confere o resultado lendo de volta.
 */
class FluxoAdminTest {

    private static final String CONSULTA = "Consulta realizada com sucesso";
    private static final String FUTURA = "2099-03-02";

    @TempDir
    Path pasta;

    private ServidorDeTeste servidor;
    private ServidorDeTeste.Cliente admin;
    private String tokenAdmin;

    @BeforeEach
    void iniciar() throws Exception {
        servidor = new ServidorDeTeste(pasta);
        admin = servidor.conectar();
        tokenAdmin = admin.logarAdmin();
    }

    @AfterEach
    void encerrar() throws Exception {
        admin.close();
        servidor.close();
    }

    @Test
    void itemI_listaTodosOsUsuarios() throws Exception {
        admin.registrar("joao", "joao@email.com", "senha123");
        admin.registrar("maria", "maria@alunos.utfpr.edu.br", "senha123");
        admin.enviar("{\"op\":\"admin_update_user\",\"token\":\"" + tokenAdmin
                + "\",\"target_user\":\"maria\",\"user\":\"\",\"password\":\"\",\"role\":\"admin\"}");

        JsonObject resp = admin.comToken("admin_list_users", tokenAdmin);
        assertResposta(resp, "admin_list_users_response", "200", CONSULTA);
        assertEquals("3", resp.get("count").getAsString());
        JsonArray users = resp.getAsJsonArray("users");
        assertEquals(List.of("admin", "joao", "maria"), campo(users, "user"));
        assertEquals(List.of("admin@admin.com", "joao@email.com", "maria@alunos.utfpr.edu.br"), campo(users, "email"));
        assertEquals(List.of("admin", "user", "admin"), campo(users, "role"));
        // Nunca devolve senha; todos os valores sao strings (regra 2.3)
        for (JsonElement u : users) {
            assertEquals(3, u.getAsJsonObject().size(), u.toString());
            u.getAsJsonObject().entrySet().forEach(e -> assertTrue(e.getValue().getAsJsonPrimitive().isString()));
        }
    }

    @Test
    void itemJ_atualizaDadosDeOutroUsuario() throws Exception {
        try (ServidorDeTeste.Cliente joao = servidor.conectar()) {
            String tokenJoao = joao.cadastrarELogar("joao");

            assertResposta(admin.enviar("{\"op\":\"admin_update_user\",\"token\":\"" + tokenAdmin
                            + "\",\"target_user\":\"joao\",\"user\":\"joaquim\",\"password\":\"Nova2026\",\"role\":\"admin\"}"),
                    "admin_update_user_response", "200", "Dados atualizados com sucesso");

            JsonObject lido = admin.enviar("{\"op\":\"admin_read_user\",\"token\":\"" + tokenAdmin
                    + "\",\"target_user\":\"joaquim\"}");
            assertResposta(lido, "admin_read_user_response", "200", CONSULTA);
            assertEquals("joaquim", lido.get("user").getAsString());
            assertEquals("joao@email.com", lido.get("email").getAsString());
            assertEquals("admin", lido.get("role").getAsString());
            // A sessao de joao continua e ja vale como admin, sem novo login (regra 3.14)
            assertResposta(joao.comToken("admin_list_users", tokenJoao), "admin_list_users_response", "200", CONSULTA);
            // Os dados do proprio admin nao mudaram
            assertEquals("admin", admin.comToken("read_user", tokenAdmin).get("user").getAsString());
            // A nova senha vale no proximo login
            joao.comToken("logout", tokenJoao);
            joao.logar("joao@email.com", "Nova2026");
        }
    }

    @Test
    void itemK_excluiCadastroDeUsuarioComum() throws Exception {
        try (ServidorDeTeste.Cliente joao = servidor.conectar()) {
            String tokenJoao = joao.cadastrarELogar("joao");

            assertResposta(admin.enviar("{\"op\":\"admin_delete_user\",\"token\":\"" + tokenAdmin
                    + "\",\"target_user\":\"joao\"}"), "admin_delete_user_response", "200", "Usuario removido com sucesso");

            assertEquals(List.of("admin"), campo(admin.comToken("admin_list_users", tokenAdmin).getAsJsonArray("users"), "user"));
            assertResposta(joao.comToken("read_user", tokenJoao), "read_user_response", "401", "Token invalido ou expirado");
            assertResposta(joao.enviar("{\"op\":\"login\",\"email\":\"joao@email.com\",\"password\":\"senha123\"}"),
                    "login_response", "401", "Email ou senha incorretos");
        }
    }

    @Test
    void itemL_cadastraSalaComNomeDiasHorariosECapacidade() throws Exception {
        JsonObject resp = criarSalaB12();
        assertResposta(resp, "create_room_response", "201", "Sala cadastrada com sucesso");
        assertEquals("1", resp.get("room_id").getAsString());

        JsonObject sala = admin.enviar("{\"op\":\"read_room\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"1\"}");
        assertEquals("Sala B12", sala.get("name").getAsString());
        assertEquals("12", sala.get("capacity").getAsString());
        assertEquals("active", sala.get("room_status").getAsString());
        assertEquals("[{\"day\":\"segunda\",\"slot\":\"M1\"},{\"day\":\"segunda\",\"slot\":\"M2\"},"
                + "{\"day\":\"quarta\",\"slot\":\"N5\"}]", sala.get("schedule").toString());
    }

    @Test
    void itemM_leSalasHorariosCapacidadeStatusEQuemReservou() throws Exception {
        criarSalaB12();
        try (ServidorDeTeste.Cliente joao = servidor.conectar()) {
            joao.cadastrarELogar("joao");
        }
        servidor.reservar("joao", 1, FUTURA, "14:00", "16:00", 6);

        JsonObject lista = admin.comToken("list_rooms", tokenAdmin);
        assertResposta(lista, "list_rooms_response", "200", CONSULTA);
        JsonObject sala = lista.getAsJsonArray("rooms").get(0).getAsJsonObject();
        assertEquals("Sala B12", sala.get("name").getAsString());
        assertEquals("12", sala.get("capacity").getAsString());
        assertEquals("active", sala.get("room_status").getAsString());
        assertEquals(3, sala.getAsJsonArray("schedule").size());

        // Livre ou ocupada no periodo
        JsonObject periodo = admin.enviar("{\"op\":\"list_rooms\",\"token\":\"" + tokenAdmin + "\",\"date\":\"" + FUTURA
                + "\",\"start_time\":\"15:00\",\"end_time\":\"15:30\"}");
        assertEquals("false", periodo.getAsJsonArray("rooms").get(0).getAsJsonObject().get("available").getAsString());

        // Quem reservou e quando
        JsonObject agenda = admin.enviar("{\"op\":\"list_reservations\",\"token\":\"" + tokenAdmin
                + "\",\"scope\":\"all\",\"room_id\":\"1\"}");
        assertResposta(agenda, "list_reservations_response", "200", CONSULTA);
        JsonObject reserva = agenda.getAsJsonArray("reservations").get(0).getAsJsonObject();
        assertEquals("joao", reserva.get("user").getAsString());
        assertEquals(FUTURA, reserva.get("date").getAsString());
        assertEquals("14:00", reserva.get("start_time").getAsString());
        assertEquals("16:00", reserva.get("end_time").getAsString());
        assertEquals("Sala B12", reserva.get("name").getAsString());
    }

    @Test
    void itemN_atualizaNomeHorariosCapacidadeEStatus() throws Exception {
        criarSalaB12();

        assertResposta(admin.enviar("{\"op\":\"update_room\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"1\","
                        + "\"name\":\"Sala B13\",\"capacity\":\"20\",\"location\":\"\",\"resources\":[\"projetor\"],"
                        + "\"room_status\":\"inactive\",\"schedule\":[{\"day\":\"sabado\",\"slot\":\"T1\"}]}"),
                "update_room_response", "200", "Sala atualizada com sucesso");

        JsonObject sala = admin.enviar("{\"op\":\"read_room\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"1\"}");
        assertEquals("Sala B13", sala.get("name").getAsString());
        assertEquals("20", sala.get("capacity").getAsString());
        assertEquals("Bloco B", sala.get("location").getAsString());
        assertEquals("inactive", sala.get("room_status").getAsString());
        assertEquals("[{\"day\":\"sabado\",\"slot\":\"T1\"}]", sala.get("schedule").toString());
        // Indisponivel: nao aparece como livre em nenhum periodo
        JsonObject periodo = admin.enviar("{\"op\":\"list_rooms\",\"token\":\"" + tokenAdmin + "\",\"date\":\"" + FUTURA
                + "\",\"start_time\":\"08:00\",\"end_time\":\"09:00\"}");
        assertEquals("false", periodo.getAsJsonArray("rooms").get(0).getAsJsonObject().get("available").getAsString());
    }

    @Test
    void itemO_removeSalaDosRegistros() throws Exception {
        criarSalaB12();

        assertResposta(admin.enviar("{\"op\":\"delete_room\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"1\"}"),
                "delete_room_response", "200", "Sala removida com sucesso");

        assertResposta(admin.enviar("{\"op\":\"read_room\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"1\"}"),
                "read_room_response", "404", "Sala nao encontrada");
        assertEquals("0", admin.comToken("list_rooms", tokenAdmin).get("count").getAsString());
    }

    private JsonObject criarSalaB12() throws Exception {
        return admin.enviar("{\"op\":\"create_room\",\"token\":\"" + tokenAdmin + "\",\"name\":\"Sala B12\","
                + "\"capacity\":\"12\",\"location\":\"Bloco B\",\"resources\":[\"tv\"],\"schedule\":["
                + "{\"day\":\"quarta\",\"slot\":\"N5\"},{\"day\":\"segunda\",\"slot\":\"M2\"},{\"day\":\"segunda\",\"slot\":\"M1\"}]}");
    }

    private static List<String> campo(JsonArray lista, String chave) {
        return StreamSupport.stream(lista.spliterator(), false)
                .map(e -> e.getAsJsonObject().get(chave).getAsString()).toList();
    }
}
