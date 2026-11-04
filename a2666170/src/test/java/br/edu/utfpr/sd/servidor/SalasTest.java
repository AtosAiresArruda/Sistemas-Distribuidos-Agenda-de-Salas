package br.edu.utfpr.sd.servidor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import br.edu.utfpr.sd.comum.ConexaoJson;
import br.edu.utfpr.sd.comum.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Testa as operacoes de sala e o list_reservations da Entrega Parcial 2.2 contra um servidor real. */
class SalasTest {

    private static final String TOKEN_FALSO = "c0fc3c713f09a43384ac08f7d91fca430dcbc6466fff9284ce4571bdc2c8f9f9";
    private static final String DADOS_SALA = "Dados da sala em formato invalido";
    private static final String DADOS = "Dados em formato invalido";
    private static final String FILTROS = "Filtros em formato invalido";
    private static final String PERMISSAO = "Permissao insuficiente";
    private static final String CONSULTA = "Consulta realizada com sucesso";
    private static final String SCHEDULE = "[{\"day\":\"quarta\",\"slot\":\"T3\"},{\"day\":\"segunda\",\"slot\":\"M2\"},"
            + "{\"day\":\"segunda\",\"slot\":\"M1\"}]";
    // Datas longe o bastante para serem sempre futuras ou sempre passadas
    private static final String FUTURA = "2099-01-10";
    private static final String PASSADA = "2000-01-10";

    @TempDir
    Path pasta;

    private ServerSocket serverSocket;
    private ConexaoJson conexao;
    private String tokenAdmin;
    private String tokenJoao;

    @BeforeEach
    void iniciar() throws Exception {
        BancoDados banco = new BancoDados(arquivo());
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
        tokenAdmin = token(enviar("{\"op\":\"login\",\"email\":\"admin@admin.com\",\"password\":\"admin\"}"));
        enviar("{\"op\":\"register\",\"email\":\"joao@email.com\",\"user\":\"joao\",\"password\":\"senha123\"}");
        tokenJoao = token(enviar("{\"op\":\"login\",\"email\":\"joao@email.com\",\"password\":\"senha123\"}"));
    }

    @AfterEach
    void encerrar() throws IOException {
        conexao.close();
        serverSocket.close();
    }

    @Test
    void createRoom() throws IOException {
        JsonObject resp = criar(tokenAdmin, "Sala de Estudo B12", "12", "Bloco B - 1 andar", "[\"projetor\",\"tv\"]", SCHEDULE);
        assertResposta(resp, "create_room_response", "201", "Sala cadastrada com sucesso");
        assertEquals("1", resp.get("room_id").getAsString());
        assertResposta(criar(tokenAdmin, "Sala de Estudo B12", "5", "Outro", "[]", null),
                "create_room_response", "409", "Sala ja cadastrada");
        // Nome unico com comparacao exata (ponto a revisar no CLAUDE.md)
        assertResposta(criar(tokenAdmin, "sala de estudo b12", "5", "Outro", "[]", null),
                "create_room_response", "201", "Sala cadastrada com sucesso");
        // Sem schedule a sala nasce sem horarios; resources pode ser vazio
        JsonObject sem = criar(tokenAdmin, "Sala C", "4", "Bloco C", "[]", null);
        assertEquals("3", sem.get("room_id").getAsString());

        assertResposta(criar(tokenAdmin, "Sala Ã", "5", "Bloco", "[]", null), "create_room_response", "400", DADOS_SALA);
        assertResposta(criar(tokenAdmin, "", "5", "Bloco", "[]", null), "create_room_response", "400", DADOS_SALA);
        assertResposta(criar(tokenAdmin, "Sala D", "0", "Bloco", "[]", null), "create_room_response", "400", DADOS_SALA);
        assertResposta(criar(tokenAdmin, "Sala D", "012", "Bloco", "[]", null), "create_room_response", "400", DADOS_SALA);
        assertResposta(criar(tokenAdmin, "Sala D", "5", "Bloco #1", "[]", null), "create_room_response", "400", DADOS_SALA);
        assertResposta(criar(tokenAdmin, "Sala D", "5", "Bloco", "\"projetor\"", null), "create_room_response", "400", DADOS_SALA);
        assertResposta(criar(tokenAdmin, "Sala D", "5", "Bloco", "[1]", null), "create_room_response", "400", DADOS_SALA);
        assertResposta(criar(tokenAdmin, "Sala D", "5", "Bloco", "[]", "[{\"day\":\"domingo\",\"slot\":\"M1\"}]"),
                "create_room_response", "400", DADOS_SALA);
        assertResposta(criar(tokenAdmin, "Sala D", "5", "Bloco", "[]", "[{\"day\":\"segunda\",\"slot\":\"M7\"}]"),
                "create_room_response", "400", DADOS_SALA);
        assertResposta(criar(tokenAdmin, "Sala D", "5", "Bloco", "[]",
                "[{\"day\":\"segunda\",\"slot\":\"M1\"},{\"day\":\"segunda\",\"slot\":\"M1\"}]"),
                "create_room_response", "400", DADOS_SALA);
        assertResposta(criar(tokenAdmin, "Sala D", "5", "Bloco", "[]", "\"segunda\""), "create_room_response", "400", DADOS_SALA);
        assertResposta(enviar("{\"op\":\"create_room\",\"token\":\"" + tokenAdmin
                        + "\",\"name\":\"Sala D\",\"capacity\":5,\"location\":\"Bloco\",\"resources\":[]}"),
                "create_room_response", "400", DADOS_SALA);
        assertResposta(enviar("{\"op\":\"create_room\",\"token\":\"" + tokenAdmin
                        + "\",\"name\":\"Sala D\",\"capacity\":\"5\",\"location\":\"Bloco\"}"),
                "create_room_response", "400", DADOS_SALA);
        assertResposta(criar("abc", "Sala D", "5", "Bloco", "[]", null), "create_room_response", "400", DADOS_SALA);

        assertResposta(criar(tokenJoao, "Sala D", "5", "Bloco", "[]", null), "create_room_response", "403", PERMISSAO);
        assertResposta(criar(TOKEN_FALSO, "Sala D", "5", "Bloco", "[]", null),
                "create_room_response", "401", "Token invalido ou expirado");
    }

    @Test
    void readRoom() throws IOException {
        criar(tokenAdmin, "Sala B12", "12", "Bloco B", "[\"projetor\",\"tv\"]", SCHEDULE);

        // Qualquer usuario logado le salas
        JsonObject resp = ler(tokenJoao, "1");
        assertResposta(resp, "read_room_response", "200", CONSULTA);
        assertEquals("1", resp.get("room_id").getAsString());
        assertEquals("Sala B12", resp.get("name").getAsString());
        assertEquals("12", resp.get("capacity").getAsString());
        assertEquals("Bloco B", resp.get("location").getAsString());
        assertEquals("[\"projetor\",\"tv\"]", resp.get("resources").toString());
        assertEquals("active", resp.get("room_status").getAsString());
        assertFalse(resp.has("available"));
        // schedule volta em ordem de dia e bloco
        assertEquals("[{\"day\":\"segunda\",\"slot\":\"M1\"},{\"day\":\"segunda\",\"slot\":\"M2\"},"
                + "{\"day\":\"quarta\",\"slot\":\"T3\"}]", resp.get("schedule").toString());

        assertResposta(ler(tokenAdmin, "99"), "read_room_response", "404", "Sala nao encontrada");
        assertResposta(ler(tokenAdmin, "abc"), "read_room_response", "400", DADOS);
        assertResposta(ler("abc", "1"), "read_room_response", "400", DADOS);
        assertResposta(ler(TOKEN_FALSO, "1"), "read_room_response", "401", "Token invalido ou expirado");
    }

    @Test
    void listRooms() throws Exception {
        criar(tokenAdmin, "Sala A", "12", "Bloco A", "[]", null);
        criar(tokenAdmin, "Sala B", "4", "Bloco B", "[]", null);
        criar(tokenAdmin, "Sala C", "20", "Bloco C", "[]", null);
        atualizar(tokenAdmin, "3", "", "", "", "[]", "inactive", null);
        reservar("joao", 1, FUTURA, "14:00", "16:00", 3);

        JsonObject todas = enviar("{\"op\":\"list_rooms\",\"token\":\"" + tokenJoao + "\"}");
        assertResposta(todas, "list_rooms_response", "200", CONSULTA);
        assertEquals("3", todas.get("count").getAsString());
        assertFalse(todas.getAsJsonArray("rooms").get(0).getAsJsonObject().has("available"));

        // Com o trio: sala reservada no periodo, sala livre e sala inactive
        JsonArray rooms = listarNoPeriodo("15:00", "17:00", null).getAsJsonArray("rooms");
        assertEquals("false", rooms.get(0).getAsJsonObject().get("available").getAsString());
        assertEquals("true", rooms.get(1).getAsJsonObject().get("available").getAsString());
        assertEquals("false", rooms.get(2).getAsJsonObject().get("available").getAsString());
        // Encostar no fim de outra reserva nao e conflito (regra 5.4)
        assertEquals("true", listarNoPeriodo("16:00", "17:00", null).getAsJsonArray("rooms")
                .get(0).getAsJsonObject().get("available").getAsString());

        JsonObject grandes = listarNoPeriodo("08:00", "09:00", "10");
        assertEquals("2", grandes.get("count").getAsString());

        assertResposta(enviar("{\"op\":\"list_rooms\",\"token\":\"" + tokenJoao + "\",\"date\":\"" + FUTURA + "\"}"),
                "list_rooms_response", "400", FILTROS);
        assertResposta(enviar("{\"op\":\"list_rooms\",\"token\":\"" + tokenJoao + "\",\"date\":\"10/01/2099\","
                + "\"start_time\":\"14:00\",\"end_time\":\"16:00\"}"), "list_rooms_response", "400", FILTROS);
        assertResposta(enviar("{\"op\":\"list_rooms\",\"token\":\"" + tokenJoao + "\",\"date\":\"" + FUTURA + "\","
                + "\"start_time\":\"24:00\",\"end_time\":\"16:00\"}"), "list_rooms_response", "400", FILTROS);
        assertResposta(enviar("{\"op\":\"list_rooms\",\"token\":\"" + tokenJoao + "\",\"min_capacity\":\"0\"}"),
                "list_rooms_response", "400", FILTROS);
        assertResposta(enviar("{\"op\":\"list_rooms\",\"token\":\"" + tokenJoao + "\",\"min_capacity\":6}"),
                "list_rooms_response", "400", FILTROS);
        assertResposta(enviar("{\"op\":\"list_rooms\",\"token\":\"abc\"}"), "list_rooms_response", "400", FILTROS);
        assertResposta(enviar("{\"op\":\"list_rooms\",\"token\":\"" + TOKEN_FALSO + "\"}"),
                "list_rooms_response", "401", "Token invalido ou expirado");
    }

    @Test
    void updateRoom() throws Exception {
        criar(tokenAdmin, "Sala A", "12", "Bloco A", "[\"tv\"]", SCHEDULE);
        criar(tokenAdmin, "Sala B", "4", "Bloco B", "[]", null);

        // "" mantem; resources substitui a lista; schedule ausente mantem os horarios
        assertResposta(atualizar(tokenAdmin, "1", "Sala Nova", "", "", "[\"projetor\",\"quadro\"]", "inactive", null),
                "update_room_response", "200", "Sala atualizada com sucesso");
        JsonObject sala = ler(tokenAdmin, "1");
        assertEquals("Sala Nova", sala.get("name").getAsString());
        assertEquals("12", sala.get("capacity").getAsString());
        assertEquals("Bloco A", sala.get("location").getAsString());
        assertEquals("[\"projetor\",\"quadro\"]", sala.get("resources").toString());
        assertEquals("inactive", sala.get("room_status").getAsString());
        assertEquals(3, sala.getAsJsonArray("schedule").size());

        // schedule presente substitui; [] limpa
        atualizar(tokenAdmin, "1", "", "16", "", "[]", "active", "[{\"day\":\"sabado\",\"slot\":\"N5\"}]");
        sala = ler(tokenAdmin, "1");
        assertEquals("16", sala.get("capacity").getAsString());
        assertEquals("active", sala.get("room_status").getAsString());
        assertEquals("[{\"day\":\"sabado\",\"slot\":\"N5\"}]", sala.get("schedule").toString());
        atualizar(tokenAdmin, "1", "", "", "", "[]", "", "[]");
        assertEquals(0, ler(tokenAdmin, "1").getAsJsonArray("schedule").size());
        // Manter o proprio nome nao e conflito
        assertResposta(atualizar(tokenAdmin, "1", "Sala Nova", "", "", "[]", "", null),
                "update_room_response", "200", "Sala atualizada com sucesso");

        assertResposta(atualizar(tokenAdmin, "1", "Sala B", "", "", "[]", "", null),
                "update_room_response", "409", "Sala ja cadastrada");
        assertResposta(atualizar(tokenAdmin, "99", "", "5", "", "[]", "", null),
                "update_room_response", "404", "Sala nao encontrada");
        assertResposta(atualizar(tokenAdmin, "1", "", "", "", "[]", "Active", null), "update_room_response", "400", DADOS_SALA);
        assertResposta(atualizar(tokenAdmin, "x", "", "", "", "[]", "", null), "update_room_response", "400", DADOS_SALA);
        assertResposta(atualizar(tokenAdmin, "1", "", "0", "", "[]", "", null), "update_room_response", "400", DADOS_SALA);
        assertResposta(enviar("{\"op\":\"update_room\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"1\",\"name\":\"\","
                + "\"capacity\":\"\",\"location\":\"\",\"room_status\":\"\"}"), "update_room_response", "400", DADOS_SALA);
        assertResposta(enviar("{\"op\":\"update_room\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"1\",\"name\":\"\","
                + "\"capacity\":\"\",\"location\":\"\",\"resources\":[]}"), "update_room_response", "400", DADOS_SALA);
        assertResposta(atualizar(tokenJoao, "1", "", "5", "", "[]", "", null), "update_room_response", "403", PERMISSAO);

        // Capacidade abaixo dos participants de uma reserva futura → 409; reserva passada nao impede
        reservar("joao", 2, PASSADA, "08:00", "09:00", 4);
        assertResposta(atualizar(tokenAdmin, "2", "", "2", "", "[]", "", null),
                "update_room_response", "200", "Sala atualizada com sucesso");
        reservar("joao", 1, FUTURA, "08:00", "09:00", 10);
        assertResposta(atualizar(tokenAdmin, "1", "", "8", "", "[]", "", null),
                "update_room_response", "409", "Sala possui reservas ativas");
        assertResposta(atualizar(tokenAdmin, "1", "", "10", "", "[]", "", null),
                "update_room_response", "200", "Sala atualizada com sucesso");
    }

    @Test
    void deleteRoom() throws Exception {
        criar(tokenAdmin, "Sala A", "12", "Bloco A", "[]", SCHEDULE);
        criar(tokenAdmin, "Sala B", "12", "Bloco B", "[]", null);
        reservar("joao", 1, FUTURA, "08:00", "09:00", 2);
        reservar("joao", 2, PASSADA, "08:00", "09:00", 2);

        assertResposta(apagar(tokenAdmin, "1"), "delete_room_response", "409", "Sala possui reservas ativas");
        assertResposta(apagar(tokenJoao, "2"), "delete_room_response", "403", PERMISSAO);
        assertResposta(apagar(tokenAdmin, "x"), "delete_room_response", "400", DADOS);
        assertResposta(apagar(tokenAdmin, "99"), "delete_room_response", "404", "Sala nao encontrada");

        // So reserva passada: a sala sai dos registros junto com o historico dela
        assertResposta(apagar(tokenAdmin, "2"), "delete_room_response", "200", "Sala removida com sucesso");
        assertResposta(ler(tokenAdmin, "2"), "read_room_response", "404", "Sala nao encontrada");
        assertEquals("1", listarReservas(tokenAdmin, "all").get("count").getAsString());
        // O room_id removido nao e reutilizado
        assertEquals("3", criar(tokenAdmin, "Sala C", "5", "Bloco C", "[]", null).get("room_id").getAsString());
    }

    @Test
    void listReservations() throws Exception {
        criar(tokenAdmin, "Sala A", "12", "Bloco A", "[]", null);
        criar(tokenAdmin, "Sala B", "12", "Bloco B", "[]", null);
        reservar("joao", 1, FUTURA, "14:00", "16:00", 6);
        reservar("admin", 2, FUTURA, "08:00", "09:00", 2);

        // Admin com scope "all" ve quem reservou e quando
        JsonObject todas = listarReservas(tokenAdmin, "all");
        assertResposta(todas, "list_reservations_response", "200", CONSULTA);
        assertEquals("2", todas.get("count").getAsString());
        JsonObject primeira = todas.getAsJsonArray("reservations").get(1).getAsJsonObject();
        assertEquals("1", primeira.get("reservation_id").getAsString());
        assertEquals("1", primeira.get("room_id").getAsString());
        assertEquals("Sala A", primeira.get("name").getAsString());
        assertEquals("joao", primeira.get("user").getAsString());
        assertEquals(FUTURA, primeira.get("date").getAsString());
        assertEquals("14:00", primeira.get("start_time").getAsString());
        assertEquals("16:00", primeira.get("end_time").getAsString());
        assertEquals("Reuniao de projeto", primeira.get("topic").getAsString());
        assertEquals("6", primeira.get("participants").getAsString());

        assertEquals("1", enviar("{\"op\":\"list_reservations\",\"token\":\"" + tokenAdmin
                + "\",\"scope\":\"all\",\"room_id\":\"2\"}").get("count").getAsString());
        assertEquals("0", enviar("{\"op\":\"list_reservations\",\"token\":\"" + tokenAdmin
                + "\",\"scope\":\"all\",\"room_id\":\"99\"}").get("count").getAsString());
        assertEquals("0", enviar("{\"op\":\"list_reservations\",\"token\":\"" + tokenAdmin
                + "\",\"scope\":\"all\",\"date\":\"2099-01-11\"}").get("count").getAsString());

        // Sem scope vale "mine"; usuario comum com "all" → 403
        JsonObject minhas = enviar("{\"op\":\"list_reservations\",\"token\":\"" + tokenJoao + "\"}");
        assertEquals("1", minhas.get("count").getAsString());
        assertEquals("joao", minhas.getAsJsonArray("reservations").get(0).getAsJsonObject().get("user").getAsString());
        assertResposta(listarReservas(tokenJoao, "all"), "list_reservations_response", "403", PERMISSAO);
        assertResposta(listarReservas(tokenAdmin, "todas"), "list_reservations_response", "400", FILTROS);
        assertResposta(enviar("{\"op\":\"list_reservations\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"a\"}"),
                "list_reservations_response", "400", FILTROS);
        assertResposta(listarReservas(TOKEN_FALSO, "all"), "list_reservations_response", "401", "Token invalido ou expirado");

        // Remover a conta apaga todas as reservas dela (regra 3.13)
        enviar("{\"op\":\"admin_delete_user\",\"token\":\"" + tokenAdmin + "\",\"target_user\":\"joao\"}");
        assertEquals("1", listarReservas(tokenAdmin, "all").get("count").getAsString());
    }

    /** Insere uma reserva direto no banco, ja que o create_reservation ainda nao existe. */
    private void reservar(String user, long salaId, String date, String inicio, String fim, int participants) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + arquivo());
             PreparedStatement ps = c.prepareStatement("INSERT INTO reservas (sala_id, usuario_id, date, start_time, "
                     + "end_time, topic, participants) VALUES (?, (SELECT id FROM usuarios WHERE user = ?), ?, ?, ?, "
                     + "'Reuniao de projeto', ?)")) {
            ps.setLong(1, salaId);
            ps.setString(2, user);
            ps.setString(3, date);
            ps.setString(4, inicio);
            ps.setString(5, fim);
            ps.setInt(6, participants);
            ps.executeUpdate();
        }
    }

    private JsonObject criar(String token, String name, String capacity, String location, String resources,
                             String schedule) throws IOException {
        return enviar("{\"op\":\"create_room\",\"token\":\"" + token + "\",\"name\":\"" + name + "\",\"capacity\":\""
                + capacity + "\",\"location\":\"" + location + "\",\"resources\":" + resources
                + (schedule == null ? "" : ",\"schedule\":" + schedule) + "}");
    }

    private JsonObject atualizar(String token, String roomId, String name, String capacity, String location,
                                 String resources, String status, String schedule) throws IOException {
        return enviar("{\"op\":\"update_room\",\"token\":\"" + token + "\",\"room_id\":\"" + roomId + "\",\"name\":\""
                + name + "\",\"capacity\":\"" + capacity + "\",\"location\":\"" + location + "\",\"resources\":"
                + resources + ",\"room_status\":\"" + status + "\"" + (schedule == null ? "" : ",\"schedule\":" + schedule) + "}");
    }

    private JsonObject ler(String token, String roomId) throws IOException {
        return enviar("{\"op\":\"read_room\",\"token\":\"" + token + "\",\"room_id\":\"" + roomId + "\"}");
    }

    private JsonObject apagar(String token, String roomId) throws IOException {
        return enviar("{\"op\":\"delete_room\",\"token\":\"" + token + "\",\"room_id\":\"" + roomId + "\"}");
    }

    private JsonObject listarNoPeriodo(String inicio, String fim, String minCapacity) throws IOException {
        return enviar("{\"op\":\"list_rooms\",\"token\":\"" + tokenJoao + "\",\"date\":\"" + FUTURA + "\",\"start_time\":\""
                + inicio + "\",\"end_time\":\"" + fim + "\"" + (minCapacity == null ? "" : ",\"min_capacity\":\"" + minCapacity + "\"") + "}");
    }

    private JsonObject listarReservas(String token, String scope) throws IOException {
        return enviar("{\"op\":\"list_reservations\",\"token\":\"" + token + "\",\"scope\":\"" + scope + "\"}");
    }

    private static String token(JsonObject login) {
        assertResposta(login, "login_response", "200", "Login realizado com sucesso");
        return login.get("token").getAsString();
    }

    private JsonObject enviar(String linha) throws IOException {
        conexao.enviarTexto(linha);
        return Json.parseObjeto(conexao.receber());
    }

    private String arquivo() {
        return pasta.resolve("teste.db").toString();
    }

    private static void assertResposta(JsonObject resposta, String op, String status, String message) {
        assertEquals(op, resposta.get("op").getAsString(), resposta.toString());
        assertEquals(status, resposta.get("status").getAsString(), resposta.toString());
        assertEquals(message, resposta.get("message").getAsString(), resposta.toString());
        // Protocolo 2.1: 'op' e sempre a primeira chave
        assertEquals("op", resposta.keySet().iterator().next());
    }
}
