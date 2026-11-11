package br.edu.utfpr.sd.servidor;

import static br.edu.utfpr.sd.servidor.ServidorDeTeste.assertResposta;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.edu.utfpr.sd.comum.ConexaoJson;
import br.edu.utfpr.sd.comum.Json;
import br.edu.utfpr.sd.servidor.ServidorDeTeste.Cliente;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regras novas das versoes 2.9.0 e 2.9.1 do protocolo e testes de fronteira das regex do dicionario,
 * contra o servidor real (fluxo secundario da EP-2: o cliente erra e o servidor responde o erro exato).
 */
class Protocolo291Test {

    private static final String DADOS = "Dados em formato invalido";
    private static final String DADOS_SALA = "Dados da sala em formato invalido";
    private static final String FILTROS = "Filtros em formato invalido";
    private static final String CADASTRO = "Dados de cadastro em formato invalido";
    private static final String CREDENCIAIS = "Email ou senha em formato invalido";
    private static final String TOKEN_FORMATO = "Token em formato invalido";
    private static final String NADA = "Nenhum dado para atualizar";
    private static final String PERMISSAO = "Permissao insuficiente";
    private static final String SALA_ATUALIZADA = "Sala atualizada com sucesso";
    private static final String CAPACIDADE = "Capacidade menor que participantes de reservas ativas";
    // Sempre futura / sempre passada; 2099-01-10 e um sabado
    private static final String FUTURA = "2099-01-10";
    private static final String PASSADA = "2000-01-10";

    @TempDir
    Path pasta;

    private ServidorDeTeste servidor;
    private Cliente admin;
    private Cliente joao;
    private String tokenAdmin;
    private String tokenJoao;

    @BeforeEach
    void iniciar() throws Exception {
        servidor = new ServidorDeTeste(pasta);
        admin = servidor.conectar();
        joao = servidor.conectar();
        tokenAdmin = admin.logarAdmin();
        tokenJoao = joao.cadastrarELogar("joao");
    }

    @AfterEach
    void encerrar() throws IOException {
        admin.close();
        joao.close();
        servidor.close();
    }

    /** Regras 2.10 e 4.2 (v2.9/v2.9.1): token ausente e 400 com a message de formato de cada operacao. */
    @Test
    void tokenAusenteResponde400ComAMessageDaOperacao() throws IOException {
        Map<String, String> esperado = Map.ofEntries(
                Map.entry("logout", TOKEN_FORMATO),
                Map.entry("read_user", TOKEN_FORMATO),
                Map.entry("admin_list_users", TOKEN_FORMATO),
                Map.entry("delete_user", TOKEN_FORMATO),
                Map.entry("update_user", DADOS),
                Map.entry("admin_read_user", DADOS),
                Map.entry("admin_update_user", DADOS),
                Map.entry("admin_delete_user", DADOS),
                Map.entry("create_room", DADOS_SALA),
                Map.entry("update_room", DADOS_SALA),
                Map.entry("read_room", DADOS),
                Map.entry("delete_room", DADOS),
                Map.entry("list_rooms", FILTROS),
                Map.entry("list_reservations", FILTROS));
        for (Map.Entry<String, String> e : esperado.entrySet()) {
            assertResposta(joao.enviar("{\"op\":\"" + e.getKey() + "\"}"), e.getKey() + "_response", "400", e.getValue());
            // token null e fora do formato: mesma resposta
            assertResposta(joao.enviar("{\"op\":\"" + e.getKey() + "\",\"token\":null}"), e.getKey() + "_response", "400",
                    e.getValue());
            assertResposta(joao.enviar("{\"op\":\"" + e.getKey() + "\",\"token\":\"" + tokenJoao.toUpperCase() + "\"}"),
                    e.getKey() + "_response", "400", e.getValue());
        }
        // delete_user com token valido e senha fora do formato continua "Senha em formato invalido"
        assertResposta(joao.enviar("{\"op\":\"delete_user\",\"token\":\"" + tokenJoao + "\",\"password\":\"a b\"}"),
                "delete_user_response", "400", "Senha em formato invalido");
        // Regra 1.12: depois de tantos erros a conexao continua aberta e a sessao vale
        assertResposta(joao.comToken("read_user", tokenJoao), "read_user_response", "200", "Consulta realizada com sucesso");
    }

    /** Regra 2.12 (v2.9): user, email e target_user so aceitam minusculas e o servidor nao converte. */
    @Test
    void maiusculasEmUserEEmailRespondem400() throws IOException {
        assertResposta(joao.registrar("Maria", "maria@email.com", "senha123"), "register_response", "400", CADASTRO);
        assertResposta(joao.registrar("maria", "Maria@email.com", "senha123"), "register_response", "400", CADASTRO);
        assertResposta(joao.enviar("{\"op\":\"login\",\"email\":\"JOAO@email.com\",\"password\":\"senha123\"}"),
                "login_response", "400", CREDENCIAIS);
        assertResposta(joao.enviar("{\"op\":\"update_user\",\"token\":\"" + tokenJoao + "\",\"user\":\"Joana\",\"password\":\"\"}"),
                "update_user_response", "400", DADOS);
        for (String op : new String[] {"admin_read_user", "admin_delete_user"}) {
            assertResposta(admin.enviar("{\"op\":\"" + op + "\",\"token\":\"" + tokenAdmin + "\",\"target_user\":\"JOAO\"}"),
                    op + "_response", "400", DADOS);
        }
        assertResposta(admin.enviar("{\"op\":\"admin_update_user\",\"token\":\"" + tokenAdmin
                + "\",\"target_user\":\"joao\",\"user\":\"Joao\",\"password\":\"\",\"role\":\"\"}"),
                "admin_update_user_response", "400", DADOS);
        // Nada foi alterado
        assertEquals("joao", joao.comToken("read_user", tokenJoao).get("user").getAsString());
    }

    /** Fronteiras das regex de cadastro: user 30/31, senha 20/21, email 100/101 (v2.9.1). */
    @Test
    void fronteirasDoCadastro() throws IOException {
        String user30 = "a".repeat(30);
        assertResposta(joao.registrar(user30, "u30@email.com", "senha123"), "register_response", "201", "Usuario cadastrado com sucesso");
        assertResposta(joao.registrar(user30 + "a", "u31@email.com", "senha123"), "register_response", "400", CADASTRO);
        assertResposta(joao.registrar("vinte", "s20@email.com", "a".repeat(20)), "register_response", "201", "Usuario cadastrado com sucesso");
        assertResposta(joao.registrar("vinteum", "s21@email.com", "a".repeat(21)), "register_response", "400", CADASTRO);
        // email: "@e.com" tem 6 caracteres
        String email100 = "b".repeat(94) + "@e.com";
        String email101 = "c".repeat(95) + "@e.com";
        assertEquals(100, email100.length());
        assertResposta(joao.registrar("cem", email100, "senha123"), "register_response", "201", "Usuario cadastrado com sucesso");
        assertResposta(joao.registrar("centoeum", email101, "senha123"), "register_response", "400", CADASTRO);
        assertResposta(joao.enviar("{\"op\":\"login\",\"email\":\"" + email101 + "\",\"password\":\"senha123\"}"),
                "login_response", "400", CREDENCIAIS);
        joao.logar(email100, "senha123");
        // Outros emails do dicionario: terminacao de 1 letra e espaco
        assertResposta(joao.registrar("um", "a@b.c", "senha123"), "register_response", "400", CADASTRO);
        assertResposta(joao.registrar("dois", "a b@email.com", "senha123"), "register_response", "400", CADASTRO);
    }

    /** Regra 2.15 (v2.9.1): valor igual ao atual e alteracao (200) e a unicidade nao conflita com o proprio registro. */
    @Test
    void valorIgualAoAtualEAlteracao() throws IOException {
        assertResposta(joao.enviar("{\"op\":\"update_user\",\"token\":\"" + tokenJoao + "\",\"user\":\"joao\",\"password\":\"\"}"),
                "update_user_response", "200", "Dados atualizados com sucesso");
        criarSala("Sala A", "10", "[\"tv\"]");
        assertResposta(atualizar("1", "Sala A", "", "", null, "", null), "update_room_response", "200", SALA_ATUALIZADA);
    }

    /** Regra 3.4 (v2.9.1): alterar user, senha ou role pelo admin nao encerra a sessao do alvo. */
    @Test
    void alterarUsuarioPeloAdminNaoEncerraASessao() throws IOException {
        assertResposta(admin.enviar("{\"op\":\"admin_update_user\",\"token\":\"" + tokenAdmin
                + "\",\"target_user\":\"joao\",\"user\":\"joana\",\"password\":\"nova123\",\"role\":\"admin\"}"),
                "admin_update_user_response", "200", "Dados atualizados com sucesso");
        JsonObject lido = joao.comToken("read_user", tokenJoao);
        assertEquals("joana", lido.get("user").getAsString());
        assertEquals("admin", lido.get("role").getAsString());
        // Promovido na hora (whitelist 3.14)
        assertResposta(joao.comToken("admin_list_users", tokenJoao), "admin_list_users_response", "200",
                "Consulta realizada com sucesso");
        // Rebaixado: continua logado, mas perde o acesso de admin na hora
        assertResposta(admin.enviar("{\"op\":\"admin_update_user\",\"token\":\"" + tokenAdmin
                + "\",\"target_user\":\"joana\",\"user\":\"\",\"password\":\"\",\"role\":\"user\"}"),
                "admin_update_user_response", "200", "Dados atualizados com sucesso");
        assertResposta(joao.comToken("admin_list_users", tokenJoao), "admin_list_users_response", "403", PERMISSAO);
        assertResposta(joao.comToken("read_user", tokenJoao), "read_user_response", "200", "Consulta realizada com sucesso");
    }

    /** Secao 4 (v2.9): o 400 de formato vem antes do 403 de perfil, e o 404 antes do 409. */
    @Test
    void ordemDeValidacao() throws IOException {
        // Usuario comum: create_room invalido -> 400; valido -> 403
        assertResposta(joao.enviar("{\"op\":\"create_room\",\"token\":\"" + tokenJoao + "\",\"name\":\"\",\"capacity\":\"5\","
                + "\"location\":\"B\",\"resources\":[],\"slots\":[\"2M1\"]}"), "create_room_response", "400", DADOS_SALA);
        assertResposta(joao.enviar("{\"op\":\"create_room\",\"token\":\"" + tokenJoao + "\",\"name\":\"Sala X\",\"capacity\":\"5\","
                + "\"location\":\"B\",\"resources\":[],\"slots\":[\"2M1\"],\"role\":\"admin\"}"), "create_room_response", "403", PERMISSAO);
        // update_room tudo vazio de usuario comum: 400 "Nenhum dado" vem antes do 403 (passo 2 da secao 4)
        assertResposta(joao.enviar("{\"op\":\"update_room\",\"token\":\"" + tokenJoao + "\",\"room_id\":\"1\",\"name\":\"\","
                + "\"capacity\":\"\",\"location\":\"\",\"room_status\":\"\"}"), "update_room_response", "400", NADA);
        // Token valido sem sessao (forjado) com dados validos -> 401 antes do 403
        String forjado = "f".repeat(64);
        assertResposta(joao.enviar("{\"op\":\"delete_room\",\"token\":\"" + forjado + "\",\"room_id\":\"1\"}"),
                "delete_room_response", "401", "Token invalido ou expirado");
        // Admin: sala inexistente -> 404 mesmo com nome que ja existe
        criarSala("Sala A", "10", "[]");
        assertResposta(atualizar("99", "Sala A", "", "", null, "", null), "update_room_response", "404", "Sala nao encontrada");
        // admin_update_user: usuario inexistente -> 404 mesmo com user ja em uso
        assertResposta(admin.enviar("{\"op\":\"admin_update_user\",\"token\":\"" + tokenAdmin
                + "\",\"target_user\":\"ninguem\",\"user\":\"joao\",\"password\":\"\",\"role\":\"\"}"),
                "admin_update_user_response", "404", "Usuario nao encontrado");
    }

    /** Regras 9.7 e 2.13 (v2.9): resources no update_room ([] mantem, ["0"] apaga) e nada para atualizar. */
    @Test
    void resourcesENadaParaAtualizarNoUpdateRoom() throws IOException {
        criarSala("Sala A", "10", "[\"projetor\",\"tv\"]");
        // resources ausente (v2.9.1) e [] mantem a lista
        assertResposta(atualizar("1", "", "12", "", null, "", null), "update_room_response", "200", SALA_ATUALIZADA);
        assertEquals("[\"projetor\",\"tv\"]", ler("1").get("resources").toString());
        assertResposta(atualizar("1", "", "", "Bloco C", "[]", "", null), "update_room_response", "200", SALA_ATUALIZADA);
        assertEquals("[\"projetor\",\"tv\"]", ler("1").get("resources").toString());
        // Lista nova substitui; ["0"] apaga todos (e sozinho ja conta como alteracao)
        assertResposta(atualizar("1", "", "", "", "[\"quadro\"]", "", null), "update_room_response", "200", SALA_ATUALIZADA);
        assertEquals("[\"quadro\"]", ler("1").get("resources").toString());
        assertResposta(atualizar("1", "", "", "", "[\"0\"]", "", null), "update_room_response", "200", SALA_ATUALIZADA);
        assertEquals("[]", ler("1").get("resources").toString());
        // "0" misturado, recurso fora da regex, 31 caracteres, null e tipo errado -> 400
        for (String invalido : new String[] {"[\"0\",\"tv\"]", "[\"tv;\"]", "[\"" + "r".repeat(31) + "\"]", "[\"\"]",
                "null", "\"tv\"", "[1]", "[\"projétor\"]"}) {
            assertResposta(atualizar("1", "", "", "", invalido, "", null), "update_room_response", "400", DADOS_SALA);
        }
        assertResposta(atualizar("1", "", "", "", "[\"" + "r".repeat(30) + "\"]", "", null), "update_room_response", "200",
                SALA_ATUALIZADA);
        // Tudo vazio: resources e slots ausentes ou [] -> 400 "Nenhum dado para atualizar"
        assertResposta(atualizar("1", "", "", "", null, "", null), "update_room_response", "400", NADA);
        assertResposta(atualizar("1", "", "", "", "[]", "", "[]"), "update_room_response", "400", NADA);
        // Outro erro de formato tem prioridade sobre o "Nenhum dado" (regra 2.13)
        assertResposta(atualizar("1", "", "", "", "[\"0\",\"x\"]", "", "[]"), "update_room_response", "400", DADOS_SALA);
        // Campos obrigatorios do update_room ausentes -> 400
        assertResposta(admin.enviar("{\"op\":\"update_room\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"1\",\"name\":\"\","
                + "\"capacity\":\"5\",\"location\":\"\"}"), "update_room_response", "400", DADOS_SALA);
        // create_room: recurso "0" e recurso fora da regex -> 400
        assertResposta(criarSala("Sala B", "5", "[\"0\"]"), "create_room_response", "400", DADOS_SALA);
        assertResposta(criarSala("Sala B", "5", "[\"tv,hdmi\"]"), "create_room_response", "400", DADOS_SALA);
        assertResposta(admin.enviar("{\"op\":\"create_room\",\"token\":\"" + tokenAdmin + "\",\"name\":\"Sala B\",\"capacity\":\"5\","
                + "\"location\":\"B\",\"slots\":[\"2M1\"]}"), "create_room_response", "400", DADOS_SALA);
    }

    /** Regra 9.6 (v2.9): capacity abaixo dos participants de reserva ativa -> 409 com a message nova. */
    @Test
    void capacidadeAbaixoDeReservaAtiva() throws Exception {
        criarSala("Sala A", "10", "[]");
        criarSala("Sala B", "10", "[]");
        servidor.reservar("joao", 1, FUTURA, "13:00", "14:40", 8);
        servidor.reservar("joao", 2, PASSADA, "13:00", "14:40", 9);
        assertResposta(atualizar("1", "", "7", "", null, "", null), "update_room_response", "409", CAPACIDADE);
        // Fronteira: igual aos participants passa
        assertResposta(atualizar("1", "", "8", "", null, "", null), "update_room_response", "200", SALA_ATUALIZADA);
        // Nome ja cadastrado e capacidade baixa ao mesmo tempo: vale "Sala ja cadastrada" (secao 4)
        assertResposta(atualizar("1", "Sala B", "1", "", null, "", null), "update_room_response", "409", "Sala ja cadastrada");
        // Reserva encerrada nao conta
        assertResposta(atualizar("2", "", "1", "", null, "", null), "update_room_response", "200", SALA_ATUALIZADA);
        // delete_room: ativa bloqueia (9.8), encerrada e apagada junto
        assertResposta(admin.enviar("{\"op\":\"delete_room\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"1\"}"),
                "delete_room_response", "409", "Sala possui reservas ativas");
        assertResposta(admin.enviar("{\"op\":\"delete_room\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"2\"}"),
                "delete_room_response", "200", "Sala removida com sucesso");
        assertEquals("1", admin.enviar("{\"op\":\"list_reservations\",\"token\":\"" + tokenAdmin + "\",\"scope\":\"all\"}")
                .get("count").getAsString());
    }

    /** Fronteiras das regex de sala: name 30/31, capacity 1/9999/0/10000/01, location 60/61, room_id 10/11 digitos. */
    @Test
    void fronteirasDaSala() throws IOException {
        assertResposta(criarSala("N".repeat(30), "1", "[]"), "create_room_response", "201", "Sala cadastrada com sucesso");
        assertResposta(criarSala("N".repeat(31), "1", "[]"), "create_room_response", "400", DADOS_SALA);
        assertResposta(criarSala("Sala Max", "9999", "[]"), "create_room_response", "201", "Sala cadastrada com sucesso");
        for (String capacidade : new String[] {"0", "10000", "01", "-1", "1.5", " 5"}) {
            assertResposta(criarSala("Sala Cap", capacidade, "[]"), "create_room_response", "400", DADOS_SALA);
        }
        assertResposta(criarSala("Sala Acentuada é", "5", "[]"), "create_room_response", "400", DADOS_SALA);
        String local60 = "L".repeat(60);
        assertResposta(admin.enviar("{\"op\":\"create_room\",\"token\":\"" + tokenAdmin + "\",\"name\":\"Sala L\",\"capacity\":\"5\","
                + "\"location\":\"" + local60 + "\",\"resources\":[],\"slots\":[\"2M1\"]}"), "create_room_response", "201",
                "Sala cadastrada com sucesso");
        assertResposta(admin.enviar("{\"op\":\"create_room\",\"token\":\"" + tokenAdmin + "\",\"name\":\"Sala L2\",\"capacity\":\"5\","
                + "\"location\":\"" + local60 + "L\",\"resources\":[],\"slots\":[\"2M1\"]}"), "create_room_response", "400", DADOS_SALA);
        // room_id: 10 digitos e formato valido (404); 11 digitos -> 400
        assertResposta(ler("9999999999"), "read_room_response", "404", "Sala nao encontrada");
        assertResposta(ler("99999999999"), "read_room_response", "400", DADOS);
        // slots: dia 1 e 8, aula N6 e M7, minusculo, vazio -> 400
        for (String slots : new String[] {"[\"1M1\"]", "[\"8M1\"]", "[\"2N6\"]", "[\"2M7\"]", "[\"2m1\"]", "[]"}) {
            assertResposta(admin.enviar("{\"op\":\"create_room\",\"token\":\"" + tokenAdmin + "\",\"name\":\"Sala S\",\"capacity\":\"5\","
                    + "\"location\":\"B\",\"resources\":[],\"slots\":" + slots + "}"), "create_room_response", "400", DADOS_SALA);
        }
        assertResposta(admin.enviar("{\"op\":\"create_room\",\"token\":\"" + tokenAdmin + "\",\"name\":\"Sala S\",\"capacity\":\"5\","
                + "\"location\":\"B\",\"resources\":[],\"slots\":[\"7N5\",\"2M1\",\"2T6\"]}"), "create_room_response", "201",
                "Sala cadastrada com sucesso");
    }

    /** Regras 2.14, 9.2, 9.4, 9.11 e 9.12: filtros opcionais, datas inexistentes e periodos invalidos. */
    @Test
    void filtrosEDatas() throws IOException {
        criarSala("Sala A", "10", "[]");
        // Filtro enviado vazio ou null -> 400 (2.14)
        for (String filtro : new String[] {"\"date\":\"\"", "\"room_id\":\"\"", "\"scope\":\"\"", "\"date\":null", "\"scope\":\"ALL\""}) {
            assertResposta(admin.enviar("{\"op\":\"list_reservations\",\"token\":\"" + tokenAdmin + "\"," + filtro + "}"),
                    "list_reservations_response", "400", FILTROS);
        }
        for (String filtro : new String[] {"\"min_capacity\":\"\"", "\"min_capacity\":\"0\"", "\"date\":\"\",\"start_time\":\"\",\"end_time\":\"\""}) {
            assertResposta(joao.enviar("{\"op\":\"list_rooms\",\"token\":\"" + tokenJoao + "\"," + filtro + "}"),
                    "list_rooms_response", "400", FILTROS);
        }
        assertResposta(joao.enviar("{\"op\":\"read_room\",\"token\":\"" + tokenJoao + "\",\"room_id\":\"1\",\"date\":\"\"}"),
                "read_room_response", "400", DADOS);
        // Data inexistente no calendario e fora da regex nova (mes 13, dia 32, 29/02 em ano nao bissexto) -> 400
        for (String data : new String[] {"2026-02-30", "2026-13-01", "2026-00-10", "2026-01-32", "2027-02-29", "26-01-01"}) {
            assertResposta(admin.enviar("{\"op\":\"list_reservations\",\"token\":\"" + tokenAdmin + "\",\"date\":\"" + data + "\"}"),
                    "list_reservations_response", "400", FILTROS);
            assertResposta(joao.enviar("{\"op\":\"read_room\",\"token\":\"" + tokenJoao + "\",\"room_id\":\"1\",\"date\":\"" + data + "\"}"),
                    "read_room_response", "400", DADOS);
            assertResposta(listarRooms(data, "13:00", "13:50"), "list_rooms_response", "400", FILTROS);
        }
        // 29/02 em ano bissexto e valido; datas passadas sao aceitas nas consultas (9.4)
        assertResposta(listarRooms("2028-02-29", "13:00", "13:50"), "list_rooms_response", "200", "Consulta realizada com sucesso");
        assertResposta(listarRooms(PASSADA, "13:00", "13:50"), "list_rooms_response", "200", "Consulta realizada com sucesso");
        assertResposta(joao.enviar("{\"op\":\"read_room\",\"token\":\"" + tokenJoao + "\",\"room_id\":\"1\",\"date\":\"" + PASSADA + "\"}"),
                "read_room_response", "200", "Consulta realizada com sucesso");
        // end_time <= start_time e horario fora da tabela -> 400 (9.11 e 8.3); a fronteira de uma aula so passa
        assertResposta(listarRooms(FUTURA, "13:50", "13:50"), "list_rooms_response", "400", FILTROS);
        assertResposta(listarRooms(FUTURA, "14:40", "13:50"), "list_rooms_response", "400", FILTROS);
        assertResposta(listarRooms(FUTURA, "13:10", "13:50"), "list_rooms_response", "400", FILTROS);
        assertResposta(listarRooms(FUTURA, "13:00", "13:50"), "list_rooms_response", "200", "Consulta realizada com sucesso");
        // Parte do trio -> 400
        assertResposta(joao.enviar("{\"op\":\"list_rooms\",\"token\":\"" + tokenJoao + "\",\"date\":\"" + FUTURA + "\"}"),
                "list_rooms_response", "400", FILTROS);
        // Sala inexistente no filtro -> 200 vazio (9.12)
        JsonObject vazio = admin.enviar("{\"op\":\"list_reservations\",\"token\":\"" + tokenAdmin + "\",\"scope\":\"all\",\"room_id\":\"77\"}");
        assertResposta(vazio, "list_reservations_response", "200", "Consulta realizada com sucesso");
        assertEquals("0", vazio.get("count").getAsString());
        // scope "all" de usuario comum -> 403, mesmo dizendo role admin
        assertResposta(joao.enviar("{\"op\":\"list_reservations\",\"token\":\"" + tokenJoao + "\",\"scope\":\"all\",\"role\":\"admin\"}"),
                "list_reservations_response", "403", PERMISSAO);
    }

    /** Regra 1.6 (v2.9.1): resposta acima de 8192 bytes e entregue inteira e o cliente le ate o '\n'. */
    @Test
    void respostaMaiorQue8192BytesChegaInteira() throws Exception {
        for (int i = 0; i < 120; i++) {
            admin.registrar("usuario" + letras(i), "usuario.numero." + i + "@dominio-de-teste.com.br", "senha123");
        }
        try (Socket socket = new Socket("127.0.0.1", porta());
             ConexaoJson cliente = new ConexaoJson(socket, "ClienteSemLimite", false)) {
            cliente.enviarTexto("{\"op\":\"admin_list_users\",\"token\":\"" + tokenAdmin + "\"}");
            String linha = cliente.receber();
            assertTrue(linha.getBytes().length > 8192, "resposta com " + linha.getBytes().length + " bytes");
            JsonObject resp = Json.parseObjeto(linha);
            assertEquals("122", resp.get("count").getAsString());
            assertEquals(122, resp.getAsJsonArray("users").size());
        }
    }

    // ------------------------------------------------------------------ apoio

    private int porta() throws Exception {
        java.lang.reflect.Field campo = ServidorDeTeste.class.getDeclaredField("serverSocket");
        campo.setAccessible(true);
        return ((java.net.ServerSocket) campo.get(servidor)).getLocalPort();
    }

    /** Sufixo so com letras (user nao aceita digitos): 0 -> "a", 26 -> "ba". */
    private static String letras(int n) {
        StringBuilder s = new StringBuilder();
        do {
            s.insert(0, (char) ('a' + n % 26));
            n /= 26;
        } while (n > 0);
        return s.toString();
    }

    private JsonObject criarSala(String name, String capacity, String resources) throws IOException {
        return admin.enviar("{\"op\":\"create_room\",\"token\":\"" + tokenAdmin + "\",\"name\":\"" + name + "\",\"capacity\":\""
                + capacity + "\",\"location\":\"Bloco B\",\"resources\":" + resources + ",\"slots\":[\"7T1\",\"7T2\"]}");
    }

    /** resources e slots null = chave ausente. */
    private JsonObject atualizar(String roomId, String name, String capacity, String location, String resources,
                                 String status, String slots) throws IOException {
        return admin.enviar("{\"op\":\"update_room\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"" + roomId + "\",\"name\":\""
                + name + "\",\"capacity\":\"" + capacity + "\",\"location\":\"" + location + "\",\"room_status\":\"" + status + "\""
                + (resources == null ? "" : ",\"resources\":" + resources) + (slots == null ? "" : ",\"slots\":" + slots) + "}");
    }

    private JsonObject ler(String roomId) throws IOException {
        return admin.enviar("{\"op\":\"read_room\",\"token\":\"" + tokenAdmin + "\",\"room_id\":\"" + roomId + "\"}");
    }

    private JsonObject listarRooms(String date, String inicio, String fim) throws IOException {
        return joao.enviar("{\"op\":\"list_rooms\",\"token\":\"" + tokenJoao + "\",\"date\":\"" + date + "\",\"start_time\":\""
                + inicio + "\",\"end_time\":\"" + fim + "\"}");
    }
}
