package br.edu.utfpr.sd.cliente;

import br.edu.utfpr.sd.comum.ConexaoJson;
import br.edu.utfpr.sd.comum.Horarios;
import br.edu.utfpr.sd.comum.Horarios.Horario;
import br.edu.utfpr.sd.comum.Json;
import br.edu.utfpr.sd.comum.ValidacaoCadastro;
import br.edu.utfpr.sd.comum.ValidacaoSala;
import br.edu.utfpr.sd.gui.PainelJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Interface grafica do cliente. Conecta em qualquer servidor da turma (IP e porta informados)
 * e envia as mensagens do CRUD do proprio cadastro, exibindo todos os JSONs trocados.
 * O login e o mesmo para todos; o role devolvido decide a interface: usuario comum ve "Minha conta",
 * administrador ve tambem a aba "Administracao" (operacoes admin_*). O role so monta a tela (protocolo 3.14):
 * quem libera as operacoes e o servidor.
 */
public class ClienteApp extends Application {

    private static final String SEM_CONEXAO = "Desconectado";
    private static final String NAO_ALTERAR = "(nao alterar)";
    private static final String RESPOSTA_GRANDE =
            "Resposta do servidor excede " + ConexaoJson.TAMANHO_MAXIMO + " bytes e foi descartada.";

    // Conexao e sessao; so sao acessadas pela thread de rede
    private ConexaoJson conexao;
    // IP e porta digitados no momento da operacao
    private volatile String[] enderecoAtual;
    private final ExecutorService rede = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Rede-Cliente");
        t.setDaemon(true);
        return t;
    });

    // Protocolo 3.2: o cliente guarda o token em memoria durante a sessao (acessado na thread da interface)
    private String token;
    private String role;
    // Servidor que emitiu o token; conectar em outro servidor descarta a sessao
    private String servidorDoToken;
    // User lido no ultimo read_user; indica quando uma operacao de admin atinge a propria conta
    private String meuUsuario;

    private final BooleanProperty ocupado = new SimpleBooleanProperty(false);
    private final BooleanProperty conectado = new SimpleBooleanProperty(false);
    private final BooleanProperty logado = new SimpleBooleanProperty(false);

    private final PainelJson painelJson = new PainelJson("Mensagens JSON enviadas e recebidas");

    // Barra de conexao
    private final TextField campoIp = new TextField("127.0.0.1");
    private final TextField campoPorta = new TextField();
    private final Label indicadorConexao = new Label(SEM_CONEXAO);
    private final Label indicadorSessao = new Label("Sem sessao");

    // Aba Login
    private final TabPane abas = new TabPane();
    private Tab abaLogin;
    private Tab abaConta;
    private final TextField loginEmail = new TextField();
    private final PasswordField loginSenha = new PasswordField();
    private final TextField cadastroEmail = new TextField();
    private final TextField cadastroUsuario = new TextField();
    private final PasswordField cadastroSenha = new PasswordField();
    private final Label retornoLogin = retorno();

    // Aba Minha conta
    private final Label lidoUsuario = valorLido();
    private final Label lidoEmail = valorLido();
    private final Label lidoPerfil = valorLido();
    private final Label lidoCriadoEm = valorLido();
    private final TextField novoUsuario = new TextField();
    private final PasswordField novaSenha = new PasswordField();
    private final PasswordField senhaExclusao = new PasswordField();
    private final Label retornoConta = retorno();

    // Aba Administracao (so existe no TabPane quando a sessao e de admin)
    private Tab abaAdmin;
    private final TableView<JsonObject> tabelaUsuarios = new TableView<>();
    private final Label contagemUsuarios = new Label("Ainda nao listado.");
    private final TextField alvo = new TextField();
    private final Label adminLidoUsuario = valorLido();
    private final Label adminLidoEmail = valorLido();
    private final Label adminLidoPerfil = valorLido();
    private final Label adminLidoCriadoEm = valorLido();
    private final TextField adminNovoUsuario = new TextField();
    private final PasswordField adminNovaSenha = new PasswordField();
    private final ComboBox<String> adminNovoPerfil =
            new ComboBox<>(FXCollections.observableArrayList(NAO_ALTERAR, "user", "admin"));
    private final Label retornoAdmin = retorno();

    // Aba Salas (so existe no TabPane quando a sessao e de admin)
    private Tab abaSalas;
    private final TableView<JsonObject> tabelaSalas = new TableView<>();
    private final Label contagemSalas = new Label("Ainda nao listado.");
    private final TextField filtroData = new TextField();
    private final TextField filtroInicio = new TextField();
    private final TextField filtroFim = new TextField();
    private final TextField filtroCapacidade = new TextField();
    private final TextField salaAlvo = new TextField();
    private final Label salaLidaId = valorLido();
    private final Label salaLidaNome = valorLido();
    private final Label salaLidaCapacidade = valorLido();
    private final Label salaLidaLocal = valorLido();
    private final Label salaLidaRecursos = valorLido();
    private final Label salaLidaStatus = valorLido();
    private final Label salaLidaHorarios = valorLido();
    private final TextField novaSalaNome = new TextField();
    private final TextField novaSalaCapacidade = new TextField();
    private final TextField novaSalaLocal = new TextField();
    private final TextField novaSalaRecursos = new TextField();
    private final GradeHorarios novaSalaHorarios = new GradeHorarios();
    private final TextField editSalaNome = new TextField();
    private final TextField editSalaCapacidade = new TextField();
    private final TextField editSalaLocal = new TextField();
    private final TextField editSalaRecursos = new TextField();
    private final ComboBox<String> editSalaStatus =
            new ComboBox<>(FXCollections.observableArrayList(NAO_ALTERAR, "active", "inactive"));
    private final CheckBox editSalaEnviarHorarios = new CheckBox("Substituir os horarios (envia schedule)");
    private final GradeHorarios editSalaHorarios = new GradeHorarios();
    private final TextField agendaData = new TextField();
    private final TableView<JsonObject> tabelaReservas = new TableView<>();
    private final Label contagemReservas = new Label("Ainda nao listado.");
    private final Label retornoSalas = retorno();

    private final TextField campoJsonManual = new TextField();

    @Override
    public void start(Stage palco) {
        abaLogin = new Tab("Login", rolavel(conteudoLogin()));
        abaConta = new Tab("Minha conta", rolavel(conteudoConta()));
        abaAdmin = new Tab("Administracao", rolavel(conteudoAdmin()));
        abaSalas = new Tab("Salas", rolavel(conteudoSalas()));
        abas.getTabs().addAll(abaLogin, abaConta);
        abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        abaConta.disableProperty().bind(logado.not());

        SplitPane centro = new SplitPane(abas, areaJson());
        centro.setDividerPositions(0.42);
        centro.setPadding(new Insets(8));

        BorderPane raiz = new BorderPane();
        raiz.setTop(barraConexao());
        raiz.setCenter(centro);

        Scene cena = new Scene(raiz, 1200, 760);
        cena.getStylesheets().add(PainelJson.class.getResource("estilo.css").toExternalForm());
        palco.setTitle("Cliente - Agendamento de Salas");
        palco.setScene(cena);
        palco.setMinWidth(900);
        palco.setMinHeight(560);
        palco.show();
        campoPorta.requestFocus();
    }

    @Override
    public void stop() {
        rede.submit(this::desconectar);
        rede.shutdown();
    }

    // ------------------------------------------------------------------ layout

    private HBox barraConexao() {
        campoIp.setPromptText("IP do servidor");
        campoIp.setPrefColumnCount(12);
        campoPorta.setPromptText("Porta");
        campoPorta.setPrefColumnCount(6);
        campoIp.disableProperty().bind(conectado);
        campoPorta.disableProperty().bind(conectado);

        Button conectar = new Button("Conectar");
        conectar.getStyleClass().add("botao-principal");
        conectar.disableProperty().bind(conectado.or(ocupado));
        conectar.setOnAction(e -> conectar());
        campoPorta.setOnAction(e -> conectar());

        Button desconectar = new Button("Desconectar");
        desconectar.disableProperty().bind(conectado.not().or(ocupado));
        desconectar.setOnAction(e -> executar(() -> {
            desconectar();
            return null;
        }, r -> painelJson.evento("", "Conexao encerrada pelo cliente.")));

        indicadorConexao.getStyleClass().add("indicador");
        indicadorSessao.getStyleClass().add("indicador");

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox barra = new HBox(new Label("IP:"), campoIp, new Label("Porta:"), campoPorta, conectar, desconectar,
                espaco, indicadorConexao, indicadorSessao);
        barra.getStyleClass().add("barra-topo");
        return barra;
    }

    private VBox conteudoLogin() {
        GridPane formLogin = formulario(
                "Email", loginEmail,
                "Senha", loginSenha);
        loginEmail.setPromptText("joao.silva@email.com");
        loginSenha.setOnAction(e -> login());
        Button entrar = botao("Entrar (login)", this::login);
        entrar.getStyleClass().add("botao-principal");
        VBox cartaoLogin = cartao("Entrar", "O servidor identifica o usuario pelo email e senha e devolve um token.",
                formLogin, entrar);

        GridPane formCadastro = formulario(
                "Email", cadastroEmail,
                "Usuario", cadastroUsuario,
                "Senha", cadastroSenha);
        cadastroEmail.setPromptText("joao.silva@email.com");
        cadastroUsuario.setPromptText("joao");
        Label dica = new Label("Usuario: apenas letras minusculas (a-z), 1 a 30.  "
                + "Senha: letras e numeros, 1 a 20.  O cadastro nasce com perfil \"user\" e nao faz login.");
        dica.getStyleClass().add("dica");
        dica.setWrapText(true);
        VBox cartaoCadastro = cartao("Cadastrar novo usuario", "Envia a mensagem register.",
                formCadastro, dica, botao("Cadastrar (register)", this::register));

        VBox conteudo = new VBox(12, cartaoLogin, retornoLogin, cartaoCadastro);
        conteudo.setPadding(new Insets(12));
        return conteudo;
    }

    private VBox conteudoConta() {
        GridPane dados = new GridPane();
        dados.setHgap(12);
        dados.setVgap(6);
        String[] rotulos = {"Usuario", "Email", "Perfil", "Criado em"};
        Label[] valores = {lidoUsuario, lidoEmail, lidoPerfil, lidoCriadoEm};
        for (int i = 0; i < rotulos.length; i++) {
            dados.addRow(i, new Label(rotulos[i] + ":"), valores[i]);
        }
        VBox cartaoLeitura = cartao("Meus dados", "Envia read_user: le o proprio cadastro a partir do token.",
                botao("Pesquisar meus dados (read_user)", this::readUser), dados);

        GridPane formUpdate = formulario(
                "Novo usuario", novoUsuario,
                "Nova senha", novaSenha);
        novoUsuario.setPromptText("em branco = nao alterar");
        novaSenha.setPromptText("em branco = nao alterar");
        Label dicaUpdate = new Label("Campos em branco sao enviados como \"\" e nao sao alterados. "
                + "O email nao pode ser alterado (a chave nao e enviada).");
        dicaUpdate.getStyleClass().add("dica");
        dicaUpdate.setWrapText(true);
        VBox cartaoUpdate = cartao("Atualizar dados", "Envia update_user.",
                formUpdate, dicaUpdate, botao("Atualizar (update_user)", this::updateUser));

        GridPane formDelete = formulario("Senha", senhaExclusao);
        Button excluir = botao("Excluir meu cadastro (delete_user)", this::deleteUser);
        excluir.getStyleClass().add("botao-perigo");
        VBox cartaoDelete = cartao("Excluir cadastro", "Envia delete_user. A senha confirma a operacao.",
                formDelete, excluir);

        VBox cartaoLogout = cartao("Sessao", "Envia logout: o servidor invalida o token.",
                botao("Sair (logout)", this::logout));

        VBox conteudo = new VBox(12, retornoConta, cartaoLeitura, cartaoUpdate, cartaoDelete, cartaoLogout);
        conteudo.setPadding(new Insets(12));
        return conteudo;
    }

    private VBox conteudoAdmin() {
        tabelaUsuarios.setPlaceholder(new Label("Clique em Listar para pedir os usuarios ao servidor."));
        tabelaUsuarios.getColumns().add(colunaJson("user", 120));
        tabelaUsuarios.getColumns().add(colunaJson("email", 220));
        tabelaUsuarios.getColumns().add(colunaJson("role", 70));
        tabelaUsuarios.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabelaUsuarios.setPrefHeight(220);
        // Clicar numa linha escolhe o usuario alvo das operacoes abaixo
        tabelaUsuarios.getSelectionModel().selectedItemProperty().addListener((obs, antes, item) -> {
            if (item != null) {
                alvo.setText(texto(item, "user"));
            }
        });
        contagemUsuarios.getStyleClass().add("contador");
        VBox cartaoLista = cartao("Usuarios cadastrados",
                "Envia admin_list_users: lista todos os usuarios. Clique numa linha para escolher o usuario alvo.",
                botao("Listar usuarios (admin_list_users)", this::adminListUsers), contagemUsuarios, tabelaUsuarios);

        alvo.setPromptText("joao");
        VBox cartaoAlvo = cartao("Usuario alvo",
                "Enviado como target_user nas operacoes abaixo. Quem faz a operacao e identificado pelo token.",
                formulario("Usuario alvo", alvo));

        GridPane dados = new GridPane();
        dados.setHgap(12);
        dados.setVgap(6);
        String[] rotulos = {"Usuario", "Email", "Perfil", "Criado em"};
        Label[] valores = {adminLidoUsuario, adminLidoEmail, adminLidoPerfil, adminLidoCriadoEm};
        for (int i = 0; i < rotulos.length; i++) {
            dados.addRow(i, new Label(rotulos[i] + ":"), valores[i]);
        }
        VBox cartaoLeitura = cartao("Ler usuario", "Envia admin_read_user: le o cadastro do usuario alvo.",
                botao("Ler usuario alvo (admin_read_user)", this::adminReadUser), dados);

        adminNovoUsuario.setPromptText("em branco = nao alterar");
        adminNovaSenha.setPromptText("em branco = nao alterar");
        adminNovoPerfil.getSelectionModel().select(NAO_ALTERAR);
        adminNovoPerfil.setMaxWidth(Double.MAX_VALUE);
        GridPane formUpdate = formulario(
                "Novo usuario", adminNovoUsuario,
                "Nova senha", adminNovaSenha,
                "Perfil", adminNovoPerfil);
        Label dicaUpdate = new Label("Campos em branco e \"" + NAO_ALTERAR + "\" sao enviados como \"\" e nao sao alterados. "
                + "O email nao pode ser alterado (a chave nao e enviada). Mudar o perfil vale na hora, sem novo login.");
        dicaUpdate.getStyleClass().add("dica");
        dicaUpdate.setWrapText(true);
        VBox cartaoUpdate = cartao("Atualizar usuario", "Envia admin_update_user: altera user, senha e/ou perfil do alvo.",
                formUpdate, dicaUpdate, botao("Atualizar usuario alvo (admin_update_user)", this::adminUpdateUser));

        Button excluir = botao("Excluir usuario alvo (admin_delete_user)", this::adminDeleteUser);
        excluir.getStyleClass().add("botao-perigo");
        VBox cartaoDelete = cartao("Excluir usuario",
                "Envia admin_delete_user: apaga o cadastro do alvo e encerra a sessao dele.", excluir);

        VBox conteudo = new VBox(12, retornoAdmin, cartaoLista, cartaoAlvo, cartaoLeitura, cartaoUpdate, cartaoDelete);
        conteudo.setPadding(new Insets(12));
        return conteudo;
    }

    private VBox conteudoSalas() {
        // Listagem (list_rooms) com os filtros opcionais do protocolo
        tabelaSalas.setPlaceholder(new Label("Clique em Listar para pedir as salas ao servidor."));
        tabelaSalas.getColumns().add(colunaJson("room_id", 60));
        tabelaSalas.getColumns().add(colunaJson("name", 130));
        tabelaSalas.getColumns().add(colunaJson("capacity", 70));
        tabelaSalas.getColumns().add(colunaJson("location", 130));
        tabelaSalas.getColumns().add(coluna("resources", 120, s -> String.join(", ", recursos(s))));
        tabelaSalas.getColumns().add(coluna("room_status", 130, s -> statusLegivel(texto(s, "room_status"))));
        tabelaSalas.getColumns().add(colunaJson("available", 70));
        tabelaSalas.getColumns().add(coluna("schedule", 260, s -> Horarios.resumo(GradeHorarios.lerSchedule(s))));
        tabelaSalas.setPrefHeight(220);
        tabelaSalas.getSelectionModel().selectedItemProperty().addListener((obs, antes, sala) -> {
            if (sala != null) {
                salaAlvo.setText(texto(sala, "room_id"));
            }
        });
        filtroData.setPromptText("AAAA-MM-DD");
        filtroInicio.setPromptText("HH:MM");
        filtroFim.setPromptText("HH:MM");
        filtroCapacidade.setPromptText("ex.: 6");
        GridPane filtros = formulario(
                "Data", filtroData,
                "Inicio", filtroInicio,
                "Fim", filtroFim,
                "Cap. minima", filtroCapacidade);
        Label dicaFiltros = new Label("Filtros opcionais (em branco = nao enviados). Data, inicio e fim vao juntos; "
                + "com eles cada sala vem com \"available\".");
        dicaFiltros.getStyleClass().add("dica");
        dicaFiltros.setWrapText(true);
        contagemSalas.getStyleClass().add("contador");
        VBox cartaoLista = cartao("Salas cadastradas",
                "Envia list_rooms. Clique numa linha para escolher a sala alvo.",
                filtros, dicaFiltros, botao("Listar salas (list_rooms)", this::listRooms), contagemSalas, tabelaSalas);

        salaAlvo.setPromptText("room_id, ex.: 1");
        VBox cartaoAlvo = cartao("Sala alvo", "Enviada como room_id nas operacoes de ler, atualizar, excluir e agenda.",
                formulario("room_id", salaAlvo));

        GridPane dados = new GridPane();
        dados.setHgap(12);
        dados.setVgap(6);
        String[] rotulos = {"room_id", "Nome", "Capacidade", "Localizacao", "Recursos", "Status", "Horarios"};
        Label[] valores = {salaLidaId, salaLidaNome, salaLidaCapacidade, salaLidaLocal, salaLidaRecursos,
                salaLidaStatus, salaLidaHorarios};
        for (int i = 0; i < rotulos.length; i++) {
            valores[i].setWrapText(true);
            dados.addRow(i, new Label(rotulos[i] + ":"), valores[i]);
        }
        VBox cartaoLeitura = cartao("Ler sala",
                "Envia read_room. Os dados lidos tambem preenchem o formulario de atualizacao.",
                botao("Ler sala alvo (read_room)", this::readRoom), dados);

        novaSalaNome.setPromptText("Sala de Estudo B12");
        novaSalaCapacidade.setPromptText("12");
        novaSalaLocal.setPromptText("Bloco B - 1 andar");
        novaSalaRecursos.setPromptText("projetor, tv  (separados por virgula; pode ficar vazio)");
        GridPane formCriar = formulario(
                "Nome", novaSalaNome,
                "Capacidade", novaSalaCapacidade,
                "Localizacao", novaSalaLocal,
                "Recursos", novaSalaRecursos);
        Label dicaCriar = new Label("Dias e horarios em que a sala funciona (campo schedule). A sala nasce \"active\" (livre).");
        dicaCriar.getStyleClass().add("dica");
        dicaCriar.setWrapText(true);
        VBox cartaoCriar = cartao("Cadastrar sala", "Envia create_room. O servidor gera o room_id.",
                formCriar, dicaCriar, novaSalaHorarios, botao("Cadastrar sala (create_room)", this::createRoom));

        editSalaNome.setPromptText("em branco = nao alterar");
        editSalaCapacidade.setPromptText("em branco = nao alterar");
        editSalaLocal.setPromptText("em branco = nao alterar");
        editSalaRecursos.setPromptText("lista completa, separada por virgula");
        editSalaStatus.getSelectionModel().select(NAO_ALTERAR);
        editSalaStatus.setMaxWidth(Double.MAX_VALUE);
        GridPane formAtualizar = formulario(
                "Nome", editSalaNome,
                "Capacidade", editSalaCapacidade,
                "Localizacao", editSalaLocal,
                "Recursos", editSalaRecursos,
                "Status", editSalaStatus);
        Label dicaAtualizar = new Label("Campos em branco e \"" + NAO_ALTERAR + "\" vao como \"\" e nao sao alterados. "
                + "Status: active = livre, inactive = indisponivel. Recursos sempre substituem a lista inteira. "
                + "Use \"Ler sala\" antes para trazer os valores atuais.");
        dicaAtualizar.getStyleClass().add("dica");
        dicaAtualizar.setWrapText(true);
        editSalaHorarios.disableProperty().bind(editSalaEnviarHorarios.selectedProperty().not());
        VBox cartaoAtualizar = cartao("Atualizar sala", "Envia update_room para a sala alvo.",
                formAtualizar, dicaAtualizar, editSalaEnviarHorarios, editSalaHorarios,
                botao("Atualizar sala alvo (update_room)", this::updateRoom));

        Button excluir = botao("Excluir sala alvo (delete_room)", this::deleteRoom);
        excluir.getStyleClass().add("botao-perigo");
        VBox cartaoExcluir = cartao("Excluir sala",
                "Envia delete_room. Sala com reservas que ainda nao terminaram nao pode ser removida.", excluir);

        tabelaReservas.setPlaceholder(new Label("Nenhuma reserva listada."));
        for (String campo : new String[] {"reservation_id", "name", "user", "date", "start_time", "end_time", "topic",
                "participants"}) {
            tabelaReservas.getColumns().add(colunaJson(campo, 90));
        }
        tabelaReservas.setPrefHeight(180);
        agendaData.setPromptText("AAAA-MM-DD (opcional)");
        contagemReservas.getStyleClass().add("contador");
        VBox cartaoAgenda = cartao("Agenda (quem reservou)",
                "Envia list_reservations com scope \"all\" e a sala alvo (em branco = todas as salas).",
                formulario("Data", agendaData), botao("Listar reservas (list_reservations)", this::listReservations),
                contagemReservas, tabelaReservas);

        VBox conteudo = new VBox(12, retornoSalas, cartaoLista, cartaoAlvo, cartaoLeitura, cartaoCriar,
                cartaoAtualizar, cartaoExcluir, cartaoAgenda);
        conteudo.setPadding(new Insets(12));
        return conteudo;
    }

    private VBox areaJson() {
        VBox.setVgrow(painelJson, Priority.ALWAYS);
        campoJsonManual.setPromptText("{\"op\": \"...\"}  - enviado exatamente como digitado");
        campoJsonManual.setOnAction(e -> jsonManual());
        HBox.setHgrow(campoJsonManual, Priority.ALWAYS);
        Button enviar = botao("Enviar JSON manual", this::jsonManual);
        HBox manual = new HBox(8, campoJsonManual, enviar);
        manual.setAlignment(Pos.CENTER_LEFT);
        VBox area = new VBox(8, painelJson, manual);
        return area;
    }

    // ------------------------------------------------------------------ operacoes

    private void register() {
        // O servidor grava user e email em minusculas (protocolo 2.12)
        String email = cadastroEmail.getText().toLowerCase(Locale.ROOT);
        String user = cadastroUsuario.getText().toLowerCase(Locale.ROOT);
        String password = cadastroSenha.getText();
        if (!dadosValidos(retornoLogin, ValidacaoCadastro.email(email), ValidacaoCadastro.user(user),
                ValidacaoCadastro.password(password))) {
            return;
        }

        JsonObject req = requisicao("register");
        req.addProperty("email", email);
        req.addProperty("user", user);
        req.addProperty("password", password);
        enviar(req, retornoLogin, resp -> {
            if (sucesso(resp)) {
                // O cadastro nao cria sessao: aproveita o email para o login
                loginEmail.setText(email);
                cadastroEmail.clear();
                cadastroUsuario.clear();
                cadastroSenha.clear();
                loginSenha.requestFocus();
            }
        });
    }

    private void login() {
        JsonObject req = requisicao("login");
        req.addProperty("email", loginEmail.getText());
        req.addProperty("password", loginSenha.getText());
        String servidor = enderecoInformado();
        enviar(req, retornoLogin, resp -> {
            if (sucesso(resp)) {
                token = texto(resp, "token");
                role = texto(resp, "role");
                servidorDoToken = servidor;
                meuUsuario = null;
                loginSenha.clear();
                limparDadosLidos();
                limparDadosAdmin();
                atualizarSessao();
                // Administrador vai para a interface de administracao; usuario comum, para a propria conta
                boolean admin = ehAdmin();
                abas.getSelectionModel().select(admin ? abaAdmin : abaConta);
                mostrar(admin ? retornoAdmin : retornoConta, "info", "Login realizado como " + loginEmail.getText()
                        + (role.isEmpty() ? "" : " (perfil " + role + ")") + ".");
            }
        });
    }

    private void readUser() {
        enviar(requisicaoComToken("read_user"), retornoConta, resp -> {
            if (sucesso(resp)) {
                meuUsuario = texto(resp, "user");
                lidoUsuario.setText(meuUsuario);
                lidoEmail.setText(texto(resp, "email"));
                lidoPerfil.setText(texto(resp, "role"));
                lidoCriadoEm.setText(texto(resp, "created_at"));
                // Um admin pode ter promovido ou rebaixado esta conta durante a sessao: a tela acompanha
                String perfilAtual = texto(resp, "role");
                if (!perfilAtual.isEmpty() && !perfilAtual.equals(role)) {
                    role = perfilAtual;
                    atualizarSessao();
                    mostrar(retornoConta, "info", "Seu perfil agora e " + role + "; a interface foi ajustada.");
                }
            }
        });
    }

    private void updateUser() {
        String user = novoUsuario.getText().toLowerCase(Locale.ROOT);
        String password = novaSenha.getText();
        // Campo vazio significa "nao alterar", entao so os preenchidos sao conferidos
        if (!dadosValidos(retornoConta, user.isEmpty() ? null : ValidacaoCadastro.user(user),
                password.isEmpty() ? null : ValidacaoCadastro.password(password))) {
            return;
        }

        JsonObject req = requisicaoComToken("update_user");
        req.addProperty("user", user);
        req.addProperty("password", password);
        enviar(req, retornoConta, resp -> {
            if (sucesso(resp)) {
                if (!user.isEmpty()) {
                    lidoUsuario.setText(user);
                }
                novoUsuario.clear();
                novaSenha.clear();
            }
        });
    }

    private void deleteUser() {
        Alert confirmacao = new Alert(Alert.AlertType.CONFIRMATION,
                "O cadastro sera removido do servidor e a sessao encerrada.", ButtonType.OK, ButtonType.CANCEL);
        confirmacao.setHeaderText("Excluir o proprio cadastro?");
        if (confirmacao.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        JsonObject req = requisicaoComToken("delete_user");
        req.addProperty("password", senhaExclusao.getText());
        enviar(req, retornoConta, resp -> {
            if (sucesso(resp)) {
                senhaExclusao.clear();
                encerrarSessao("Cadastro removido. Faca um novo cadastro ou entre com outra conta.");
            }
        });
    }

    private void logout() {
        enviar(requisicaoComToken("logout"), retornoConta, resp -> {
            if (sucesso(resp)) {
                encerrarSessao("Logout realizado. Token descartado.");
            }
        });
    }

    private void adminListUsers() {
        enviar(requisicaoComToken("admin_list_users"), retornoAdmin, resp -> {
            if (sucesso(resp)) {
                List<JsonObject> usuarios = new ArrayList<>();
                JsonElement lista = resp.get("users");
                if (lista != null && lista.isJsonArray()) {
                    for (JsonElement item : lista.getAsJsonArray()) {
                        if (item.isJsonObject()) {
                            usuarios.add(item.getAsJsonObject());
                        }
                    }
                }
                tabelaUsuarios.setItems(FXCollections.observableArrayList(usuarios));
                contagemUsuarios.setText("count: " + texto(resp, "count") + " usuario(s)");
            }
        });
    }

    private void adminReadUser() {
        String usuarioAlvo = alvoValido();
        if (usuarioAlvo == null) {
            return;
        }
        JsonObject req = requisicaoComToken("admin_read_user");
        req.addProperty("target_user", usuarioAlvo);
        enviar(req, retornoAdmin, resp -> {
            limparLeituraAdmin();
            if (sucesso(resp)) {
                adminLidoUsuario.setText(texto(resp, "user"));
                adminLidoEmail.setText(texto(resp, "email"));
                adminLidoPerfil.setText(texto(resp, "role"));
                adminLidoCriadoEm.setText(texto(resp, "created_at"));
            }
            recarregarSeNaoEncontrado(resp);
        });
    }

    private void adminUpdateUser() {
        String usuarioAlvo = alvoValido();
        if (usuarioAlvo == null) {
            return;
        }
        String user = adminNovoUsuario.getText().toLowerCase(Locale.ROOT);
        String password = adminNovaSenha.getText();
        String perfil = adminNovoPerfil.getValue();
        String novoRole = perfil == null || NAO_ALTERAR.equals(perfil) ? "" : perfil;
        // Campo vazio significa "nao alterar", entao so os preenchidos sao conferidos
        if (!dadosValidos(retornoAdmin, user.isEmpty() ? null : ValidacaoCadastro.user(user),
                password.isEmpty() ? null : ValidacaoCadastro.password(password))) {
            return;
        }

        JsonObject req = requisicaoComToken("admin_update_user");
        req.addProperty("target_user", usuarioAlvo);
        req.addProperty("user", user);
        req.addProperty("password", password);
        req.addProperty("role", novoRole);
        enviar(req, retornoAdmin, resp -> {
            if (sucesso(resp)) {
                adminNovoUsuario.clear();
                adminNovaSenha.clear();
                adminNovoPerfil.getSelectionModel().select(NAO_ALTERAR);
                if (!user.isEmpty()) {
                    alvo.setText(user);
                }
                limparLeituraAdmin();
                // O admin alterou a propria conta: rebaixar-se tira a aba de administracao
                if (usuarioAlvo.equals(meuUsuario)) {
                    if (!user.isEmpty()) {
                        meuUsuario = user;
                    }
                    if (!novoRole.isEmpty() && !novoRole.equals(role)) {
                        role = novoRole;
                        atualizarSessao();
                    }
                }
                if (ehAdmin()) {
                    adminListUsers();
                }
            }
            recarregarSeNaoEncontrado(resp);
        });
    }

    private void adminDeleteUser() {
        String usuarioAlvo = alvoValido();
        if (usuarioAlvo == null) {
            return;
        }
        Alert confirmacao = new Alert(Alert.AlertType.CONFIRMATION,
                "O cadastro de '" + usuarioAlvo + "' sera removido do servidor e a sessao dele encerrada.",
                ButtonType.OK, ButtonType.CANCEL);
        confirmacao.setHeaderText("Excluir o usuario " + usuarioAlvo + "?");
        if (confirmacao.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        JsonObject req = requisicaoComToken("admin_delete_user");
        req.addProperty("target_user", usuarioAlvo);
        enviar(req, retornoAdmin, resp -> {
            if (sucesso(resp)) {
                if (usuarioAlvo.equals(meuUsuario)) {
                    encerrarSessao("Sua propria conta foi removida. Faca um novo cadastro ou entre com outra conta.");
                    return;
                }
                alvo.clear();
                limparLeituraAdmin();
                adminListUsers();
            }
            recarregarSeNaoEncontrado(resp);
        });
    }

    private void listRooms() {
        String date = filtroData.getText().trim();
        String inicio = filtroInicio.getText().trim();
        String fim = filtroFim.getText().trim();
        String minCapacity = filtroCapacidade.getText().trim();
        int trio = (date.isEmpty() ? 0 : 1) + (inicio.isEmpty() ? 0 : 1) + (fim.isEmpty() ? 0 : 1);
        if (!dadosValidos(retornoSalas,
                trio == 1 || trio == 2 ? "Informe data, inicio e fim juntos, ou deixe os tres em branco." : null,
                date.isEmpty() ? null : ValidacaoSala.date(date),
                inicio.isEmpty() ? null : ValidacaoSala.time("Inicio", inicio),
                fim.isEmpty() ? null : ValidacaoSala.time("Fim", fim),
                minCapacity.isEmpty() ? null : ValidacaoSala.minCapacity(minCapacity))) {
            return;
        }

        JsonObject req = requisicaoComToken("list_rooms");
        adicionarSePreenchido(req, "date", date);
        adicionarSePreenchido(req, "start_time", inicio);
        adicionarSePreenchido(req, "end_time", fim);
        adicionarSePreenchido(req, "min_capacity", minCapacity);
        enviar(req, retornoSalas, resp -> {
            if (sucesso(resp)) {
                tabelaSalas.setItems(FXCollections.observableArrayList(objetos(resp, "rooms")));
                contagemSalas.setText("count: " + texto(resp, "count") + " sala(s)");
            }
        });
    }

    private void readRoom() {
        String roomId = salaAlvoValida();
        if (roomId == null) {
            return;
        }
        JsonObject req = requisicaoComToken("read_room");
        req.addProperty("room_id", roomId);
        enviar(req, retornoSalas, resp -> {
            limparLeituraSala();
            if (sucesso(resp)) {
                List<Horario> horarios = GradeHorarios.lerSchedule(resp);
                salaLidaId.setText(texto(resp, "room_id"));
                salaLidaNome.setText(texto(resp, "name"));
                salaLidaCapacidade.setText(texto(resp, "capacity"));
                salaLidaLocal.setText(texto(resp, "location"));
                salaLidaRecursos.setText(String.join(", ", recursos(resp)));
                salaLidaStatus.setText(statusLegivel(texto(resp, "room_status")));
                // Servidores que nao usam a extensao "schedule" nao mandam o campo
                salaLidaHorarios.setText(resp.has("schedule") ? Horarios.resumo(horarios) : "nao informado pelo servidor");
                // Prepara a atualizacao com os valores atuais
                editSalaNome.setText(texto(resp, "name"));
                editSalaCapacidade.setText(texto(resp, "capacity"));
                editSalaLocal.setText(texto(resp, "location"));
                editSalaRecursos.setText(String.join(", ", recursos(resp)));
                editSalaStatus.getSelectionModel().select(NAO_ALTERAR);
                editSalaHorarios.marcar(horarios);
                editSalaEnviarHorarios.setSelected(resp.has("schedule"));
            }
            recarregarSalasSeNaoEncontrada(resp);
        });
    }

    private void createRoom() {
        String name = novaSalaNome.getText();
        String capacity = novaSalaCapacidade.getText().trim();
        String location = novaSalaLocal.getText();
        if (!dadosValidos(retornoSalas, ValidacaoSala.name(name), ValidacaoSala.capacity(capacity),
                ValidacaoSala.location(location))) {
            return;
        }

        JsonObject req = requisicaoComToken("create_room");
        req.addProperty("name", name);
        req.addProperty("capacity", capacity);
        req.addProperty("location", location);
        req.add("resources", listaRecursos(novaSalaRecursos.getText()));
        req.add("schedule", novaSalaHorarios.comoJson());
        enviar(req, retornoSalas, resp -> {
            if (sucesso(resp)) {
                // Aba "Codigos de Status": no 201 o cliente guarda o id devolvido
                salaAlvo.setText(texto(resp, "room_id"));
                novaSalaNome.clear();
                novaSalaCapacidade.clear();
                novaSalaLocal.clear();
                novaSalaRecursos.clear();
                novaSalaHorarios.limpar();
                listRooms();
            }
        });
    }

    private void updateRoom() {
        String roomId = salaAlvoValida();
        if (roomId == null) {
            return;
        }
        String name = editSalaNome.getText();
        String capacity = editSalaCapacidade.getText().trim();
        String location = editSalaLocal.getText();
        String status = editSalaStatus.getValue();
        String roomStatus = status == null || NAO_ALTERAR.equals(status) ? "" : status;
        // Campo vazio significa "nao alterar", entao so os preenchidos sao conferidos
        if (!dadosValidos(retornoSalas, name.isEmpty() ? null : ValidacaoSala.name(name),
                capacity.isEmpty() ? null : ValidacaoSala.capacity(capacity),
                location.isEmpty() ? null : ValidacaoSala.location(location))) {
            return;
        }

        JsonObject req = requisicaoComToken("update_room");
        req.addProperty("room_id", roomId);
        req.addProperty("name", name);
        req.addProperty("capacity", capacity);
        req.addProperty("location", location);
        req.add("resources", listaRecursos(editSalaRecursos.getText()));
        req.addProperty("room_status", roomStatus);
        // Sem a caixa marcada a chave nao e enviada e o servidor mantem os horarios
        if (editSalaEnviarHorarios.isSelected()) {
            req.add("schedule", editSalaHorarios.comoJson());
        }
        enviar(req, retornoSalas, resp -> {
            if (sucesso(resp)) {
                limparLeituraSala();
                listRooms();
            }
            recarregarSalasSeNaoEncontrada(resp);
        });
    }

    private void deleteRoom() {
        String roomId = salaAlvoValida();
        if (roomId == null) {
            return;
        }
        Alert confirmacao = new Alert(Alert.AlertType.CONFIRMATION,
                "A sala " + roomId + " sera removida dos registros do servidor.", ButtonType.OK, ButtonType.CANCEL);
        confirmacao.setHeaderText("Excluir a sala " + roomId + "?");
        if (confirmacao.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        JsonObject req = requisicaoComToken("delete_room");
        req.addProperty("room_id", roomId);
        enviar(req, retornoSalas, resp -> {
            if (sucesso(resp)) {
                salaAlvo.clear();
                limparLeituraSala();
                listRooms();
            }
            recarregarSalasSeNaoEncontrada(resp);
        });
    }

    private void listReservations() {
        String roomId = salaAlvo.getText().trim();
        String date = agendaData.getText().trim();
        if (!dadosValidos(retornoSalas, roomId.isEmpty() ? null : ValidacaoSala.roomId(roomId),
                date.isEmpty() ? null : ValidacaoSala.date(date))) {
            return;
        }

        JsonObject req = requisicaoComToken("list_reservations");
        req.addProperty("scope", "all");
        adicionarSePreenchido(req, "date", date);
        adicionarSePreenchido(req, "room_id", roomId);
        enviar(req, retornoSalas, resp -> {
            if (sucesso(resp)) {
                tabelaReservas.setItems(FXCollections.observableArrayList(objetos(resp, "reservations")));
                contagemReservas.setText("count: " + texto(resp, "count") + " reserva(s)"
                        + (roomId.isEmpty() ? " em todas as salas" : " na sala " + roomId));
            }
        });
    }

    private void recarregarSalasSeNaoEncontrada(JsonObject resp) {
        if ("404".equals(texto(resp, "status")) && ehAdmin()) {
            listRooms();
        }
    }

    private String salaAlvoValida() {
        String roomId = salaAlvo.getText().trim();
        return dadosValidos(retornoSalas, ValidacaoSala.roomId(roomId)) ? roomId : null;
    }

    /** Recursos digitados separados por virgula, sem itens vazios; texto vazio vira []. */
    private static JsonArray listaRecursos(String texto) {
        JsonArray lista = new JsonArray();
        for (String item : texto.split(",")) {
            if (!item.isBlank()) {
                lista.add(item.trim());
            }
        }
        return lista;
    }

    private static List<String> recursos(JsonObject sala) {
        List<String> recursos = new ArrayList<>();
        JsonElement lista = sala.get("resources");
        if (lista != null && lista.isJsonArray()) {
            for (JsonElement item : lista.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    recursos.add(item.getAsString());
                }
            }
        }
        return recursos;
    }

    /** Itens objeto de um array da resposta (ex.: "rooms", "reservations"). */
    private static List<JsonObject> objetos(JsonObject resp, String chave) {
        List<JsonObject> itens = new ArrayList<>();
        JsonElement lista = resp.get(chave);
        if (lista != null && lista.isJsonArray()) {
            for (JsonElement item : lista.getAsJsonArray()) {
                if (item.isJsonObject()) {
                    itens.add(item.getAsJsonObject());
                }
            }
        }
        return itens;
    }

    /** room_status com o significado pedido na EP-2: active = livre, inactive = indisponivel. */
    private static String statusLegivel(String roomStatus) {
        return switch (roomStatus) {
            case "active" -> "active (livre)";
            case "inactive" -> "inactive (indisponivel)";
            default -> roomStatus;
        };
    }

    private static void adicionarSePreenchido(JsonObject req, String chave, String valor) {
        if (!valor.isEmpty()) {
            req.addProperty(chave, valor);
        }
    }

    /** Aba "Codigos de Status": no 404 o cliente informa e recarrega a listagem. */
    private void recarregarSeNaoEncontrado(JsonObject resp) {
        if ("404".equals(texto(resp, "status")) && ehAdmin()) {
            adminListUsers();
        }
    }

    /** Usuario alvo digitado, em minusculas como o servidor grava (protocolo 2.12); null se invalido. */
    private String alvoValido() {
        String usuarioAlvo = alvo.getText().trim().toLowerCase(Locale.ROOT);
        return dadosValidos(retornoAdmin, ValidacaoCadastro.targetUser(usuarioAlvo)) ? usuarioAlvo : null;
    }

    /** Envia a linha digitada sem alteracoes, para testar como o servidor reage. */
    private void jsonManual() {
        String linha = campoJsonManual.getText();
        if (linha.isBlank()) {
            return;
        }
        executar(() -> trocar(c -> c.enviarTexto(linha)), resp -> {
            if (resp != null) {
                tratarStatusGeral(resp);
            }
        });
    }

    private void conectar() {
        executar(() -> {
            conectarSeNecessario();
            return null;
        }, r -> {
        });
    }

    // ------------------------------------------------------------------ rede

    /** Envia a requisicao na thread de rede e trata a resposta na thread da interface. */
    private void enviar(JsonObject requisicao, Label retorno, Consumer<JsonObject> aoResponder) {
        executar(() -> trocar(c -> c.enviar(requisicao)), resp -> {
            if (resp == null) {
                return;
            }
            mostrarResposta(retorno, resp);
            aoResponder.accept(resp);
            tratarStatusGeral(resp);
        });
    }

    @FunctionalInterface
    private interface TarefaRede {
        JsonObject executar() throws IOException;
    }

    /** Escrita de uma requisicao na conexao; pode ser repetida numa conexao nova. */
    @FunctionalInterface
    private interface EnvioRede {
        void enviar(ConexaoJson conexao) throws IOException;
    }

    /**
     * Envia a requisicao e le a resposta (chamado na thread de rede). Se a conexao que ja estava aberta
     * caiu antes da resposta (ex.: o servidor fechou por 300 s de inatividade), abre outra e reenvia
     * a mesma requisicao, com o mesmo token, uma unica vez (protocolo 1.11).
     */
    private JsonObject trocar(EnvioRede envio) throws IOException {
        if (conexao != null) {
            String linha = null;
            try {
                envio.enviar(conexao);
                linha = conexao.receber();
            } catch (ConexaoJson.MensagemMuitoGrandeException e) {
                avisar(RESPOSTA_GRANDE);
                return null;
            } catch (IOException e) {
                // A conexao antiga caiu; reconecta abaixo
            }
            if (linha != null) {
                return interpretar(linha);
            }
            desconectar();
            evento("A conexao caiu antes da resposta. Reconectando para reenviar a requisicao (protocolo 1.11).");
        }
        conectarSeNecessario();
        envio.enviar(conexao);
        return receber();
    }

    private void executar(TarefaRede tarefa, Consumer<JsonObject> aoTerminar) {
        String ip = campoIp.getText().trim();
        String porta = campoPorta.getText().trim();
        ocupado.set(true);
        rede.submit(() -> {
            JsonObject resultado = null;
            String falha = null;
            try {
                enderecoAtual = new String[] {ip, porta};
                resultado = tarefa.executar();
            } catch (UnknownHostException e) {
                falha = "Servidor " + ip + " nao encontrado.";
                desconectar();
            } catch (NumberFormatException e) {
                falha = "Porta invalida. O valor deve ser um numero.";
            } catch (IOException | IllegalArgumentException e) {
                falha = "Erro de comunicacao com " + ip + ":" + porta + ": " + e.getMessage();
                desconectar();
            }
            JsonObject r = resultado;
            String f = falha;
            Platform.runLater(() -> {
                ocupado.set(false);
                if (f != null) {
                    System.err.println(f);
                    painelJson.evento("", f);
                    mostrar(retornoAtual(), "erro", f);
                }
                aoTerminar.accept(r);
            });
        });
    }

    private void conectarSeNecessario() throws IOException {
        if (conexao != null) {
            return;
        }
        String ip = enderecoAtual[0];
        int porta = Integer.parseInt(enderecoAtual[1]);
        if (ip.isEmpty()) {
            throw new UnknownHostException("IP vazio");
        }
        System.out.println("Conectando com " + ip + ":" + porta + "...");
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(ip, porta), 5000);
        conexao = new ConexaoJson(socket, "Cliente");
        conexao.setOuvinte((direcao, texto) -> Platform.runLater(() -> painelJson.mensagem("", direcao, texto)));
        String endereco = ip + ":" + porta;
        Platform.runLater(() -> {
            conectado.set(true);
            indicadorConexao.setText("Conectado a " + endereco);
            indicadorConexao.getStyleClass().add("ativo");
            painelJson.evento("", "Conectado a " + endereco);
            // Token de outro servidor nao vale aqui
            if (token != null && !endereco.equals(servidorDoToken)) {
                descartarToken();
            }
        });
    }

    /** Le a resposta; devolve null (e avisa) se o servidor fechou a conexao ou mandou algo que nao e JSON. */
    private JsonObject receber() throws IOException {
        String linha;
        try {
            linha = conexao.receber();
        } catch (ConexaoJson.MensagemMuitoGrandeException e) {
            avisar(RESPOSTA_GRANDE);
            return null;
        }
        if (linha == null) {
            desconectar();
            avisar("O servidor encerrou a conexao sem responder. Ela sera reaberta na proxima operacao.");
            return null;
        }
        return interpretar(linha);
    }

    private JsonObject interpretar(String linha) {
        try {
            return Json.parseObjeto(linha);
        } catch (JsonParseException e) {
            avisar("Resposta do servidor nao e um JSON valido.");
            return null;
        }
    }

    /** Registra no console e no painel de mensagens, sem marcar erro no formulario. */
    private void evento(String texto) {
        System.out.println(texto);
        Platform.runLater(() -> painelJson.evento("", texto));
    }

    private void avisar(String texto) {
        System.out.println(texto);
        Platform.runLater(() -> {
            painelJson.evento("", texto);
            mostrar(retornoAtual(), "erro", texto);
        });
    }

    /** Fecha o socket (chamado na thread de rede). O token continua em memoria (protocolo 1.9). */
    private void desconectar() {
        if (conexao != null) {
            try {
                conexao.close();
            } catch (IOException ignored) {
                // A conexao ja esta sendo descartada
            }
            conexao = null;
        }
        Platform.runLater(() -> {
            conectado.set(false);
            indicadorConexao.setText(SEM_CONEXAO);
            indicadorConexao.getStyleClass().remove("ativo");
        });
    }

    // ------------------------------------------------------------------ sessao e status

    /** Reacoes da aba "Codigos de Status" que valem para qualquer operacao. */
    private void tratarStatusGeral(JsonObject resp) {
        String status = texto(resp, "status");
        String op = texto(resp, "op");
        // 401: descarta o token local e volta para a tela de login (no login, apenas credenciais erradas)
        if ("401".equals(status) && token != null && !"login_response".equals(op)) {
            encerrarSessao("Sessao descartada (401). Faca login novamente.");
        }
    }

    private void encerrarSessao(String motivo) {
        descartarToken();
        abas.getSelectionModel().select(abaLogin);
        mostrar(retornoLogin, "info", motivo);
    }

    private void descartarToken() {
        token = null;
        role = null;
        servidorDoToken = null;
        meuUsuario = null;
        limparDadosLidos();
        atualizarSessao();
    }

    private boolean ehAdmin() {
        return token != null && "admin".equals(role);
    }

    /** Ajusta a tela ao estado da sessao; a aba de administracao so existe para sessao de admin. */
    private void atualizarSessao() {
        logado.set(token != null);
        if (ehAdmin()) {
            for (Tab aba : new Tab[] {abaAdmin, abaSalas}) {
                if (!abas.getTabs().contains(aba)) {
                    abas.getTabs().add(aba);
                }
            }
        } else if (abas.getTabs().removeAll(abaAdmin, abaSalas)) {
            limparDadosAdmin();
        }
        indicadorSessao.getStyleClass().remove("ativo");
        if (token == null) {
            indicadorSessao.setText("Sem sessao");
        } else {
            indicadorSessao.setText("Logado" + (role == null || role.isEmpty() ? "" : " (" + role + ")"));
            indicadorSessao.getStyleClass().add("ativo");
        }
    }

    private void limparDadosLidos() {
        for (Label l : new Label[] {lidoUsuario, lidoEmail, lidoPerfil, lidoCriadoEm}) {
            l.setText("-");
        }
    }

    private void limparLeituraAdmin() {
        for (Label l : new Label[] {adminLidoUsuario, adminLidoEmail, adminLidoPerfil, adminLidoCriadoEm}) {
            l.setText("-");
        }
    }

    /** Dados de outros usuarios nao ficam na tela depois que a sessao de admin termina. */
    private void limparDadosAdmin() {
        tabelaUsuarios.getItems().clear();
        contagemUsuarios.setText("Ainda nao listado.");
        alvo.clear();
        adminNovoUsuario.clear();
        adminNovaSenha.clear();
        adminNovoPerfil.getSelectionModel().select(NAO_ALTERAR);
        limparLeituraAdmin();
        // Aba Salas
        tabelaSalas.getItems().clear();
        contagemSalas.setText("Ainda nao listado.");
        tabelaReservas.getItems().clear();
        contagemReservas.setText("Ainda nao listado.");
        for (TextField campo : new TextField[] {filtroData, filtroInicio, filtroFim, filtroCapacidade, salaAlvo,
                novaSalaNome, novaSalaCapacidade, novaSalaLocal, novaSalaRecursos, editSalaNome, editSalaCapacidade,
                editSalaLocal, editSalaRecursos, agendaData}) {
            campo.clear();
        }
        novaSalaHorarios.limpar();
        editSalaHorarios.limpar();
        editSalaEnviarHorarios.setSelected(false);
        editSalaStatus.getSelectionModel().select(NAO_ALTERAR);
        limparLeituraSala();
        for (Label retorno : new Label[] {retornoAdmin, retornoSalas}) {
            retorno.setVisible(false);
            retorno.setManaged(false);
        }
    }

    private void limparLeituraSala() {
        for (Label l : new Label[] {salaLidaId, salaLidaNome, salaLidaCapacidade, salaLidaLocal, salaLidaRecursos,
                salaLidaStatus, salaLidaHorarios}) {
            l.setText("-");
        }
    }

    private String enderecoInformado() {
        return campoIp.getText().trim() + ":" + campoPorta.getText().trim();
    }

    private Label retornoAtual() {
        Tab atual = abas.getSelectionModel().getSelectedItem();
        if (atual == abaConta) {
            return retornoConta;
        }
        if (atual == abaSalas) {
            return retornoSalas;
        }
        return atual == abaAdmin ? retornoAdmin : retornoLogin;
    }

    private void mostrarResposta(Label retorno, JsonObject resp) {
        String status = texto(resp, "status");
        String texto = status + " - " + texto(resp, "message");
        System.out.println(">> " + texto);
        mostrar(retorno, status.startsWith("2") ? "sucesso" : "erro", texto);
    }

    private static void mostrar(Label retorno, String tipo, String texto) {
        retorno.getStyleClass().removeAll("sucesso", "erro", "info");
        retorno.getStyleClass().add(tipo);
        retorno.setText(texto);
        retorno.setVisible(true);
        retorno.setManaged(true);
    }

    /** Mostra os problemas encontrados e devolve false se algum campo estiver fora do formato. */
    private static boolean dadosValidos(Label retorno, String... problemas) {
        StringBuilder texto = new StringBuilder();
        for (String problema : problemas) {
            if (problema != null) {
                texto.append(texto.isEmpty() ? "" : "\n").append(problema);
            }
        }
        if (texto.isEmpty()) {
            return true;
        }
        mostrar(retorno, "erro", texto + "\nNada foi enviado ao servidor.");
        return false;
    }

    private static boolean sucesso(JsonObject resp) {
        return texto(resp, "status").startsWith("2");
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

    /** Valor string de um campo da resposta; tolera servidores que omitam o campo. */
    private static String texto(JsonObject obj, String chave) {
        JsonElement valor = obj.get(chave);
        return valor != null && valor.isJsonPrimitive() ? valor.getAsString() : "";
    }

    // ------------------------------------------------------------------ componentes

    private Button botao(String texto, Runnable acao) {
        Button b = new Button(texto);
        // Sem isso o '_' de "read_user" seria tratado como atalho de teclado e sumiria do rotulo
        b.setMnemonicParsing(false);
        b.setOnAction(e -> acao.run());
        b.disableProperty().bind(ocupado);
        return b;
    }

    private static GridPane formulario(Object... rotuloECampo) {
        GridPane grade = new GridPane();
        grade.setHgap(10);
        grade.setVgap(8);
        ColumnConstraints colRotulo = new ColumnConstraints(90);
        ColumnConstraints colCampo = new ColumnConstraints();
        colCampo.setHgrow(Priority.ALWAYS);
        grade.getColumnConstraints().addAll(colRotulo, colCampo);
        for (int i = 0; i < rotuloECampo.length; i += 2) {
            grade.addRow(i / 2, new Label(rotuloECampo[i] + ":"), (Node) rotuloECampo[i + 1]);
        }
        return grade;
    }

    private static VBox cartao(String titulo, String subtitulo, Node... conteudo) {
        Label t = new Label(titulo);
        t.getStyleClass().add("titulo-secao");
        Label s = new Label(subtitulo);
        s.getStyleClass().add("subtitulo");
        s.setWrapText(true);
        VBox cartao = new VBox(t, s, new Separator());
        cartao.getChildren().addAll(conteudo);
        cartao.getStyleClass().add("cartao");
        return cartao;
    }

    /** Coluna que mostra um campo de cada objeto JSON da lista (ex.: "user" de admin_list_users). */
    private static TableColumn<JsonObject, String> colunaJson(String chave, double largura) {
        return coluna(chave, largura, o -> texto(o, chave));
    }

    /** Coluna com um valor calculado a partir de cada objeto JSON da lista. */
    private static TableColumn<JsonObject, String> coluna(String titulo, double largura,
                                                          Function<JsonObject, String> valor) {
        TableColumn<JsonObject, String> coluna = new TableColumn<>(titulo);
        coluna.setPrefWidth(largura);
        coluna.setCellValueFactory(c -> new SimpleStringProperty(valor.apply(c.getValue())));
        return coluna;
    }

    private static ScrollPane rolavel(Node conteudo) {
        ScrollPane rolagem = new ScrollPane(conteudo);
        rolagem.setFitToWidth(true);
        return rolagem;
    }

    private static Label retorno() {
        Label l = new Label();
        l.getStyleClass().add("retorno");
        l.setMaxWidth(Double.MAX_VALUE);
        l.setVisible(false);
        l.setManaged(false);
        return l;
    }

    private static Label valorLido() {
        Label l = new Label("-");
        l.getStyleClass().add("valor-lido");
        return l;
    }
}
