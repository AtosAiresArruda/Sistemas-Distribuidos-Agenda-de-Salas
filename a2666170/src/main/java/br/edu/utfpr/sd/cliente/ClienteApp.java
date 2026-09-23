package br.edu.utfpr.sd.cliente;

import br.edu.utfpr.sd.comum.ConexaoJson;
import br.edu.utfpr.sd.comum.Json;
import br.edu.utfpr.sd.comum.ValidacaoCadastro;
import br.edu.utfpr.sd.gui.PainelJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
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
 */
public class ClienteApp extends Application {

    private static final String SEM_CONEXAO = "Desconectado";

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

    private final TextField campoJsonManual = new TextField();

    @Override
    public void start(Stage palco) {
        abaLogin = new Tab("Login", rolavel(conteudoLogin()));
        abaConta = new Tab("Minha conta", rolavel(conteudoConta()));
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
                loginSenha.clear();
                atualizarSessao();
                limparDadosLidos();
                abas.getSelectionModel().select(abaConta);
                mostrar(retornoConta, "info", "Login realizado como " + loginEmail.getText()
                        + (role.isEmpty() ? "" : " (perfil " + role + ")") + ".");
            }
        });
    }

    private void readUser() {
        enviar(requisicaoComToken("read_user"), retornoConta, resp -> {
            if (sucesso(resp)) {
                lidoUsuario.setText(texto(resp, "user"));
                lidoEmail.setText(texto(resp, "email"));
                lidoPerfil.setText(texto(resp, "role"));
                lidoCriadoEm.setText(texto(resp, "created_at"));
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

    /** Envia a linha digitada sem alteracoes, para testar como o servidor reage. */
    private void jsonManual() {
        String linha = campoJsonManual.getText();
        if (linha.isBlank()) {
            return;
        }
        executar(() -> {
            conectarSeNecessario();
            conexao.enviarTexto(linha);
            return receber();
        }, resp -> {
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
        executar(() -> {
            conectarSeNecessario();
            conexao.enviar(requisicao);
            return receber();
        }, resp -> {
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
            avisar("Resposta do servidor excede " + ConexaoJson.TAMANHO_MAXIMO + " bytes e foi descartada.");
            return null;
        }
        if (linha == null) {
            desconectar();
            avisar("O servidor encerrou a conexao. Ela sera reaberta na proxima operacao.");
            return null;
        }
        try {
            return Json.parseObjeto(linha);
        } catch (JsonParseException e) {
            avisar("Resposta do servidor nao e um JSON valido.");
            return null;
        }
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
        limparDadosLidos();
        atualizarSessao();
    }

    private void atualizarSessao() {
        logado.set(token != null);
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

    private String enderecoInformado() {
        return campoIp.getText().trim() + ":" + campoPorta.getText().trim();
    }

    private Label retornoAtual() {
        return abas.getSelectionModel().getSelectedItem() == abaConta ? retornoConta : retornoLogin;
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
