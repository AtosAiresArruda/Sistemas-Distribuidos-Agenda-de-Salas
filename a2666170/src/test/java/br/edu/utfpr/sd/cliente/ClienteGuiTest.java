package br.edu.utfpr.sd.cliente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.edu.utfpr.sd.comum.ConexaoJson;
import br.edu.utfpr.sd.comum.Json;
import br.edu.utfpr.sd.servidor.BancoDados;
import br.edu.utfpr.sd.servidor.MonitorServidor;
import br.edu.utfpr.sd.servidor.Servidor;
import br.edu.utfpr.sd.servidor.TratadorCliente;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Plano de testes da EP-2, lado do cliente (itens a a g): abre o ClienteApp de verdade (janela JavaFX),
 * preenche os campos e aciona as operacoes como o usuario faria.
 * - Cliente -> Servidor: o JSON que o servidor recebeu e conferido campo a campo.
 * - Servidor -> Cliente: a tela reflete a resposta; um servidor falso manda respostas estranhas.
 * Abre janelas, entao so roda quando pedido: mvn test -Dgui=true -Dtest=ClienteGuiTest
 */
@EnabledIfSystemProperty(named = "gui", matches = "true")
class ClienteGuiTest {

    // Uma segunda-feira (dia 2 no codigo da UTFPR)
    private static final String FUTURA = "2099-03-02";

    @TempDir
    Path pasta;

    private ClienteApp app;
    private Stage palco;
    private ServerSocket serverSocket;
    private BancoDados banco;
    // Linhas que o servidor recebeu do cliente, na ordem
    private final List<String> recebidas = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void iniciarJavaFx() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException jaIniciado) {
            // Outro teste ja iniciou o JavaFX
        }
        Platform.setImplicitExit(false);
    }

    @BeforeEach
    void abrirCliente() throws Exception {
        app = new ClienteApp();
        fx(() -> {
            palco = new Stage();
            app.start(palco);
            return null;
        });
    }

    @AfterEach
    void fecharCliente() throws Exception {
        fx(() -> {
            app.stop();
            palco.close();
            return null;
        });
        if (serverSocket != null) {
            serverSocket.close();
        }
    }

    // ------------------------------------------------------------------ cliente -> servidor real

    @Test
    void loginDirecionaCadaPerfilParaSuaInterface() throws Exception {
        iniciarServidorReal();
        banco.cadastrar("joao", "joao@email.com", "senha123");

        logar("joao@email.com", "senha123");
        assertEquals(List.of("Login", "Minha conta"), nomesDasAbas());
        assertEquals("Minha conta", abaSelecionada());
        assertJson(ultima("login"), "op", "login", "email", "joao@email.com", "password", "senha123");
        acionar("logout");
        assertNull(campo("token"));

        logar("admin@admin.com", "admin");
        assertEquals(List.of("Login", "Minha conta", "Administracao", "Salas"), nomesDasAbas());
        assertEquals("Administracao", abaSelecionada());
    }

    @Test
    void itemA_admListaTodosOsUsuarios() throws Exception {
        iniciarServidorReal();
        banco.cadastrar("joao", "joao@email.com", "senha123");
        banco.cadastrar("maria", "maria@email.com", "senha123");
        String token = logar("admin@admin.com", "admin");

        acionar("adminListUsers");
        assertJson(ultima("admin_list_users"), "op", "admin_list_users", "token", token);
        assertEquals(List.of("admin", "joao", "maria"), coluna("tabelaUsuarios", "user"));
        assertEquals(List.of("admin", "user", "user"), coluna("tabelaUsuarios", "role"));
        assertEquals("count: 3 usuario(s)", this.<Label>campo("contagemUsuarios").getText());
    }

    @Test
    void itemB_admAtualizaOutroUsuario() throws Exception {
        iniciarServidorReal();
        banco.cadastrar("joao", "joao@email.com", "senha123");
        String token = logar("admin@admin.com", "admin");
        acionar("adminListUsers");

        // Clicar na linha de joao preenche o usuario alvo
        fx(() -> {
            TableView<JsonObject> tabela = campo("tabelaUsuarios");
            tabela.getSelectionModel().select(1);
            return null;
        });
        assertEquals("joao", this.<TextField>campo("alvo").getText());
        digitar("adminNovoUsuario", "joana");
        digitar("adminNovaSenha", "Nova2026");
        fx(() -> {
            this.<ComboBox<String>>campo("adminNovoPerfil").getSelectionModel().select("admin");
            return null;
        });
        acionar("adminUpdateUser");

        assertJson(ultima("admin_update_user"), "op", "admin_update_user", "token", token, "target_user", "joao",
                "user", "joana", "password", "Nova2026", "role", "admin");
        assertEquals("admin", banco.buscarPorUser("joana").role());
        assertNotNull(banco.autenticar("joao@email.com", "Nova2026"));
        // A tela recarregou a lista e o admin continua sendo ele mesmo
        assertEquals(List.of("admin", "joana"), coluna("tabelaUsuarios", "user"));
        assertEquals("admin", banco.buscarPorUser("admin").role());
    }

    @Test
    void itemC_admExcluiUsuarioComum() throws Exception {
        iniciarServidorReal();
        banco.cadastrar("joao", "joao@email.com", "senha123");
        String token = logar("admin@admin.com", "admin");

        digitar("alvo", "joao");
        acionarComConfirmacao("adminDeleteUser");

        assertJson(ultima("admin_delete_user"), "op", "admin_delete_user", "token", token, "target_user", "joao");
        assertNull(banco.buscarPorUser("joao"));
        assertEquals(List.of("admin"), coluna("tabelaUsuarios", "user"));
    }

    @Test
    void itensDaG_admCadastraLeAtualizaERemoveSala() throws Exception {
        iniciarServidorReal();
        banco.cadastrar("joao", "joao@email.com", "senha123");
        String token = logar("admin@admin.com", "admin");

        // d) cadastra: nome, capacidade, dias da semana e horarios (grade da UTFPR, dias x aulas)
        digitar("novaSalaNome", "Sala B12");
        digitar("novaSalaCapacidade", "12");
        digitar("novaSalaLocal", "Bloco B");
        digitar("novaSalaRecursos", "projetor, tv");
        fx(() -> {
            this.<GradeHorarios>campo("novaSalaHorarios").marcar(List.of("4N5", "2M1"));
            return null;
        });
        acionar("createRoom");
        assertJson(ultima("create_room"), "op", "create_room", "token", token, "name", "Sala B12", "capacity", "12",
                "location", "Bloco B");
        assertEquals("[\"projetor\",\"tv\"]", ultima("create_room").get("resources").toString());
        assertEquals("[\"2M1\",\"4N5\"]", ultima("create_room").get("slots").toString());
        assertEquals("1", this.<TextField>campo("salaAlvo").getText());
        assertEquals(List.of("Sala B12"), coluna("tabelaSalas", "name"));

        // e) le: horarios, capacidade, status e quem reservou
        reservar("joao", 1, FUTURA, "07:30", "08:20");
        acionar("readRoom");
        assertJson(ultima("read_room"), "op", "read_room", "token", token, "room_id", "1");
        assertEquals("12", this.<Label>campo("salaLidaCapacidade").getText());
        assertEquals("active (livre)", this.<Label>campo("salaLidaStatus").getText());
        assertEquals("Segunda: M1 | Quarta: N5", this.<Label>campo("salaLidaHorarios").getText());
        // Com a data, a agenda do dia mostra quem reservou cada horario da sala
        digitar("leituraData", FUTURA);
        acionar("readRoom");
        assertJson(ultima("read_room"), "op", "read_room", "token", token, "room_id", "1", "date", FUTURA);
        assertEquals(List.of("2M1"), coluna("tabelaAgenda", "slot"));
        assertEquals(List.of("indisponivel"), coluna("tabelaAgenda", "slot_status"));
        assertEquals(List.of("joao"), coluna("tabelaAgenda", "user"));
        acionar("listReservations");
        assertJson(ultima("list_reservations"), "op", "list_reservations", "token", token, "scope", "all", "room_id", "1");
        assertEquals(List.of("joao"), coluna("tabelaReservas", "user"));
        assertEquals(List.of(FUTURA), coluna("tabelaReservas", "date"));
        assertEquals(List.of("07:30"), coluna("tabelaReservas", "start_time"));

        // f) atualiza: nome, horarios, capacidade e status (o read_room ja preencheu o formulario)
        digitar("editSalaNome", "Sala B13");
        digitar("editSalaCapacidade", "20");
        fx(() -> {
            this.<ComboBox<String>>campo("editSalaStatus").getSelectionModel().select("inactive");
            assertTrue(this.<CheckBox>campo("editSalaEnviarHorarios").isSelected());
            this.<GradeHorarios>campo("editSalaHorarios").marcar(List.of("7T1"));
            return null;
        });
        acionar("updateRoom");
        assertJson(ultima("update_room"), "op", "update_room", "token", token, "room_id", "1", "name", "Sala B13",
                "capacity", "20", "location", "Bloco B", "room_status", "inactive");
        assertEquals("[\"7T1\"]", ultima("update_room").get("slots").toString());
        BancoDados.Sala sala = banco.buscarSala(1);
        assertEquals("Sala B13", sala.name());
        assertEquals(20, sala.capacity());
        assertEquals("inactive", sala.roomStatus());
        assertEquals(List.of("7T1"), sala.slots());
        assertEquals(List.of("inactive"), coluna("tabelaSalas", "room_status"));
        // O formulario veio preenchido pelo read_room: a lista atual de recursos vai inteira
        assertEquals("[\"projetor\",\"tv\"]", ultima("update_room").get("resources").toString());

        // Regra 9.7 (v2.9): "Apagar todos os recursos" envia ["0"]; recursos em branco vao como [] (manter)
        digitar("editSalaNome", "");
        digitar("editSalaCapacidade", "");
        fx(() -> {
            this.<CheckBox>campo("editSalaEnviarHorarios").setSelected(false);
            this.<ComboBox<String>>campo("editSalaStatus").getSelectionModel().select(0);
            this.<CheckBox>campo("editSalaApagarRecursos").setSelected(true);
            return null;
        });
        acionar("updateRoom");
        assertEquals("[\"0\"]", ultima("update_room").get("resources").toString());
        assertFalse(ultima("update_room").has("slots"));
        assertEquals(List.of(), banco.buscarSala(1).resources());
        assertEquals(List.of("7T1"), banco.buscarSala(1).slots());
        assertEquals("200 - Sala atualizada com sucesso", this.<Label>campo("retornoSalas").getText());
        // Tudo em branco: o servidor responde 400 "Nenhum dado para atualizar" (regra 2.13) e o cliente mostra
        digitar("editSalaLocal", "");
        acionar("updateRoom");
        assertEquals("[]", ultima("update_room").get("resources").toString());
        assertEquals("400 - Nenhum dado para atualizar", this.<Label>campo("retornoSalas").getText());

        // g) remove: com reserva futura o servidor recusa; sem ela, a sala sai dos registros
        acionarComConfirmacao("deleteRoom");
        assertEquals("409 - Sala possui reservas ativas", this.<Label>campo("retornoSalas").getText());
        apagarReservas();
        acionarComConfirmacao("deleteRoom");
        assertJson(ultima("delete_room"), "op", "delete_room", "token", token, "room_id", "1");
        assertNull(banco.buscarSala(1));
        assertTrue(this.<TableView<JsonObject>>campo("tabelaSalas").getItems().isEmpty());
    }

    /**
     * Botao "Cadastrar sala" clicado de verdade, com a aba rolada ate o cartao: o aviso de dados invalidos fica
     * visivel (fora da area rolavel) e o "201" nao e trocado pelo retorno da listagem automatica que vem depois.
     */
    @Test
    void botaoCadastrarSalaMostraORetornoAoUsuario() throws Exception {
        iniciarServidorReal();
        logar("admin@admin.com", "admin");
        selecionarAba("Salas");
        digitar("novaSalaNome", "Sala B12");
        digitar("novaSalaCapacidade", "12");
        digitar("novaSalaLocal", "Bloco B");
        Button cadastrar = fx(() -> botao("Cadastrar sala (create_room)"));
        Label retorno = campo("retornoSalas");
        fx(() -> {
            ScrollPane rolagem = (ScrollPane) palco.getScene().getRoot().lookupAll(".scroll-pane").stream()
                    .filter(n -> n.isVisible() && n.getParent() != null && n.getParent().getChildrenUnmodifiable()
                            .contains(retorno)).findFirst().orElseThrow();
            rolagem.setVvalue(1.0);
            return null;
        });

        // Sem horario marcado: nada e enviado e o aviso aparece fora da area rolavel
        int antes = recebidas.size();
        fx(() -> {
            cadastrar.fire();
            return null;
        });
        esperarRede();
        assertEquals(antes, recebidas.size());
        assertTrue(fx(retorno::isVisible));
        assertTrue(retorno.getText().startsWith("Horarios: marque na grade pelo menos um horario"));
        assertFalse(fx(() -> estaDentroDeScrollPane(retorno)));

        // Marcando uma caixa da grade: create_room sai e o 201 continua na tela depois da listagem
        fx(() -> {
            GradeHorarios grade = campo("novaSalaHorarios");
            grade.getChildren().stream().filter(n -> n instanceof CheckBox).map(n -> (CheckBox) n).findFirst()
                    .orElseThrow().fire();
            return null;
        });
        fx(() -> {
            cadastrar.fire();
            return null;
        });
        esperarRede();
        assertEquals("[\"2M1\"]", ultima("create_room").get("slots").toString());
        ultima("list_rooms");
        assertEquals("201 - Sala cadastrada com sucesso", fx(retorno::getText));
        assertEquals(List.of("Sala B12"), coluna("tabelaSalas", "name"));
    }

    @Test
    void usuarioComumNaoTemInterfaceDeAdminNemPeloJsonManual() throws Exception {
        iniciarServidorReal();
        banco.cadastrar("joao", "joao@email.com", "senha123");
        String token = logar("joao@email.com", "senha123");
        assertFalse(nomesDasAbas().contains("Administracao"));
        assertFalse(nomesDasAbas().contains("Salas"));

        // Mesmo enviando o JSON de admin a mao, com role falso, o servidor recusa e a sessao continua
        digitar("campoJsonManual", "{\"op\":\"admin_list_users\",\"token\":\"" + token + "\",\"role\":\"admin\"}");
        acionar("jsonManual");
        assertEquals(token, campo("token"));
        assertFalse(nomesDasAbas().contains("Administracao"));
    }

    // ------------------------------------------------------------------ servidor -> cliente (servidor falso)

    @Test
    void clienteResisteARespostasEstranhasDoServidor() throws Exception {
        ServidorFalso falso = new ServidorFalso(req -> switch (op(req)) {
            case "login" -> "{\"op\":\"login_response\",\"status\":\"200\",\"message\":\"Login realizado com sucesso\","
                    + "\"token\":\"" + "a".repeat(64) + "\",\"role\":\"admin\"}";
            case "admin_list_users" -> "{\"op\":\"admin_list_users_response\",\"status\":\"200\","
                    + "\"message\":\"Consulta realizada com sucesso\",\"users\":\"nao e uma lista\"}";
            case "list_rooms" -> "isto nao e json";
            case "admin_read_user" -> "{\"op\":\"admin_read_user_response\",\"status\":\"403\",\"message\":\"Permissao insuficiente\"}";
            case "read_room" -> "{\"op\":\"read_room_response\",\"status\":\"200\",\"message\":\"Consulta realizada com sucesso\","
                    + "\"room_id\":\"1\",\"name\":\"Sala\",\"capacity\":\"5\",\"location\":\"B\",\"resources\":[],\"room_status\":\"active\"}";
            default -> "{\"op\":\"" + op(req) + "_response\",\"status\":\"401\",\"message\":\"Token invalido ou expirado\"}";
        });
        usarServidor(falso.porta());
        logar("admin@admin.com", "admin");

        // "users" que nao e lista: a tabela fica vazia e nada quebra
        acionar("adminListUsers");
        assertTrue(this.<TableView<JsonObject>>campo("tabelaUsuarios").getItems().isEmpty());
        // Resposta que nao e JSON: so um aviso, na aba em que o usuario esta
        selecionarAba("Salas");
        acionar("listRooms");
        assertEquals("Resposta do servidor nao e um JSON valido.", this.<Label>campo("retornoSalas").getText());
        // Servidor de colega que nao manda o campo slots
        digitar("salaAlvo", "1");
        acionar("readRoom");
        assertEquals("nao informado pelo servidor", this.<Label>campo("salaLidaHorarios").getText());
        // 403: informa e mantem a sessao
        digitar("alvo", "joao");
        acionar("adminReadUser");
        assertEquals("403 - Permissao insuficiente", this.<Label>campo("retornoAdmin").getText());
        assertNotNull(campo("token"));
        // 401: descarta o token e volta ao login, sem as abas de admin
        acionar("readUser");
        assertNull(campo("token"));
        assertEquals(List.of("Login", "Minha conta"), nomesDasAbas());
        assertEquals("Login", abaSelecionada());
        falso.fechar();
    }

    @Test
    void perfilRebaixadoNoServidorTiraAsAbasDeAdmin() throws Exception {
        ServidorFalso falso = new ServidorFalso(req -> switch (op(req)) {
            case "login" -> "{\"op\":\"login_response\",\"status\":\"200\",\"message\":\"Login realizado com sucesso\","
                    + "\"token\":\"" + "b".repeat(64) + "\",\"role\":\"admin\"}";
            default -> "{\"op\":\"read_user_response\",\"status\":\"200\",\"message\":\"Consulta realizada com sucesso\","
                    + "\"user\":\"ana\",\"email\":\"ana@email.com\",\"role\":\"user\",\"created_at\":\"2026-11-04 10:00:00\"}";
        });
        usarServidor(falso.porta());
        logar("ana@email.com", "senha123");
        assertTrue(nomesDasAbas().contains("Administracao"));

        acionar("readUser");
        assertEquals(List.of("Login", "Minha conta"), nomesDasAbas());
        falso.fechar();
    }

    @Test
    void conexaoQueCaiAntesDaRespostaEReenviada() throws Exception {
        String token = "c".repeat(64);
        ServidorFalso falso = new ServidorFalso(req -> switch (op(req)) {
            case "login" -> "{\"op\":\"login_response\",\"status\":\"200\",\"message\":\"Login realizado com sucesso\","
                    + "\"token\":\"" + token + "\",\"role\":\"admin\"}";
            default -> null;
        });
        usarServidor(falso.porta());
        logar("admin@admin.com", "admin");
        // A partir daqui o servidor falso fecha a primeira conexao sem responder e atende na seguinte
        falso.responder = req -> falso.conexoes() >= 2
                ? "{\"op\":\"admin_list_users_response\",\"status\":\"200\",\"message\":\"Consulta realizada com sucesso\","
                        + "\"count\":\"1\",\"users\":[{\"user\":\"admin\",\"email\":\"admin@admin.com\",\"role\":\"admin\"}]}"
                : null;

        acionar("adminListUsers");
        // Regra 1.11: abriu outra conexao e reenviou a mesma requisicao, com o mesmo token
        List<JsonObject> listas = falso.recebidas.stream().filter(r -> "admin_list_users".equals(op(r))).toList();
        assertEquals(2, listas.size());
        assertEquals(listas.get(0), listas.get(1));
        assertEquals(token, listas.get(1).get("token").getAsString());
        assertEquals(List.of("admin"), coluna("tabelaUsuarios", "user"));
        falso.fechar();
    }

    // ------------------------------------------------------------------ apoio

    private void iniciarServidorReal() throws Exception {
        String arquivo = pasta.resolve("teste.db").toString();
        banco = new BancoDados(arquivo);
        serverSocket = new ServerSocket(0);
        MonitorServidor monitor = new MonitorServidor() {
            @Override
            public void mensagem(TratadorCliente tratador, ConexaoJson.Direcao direcao, String texto) {
                if (direcao == ConexaoJson.Direcao.RECEBIDA) {
                    recebidas.add(texto);
                }
            }
        };
        Thread servidor = new Thread(() -> {
            try {
                Servidor.atender(serverSocket, banco, monitor);
            } catch (IOException fechado) {
                // Fim do teste
            }
        });
        servidor.setDaemon(true);
        servidor.start();
        usarServidor(serverSocket.getLocalPort());
    }

    private void usarServidor(int porta) throws Exception {
        digitar("campoIp", "127.0.0.1");
        digitar("campoPorta", String.valueOf(porta));
    }

    private String logar(String email, String senha) throws Exception {
        digitar("loginEmail", email);
        digitar("loginSenha", senha);
        acionar("login");
        String token = campo("token");
        assertNotNull(token, "login nao concluido");
        return token;
    }

    /** Ultima requisicao com a op informada que o servidor real recebeu. */
    private JsonObject ultima(String op) {
        for (int i = recebidas.size() - 1; i >= 0; i--) {
            JsonObject req = Json.parseObjeto(recebidas.get(i));
            if (op.equals(op(req))) {
                return req;
            }
        }
        throw new AssertionError("O servidor nao recebeu " + op + ": " + recebidas);
    }

    /** Confere que a requisicao tem exatamente estas chaves e valores, com "op" primeiro (regra 2.1). */
    private static void assertJson(JsonObject req, String... chaveValor) {
        List<String> chaves = new ArrayList<>(req.keySet());
        List<String> esperadas = new ArrayList<>();
        for (int i = 0; i < chaveValor.length; i += 2) {
            esperadas.add(chaveValor[i]);
            assertEquals(chaveValor[i + 1], req.get(chaveValor[i]).getAsString(), req.toString());
        }
        chaves.removeAll(List.of("resources", "slots"));
        assertEquals(esperadas, chaves, req.toString());
        assertEquals("op", req.keySet().iterator().next());
    }

    private static String op(JsonObject req) {
        return req.has("op") && req.get("op").isJsonPrimitive() ? req.get("op").getAsString() : "";
    }

    private void reservar(String user, long salaId, String date, String inicio, String fim) throws Exception {
        sql("INSERT INTO reservas (sala_id, usuario_id, date, start_time, end_time, topic, participants) VALUES ("
                + salaId + ", (SELECT id FROM usuarios WHERE user = '" + user + "'), '" + date + "', '" + inicio
                + "', '" + fim + "', 'Reuniao', 3)");
    }

    private void apagarReservas() throws Exception {
        sql("DELETE FROM reservas");
    }

    private void sql(String comando) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + pasta.resolve("teste.db"));
             Statement st = c.createStatement()) {
            st.execute(comando);
        }
    }

    /** Botao da janela do cliente com o texto informado (chamar na thread da interface). */
    private Button botao(String texto) {
        return palco.getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && b.getText().equals(texto)).map(n -> (Button) n)
                .findFirst().orElseThrow();
    }

    private static boolean estaDentroDeScrollPane(javafx.scene.Node no) {
        for (javafx.scene.Parent p = no.getParent(); p != null; p = p.getParent()) {
            if (p instanceof ScrollPane) {
                return true;
            }
        }
        return false;
    }

    private List<String> nomesDasAbas() throws Exception {
        return fx(() -> this.<TabPane>campo("abas").getTabs().stream().map(Tab::getText).toList());
    }

    private void selecionarAba(String nome) throws Exception {
        fx(() -> {
            TabPane abas = campo("abas");
            abas.getTabs().stream().filter(t -> t.getText().equals(nome)).findFirst()
                    .ifPresent(t -> abas.getSelectionModel().select(t));
            return null;
        });
    }

    private String abaSelecionada() throws Exception {
        return fx(() -> this.<TabPane>campo("abas").getSelectionModel().getSelectedItem().getText());
    }

    private List<String> coluna(String tabela, String chave) throws Exception {
        return fx(() -> this.<TableView<JsonObject>>campo(tabela).getItems().stream()
                .map(o -> o.get(chave).getAsString()).toList());
    }

    private void digitar(String campo, String texto) throws Exception {
        fx(() -> {
            this.<TextField>campo(campo).setText(texto);
            return null;
        });
    }

    /** Chama a operacao como o botao faria e espera a rede e a tela terminarem. */
    private void acionar(String metodo) throws Exception {
        fx(() -> {
            invocar(metodo);
            return null;
        });
        esperarRede();
    }

    /** Operacoes destrutivas abrem uma confirmacao: o teste clica em OK. */
    private void acionarComConfirmacao(String metodo) throws Exception {
        Platform.runLater(() -> invocar(metodo));
        esperar("janela de confirmacao", () -> dialogo() != null);
        fx(() -> {
            ((Button) dialogo().lookupButton(ButtonType.OK)).fire();
            return null;
        });
        esperarRede();
    }

    private static DialogPane dialogo() {
        for (Window janela : Window.getWindows()) {
            if (janela.isShowing() && janela.getScene() != null && janela.getScene().getRoot() instanceof DialogPane d) {
                return d;
            }
        }
        return null;
    }

    /** Espera o cliente ficar livre (sem requisicao em andamento) por alguns instantes seguidos. */
    private void esperarRede() throws Exception {
        int livre = 0;
        long limite = System.currentTimeMillis() + 15_000;
        while (livre < 4) {
            assertTrue(System.currentTimeMillis() < limite, "o cliente nao terminou a operacao");
            Thread.sleep(50);
            boolean ocupado = fx(() -> this.<BooleanProperty>campo("ocupado").get());
            livre = ocupado ? 0 : livre + 1;
        }
    }

    private void esperar(String oQue, BooleanSupplier condicao) throws Exception {
        long limite = System.currentTimeMillis() + 10_000;
        while (!fx(condicao::getAsBoolean)) {
            assertTrue(System.currentTimeMillis() < limite, "esperando " + oQue);
            Thread.sleep(50);
        }
    }

    private void invocar(String metodo) {
        try {
            Method m = ClienteApp.class.getDeclaredMethod(metodo);
            m.setAccessible(true);
            m.invoke(app);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T campo(String nome) {
        try {
            Field f = ClienteApp.class.getDeclaredField(nome);
            f.setAccessible(true);
            return (T) f.get(app);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T fx(java.util.concurrent.Callable<T> acao) throws Exception {
        CompletableFuture<T> resultado = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                resultado.complete(acao.call());
            } catch (Throwable t) {
                resultado.completeExceptionally(t);
            }
        });
        return resultado.get(10, TimeUnit.SECONDS);
    }

    /** Servidor TCP falso: responde cada requisicao com a linha que o roteiro devolver (null = fecha a conexao). */
    private static final class ServidorFalso {
        final List<JsonObject> recebidas = new CopyOnWriteArrayList<>();
        volatile Function<JsonObject, String> responder;
        private final ServerSocket socket = new ServerSocket(0);
        private int conexoes;

        ServidorFalso(Function<JsonObject, String> responder) throws IOException {
            this.responder = responder;
            Thread aceitador = new Thread(this::aceitar);
            aceitador.setDaemon(true);
            aceitador.start();
        }

        int porta() {
            return socket.getLocalPort();
        }

        synchronized int conexoes() {
            return conexoes;
        }

        private void aceitar() {
            try {
                while (true) {
                    Socket cliente = socket.accept();
                    synchronized (this) {
                        conexoes++;
                    }
                    Thread t = new Thread(() -> atender(cliente));
                    t.setDaemon(true);
                    t.start();
                }
            } catch (IOException fechado) {
                // Fim do teste
            }
        }

        private void atender(Socket cliente) {
            try (ConexaoJson conexao = new ConexaoJson(cliente, "ServidorFalso")) {
                String linha;
                while ((linha = conexao.receber()) != null) {
                    JsonObject req = Json.parseObjeto(linha);
                    recebidas.add(req);
                    String resposta = responder.apply(req);
                    if (resposta == null) {
                        return;
                    }
                    conexao.enviarTexto(resposta);
                }
            } catch (IOException fim) {
                // Conexao encerrada
            }
        }

        void fechar() throws IOException {
            socket.close();
        }
    }
}
