package br.edu.utfpr.sd.servidor;

import br.edu.utfpr.sd.comum.ConexaoJson.Direcao;
import br.edu.utfpr.sd.comum.Json;
import br.edu.utfpr.sd.gui.PainelJson;
import br.edu.utfpr.sd.gui.PainelJson.Linha;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Interface grafica do servidor: pede a porta, inicia o servidor e monitora as threads.
 * A aba principal mostra as threads ativas, a conversa de cada uma e a comunicacao de todas.
 */
public class ServidorApp extends Application {

    private static final String ARQUIVO_BANCO = "agenda.db";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Estado de uma thread de atendimento, como exibido na tabela. */
    public static final class InfoThread {
        final TratadorCliente tratador;
        final String nome;
        final String remoto;
        final String inicio = LocalTime.now().format(HORA);
        final StringProperty estado = new SimpleStringProperty("Ativa");
        final StringProperty usuario = new SimpleStringProperty("-");
        final StringProperty ultimaOp = new SimpleStringProperty("-");
        final StringProperty ultimoStatus = new SimpleStringProperty("-");
        final IntegerProperty mensagens = new SimpleIntegerProperty();
        final ObservableList<Linha> conversa = FXCollections.observableArrayList();
        boolean ativa = true;
        // Email enviado no ultimo login, confirmado quando a resposta vier com 200
        String emailPendente;

        InfoThread(TratadorCliente tratador) {
            this.tratador = tratador;
            this.nome = tratador.getName();
            this.remoto = tratador.getRemoto();
        }
    }

    private final ObservableList<InfoThread> threads = FXCollections.observableArrayList();
    private final IntegerProperty threadsAtivas = new SimpleIntegerProperty();
    private final IntegerProperty totalConexoes = new SimpleIntegerProperty();
    private final IntegerProperty totalMensagens = new SimpleIntegerProperty();

    private final PainelJson painelGeral = new PainelJson("Comunicacao de todas as threads");
    private final PainelJson painelThread = new PainelJson("Conversa da thread selecionada");
    private final TableView<InfoThread> tabela = new TableView<>(threads);

    private final TextField campoPorta = new TextField();
    private final Button botaoIniciar = new Button("Iniciar servidor");
    private final Button botaoParar = new Button("Parar");
    private final Label indicador = new Label("Parado");

    private BancoDados banco;
    private ServerSocket serverSocket;

    @Override
    public void start(Stage palco) {
        BorderPane raiz = new BorderPane();
        raiz.setTop(barraTopo());
        raiz.setCenter(areaMonitor());

        Scene cena = new Scene(raiz, 1200, 780);
        cena.getStylesheets().add(PainelJson.class.getResource("estilo.css").toExternalForm());
        palco.setTitle("Servidor - Agendamento de Salas");
        palco.setScene(cena);
        palco.setMinWidth(900);
        palco.setMinHeight(600);
        palco.show();
        campoPorta.requestFocus();
    }

    @Override
    public void stop() {
        pararServidor();
    }

    // ------------------------------------------------------------------ layout

    private HBox barraTopo() {
        Label rotulo = new Label("Porta:");
        campoPorta.setPromptText("ex.: 23456");
        campoPorta.setPrefColumnCount(7);
        campoPorta.setOnAction(e -> iniciarServidor());

        botaoIniciar.getStyleClass().add("botao-principal");
        botaoIniciar.setOnAction(e -> iniciarServidor());
        botaoParar.setOnAction(e -> pararServidor());
        botaoParar.setDisable(true);
        indicador.getStyleClass().add("indicador");

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        HBox barra = new HBox(rotulo, campoPorta, botaoIniciar, botaoParar, indicador, espaco,
                metrica(threadsAtivas, "threads ativas"),
                metrica(totalConexoes, "conexoes desde o inicio"),
                metrica(totalMensagens, "mensagens JSON"));
        barra.getStyleClass().add("barra-topo");
        barra.setSpacing(10);
        return barra;
    }

    private VBox metrica(IntegerProperty valor, String rotulo) {
        Label numero = new Label();
        numero.textProperty().bind(valor.asString());
        numero.getStyleClass().add("metrica");
        Label legenda = new Label(rotulo);
        legenda.getStyleClass().add("metrica-rotulo");
        VBox caixa = new VBox(numero, legenda);
        caixa.setAlignment(Pos.CENTER);
        caixa.setPadding(new Insets(0, 10, 0, 10));
        return caixa;
    }

    private SplitPane areaMonitor() {
        SplitPane superior = new SplitPane(painelThreads(), painelThread);
        superior.setDividerPositions(0.52);

        SplitPane area = new SplitPane(superior, painelGeral);
        area.setOrientation(Orientation.VERTICAL);
        area.setDividerPositions(0.55);
        area.setPadding(new Insets(8));
        return area;
    }

    private VBox painelThreads() {
        Label titulo = new Label("Threads de atendimento");
        titulo.getStyleClass().add("titulo-secao");
        Label resumo = new Label();
        resumo.getStyleClass().add("contador");
        resumo.textProperty().bind(Bindings.createStringBinding(
                () -> threadsAtivas.get() + " ativa(s) de " + threads.size() + " listada(s)", threadsAtivas, threads));

        tabela.setPlaceholder(new Label("Nenhum cliente conectado."));
        tabela.getColumns().add(coluna("Thread", 75, i -> new SimpleStringProperty(i.nome)));
        tabela.getColumns().add(coluna("Cliente (ip:porta)", 140, i -> new SimpleStringProperty(i.remoto)));
        tabela.getColumns().add(coluna("Estado", 80, i -> i.estado));
        tabela.getColumns().add(coluna("Usuario logado", 170, i -> i.usuario));
        tabela.getColumns().add(coluna("Ultima op", 110, i -> i.ultimaOp));
        tabela.getColumns().add(coluna("Status", 60, i -> i.ultimoStatus));
        tabela.getColumns().add(coluna("Msgs", 50, i -> i.mensagens.asString()));
        tabela.getColumns().add(coluna("Desde", 70, i -> new SimpleStringProperty(i.inicio)));
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabela.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(InfoThread info, boolean vazia) {
                super.updateItem(info, vazia);
                getStyleClass().remove("encerrada");
                if (!vazia && info != null && !info.ativa) {
                    getStyleClass().add("encerrada");
                }
            }
        });
        tabela.getSelectionModel().selectedItemProperty().addListener((obs, antes, info) -> mostrarThread(info));
        VBox.setVgrow(tabela, Priority.ALWAYS);

        Button desconectar = new Button("Desconectar cliente");
        desconectar.disableProperty().bind(tabela.getSelectionModel().selectedItemProperty().isNull());
        desconectar.setOnAction(e -> {
            InfoThread info = tabela.getSelectionModel().getSelectedItem();
            if (info != null && info.ativa) {
                info.tratador.encerrar();
            }
        });
        Button removerEncerradas = new Button("Remover encerradas");
        removerEncerradas.setOnAction(e -> threads.removeIf(i -> !i.ativa));

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox cabecalho = new HBox(8, titulo, espaco, resumo);
        cabecalho.setAlignment(Pos.CENTER_LEFT);
        HBox acoes = new HBox(8, desconectar, removerEncerradas);

        VBox painel = new VBox(6, cabecalho, tabela, acoes);
        painel.getStyleClass().add("painel-json");
        painel.setPadding(new Insets(8));
        return painel;
    }

    private static TableColumn<InfoThread, String> coluna(String titulo, double largura,
                                                          Function<InfoThread, ObservableValue<String>> valor) {
        TableColumn<InfoThread, String> coluna = new TableColumn<>(titulo);
        coluna.setPrefWidth(largura);
        coluna.setCellValueFactory(c -> valor.apply(c.getValue()));
        return coluna;
    }

    private void mostrarThread(InfoThread info) {
        if (info == null) {
            painelThread.setTitulo("Conversa da thread selecionada");
            painelThread.usarLinhas(FXCollections.observableArrayList());
        } else {
            painelThread.setTitulo("Conversa de " + info.nome + " (" + info.remoto + ")");
            painelThread.usarLinhas(info.conversa);
        }
    }

    // ------------------------------------------------------------------ servidor

    private void iniciarServidor() {
        int porta;
        try {
            porta = Integer.parseInt(campoPorta.getText().trim());
            if (porta < 1 || porta > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            erro("Porta invalida", "Informe um numero entre 1 e 65535.");
            return;
        }

        try {
            if (banco == null) {
                banco = new BancoDados(ARQUIVO_BANCO);
            }
            serverSocket = new ServerSocket(porta);
        } catch (SQLException e) {
            erro("Banco de dados", "Nao foi possivel abrir o banco " + ARQUIVO_BANCO + ": " + e.getMessage());
            return;
        } catch (BindException e) {
            erro("Porta ocupada", "A porta " + porta + " esta ocupada. Escolha outra porta.");
            return;
        } catch (IOException e) {
            erro("Erro ao abrir a porta", e.getMessage());
            return;
        }

        System.out.println("Servidor carregado na porta " + porta + ". Aguardando conexoes...\n");
        painelGeral.evento("Servidor", "Servidor carregado na porta " + porta + ". Aguardando conexoes...");
        ServerSocket socketAtual = serverSocket;
        Thread aceitador = new Thread(() -> {
            try {
                Servidor.atender(socketAtual, banco, new MonitorGui());
            } catch (IOException e) {
                // accept() interrompido pelo botao Parar
            }
        }, "Aceitador");
        aceitador.setDaemon(true);
        aceitador.start();

        campoPorta.setDisable(true);
        botaoIniciar.setDisable(true);
        botaoParar.setDisable(false);
        indicador.setText("Escutando na porta " + porta);
        indicador.getStyleClass().add("ativo");
    }

    private void pararServidor() {
        if (serverSocket == null) {
            return;
        }
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // Ja fechado
        }
        serverSocket = null;
        for (InfoThread info : threads) {
            if (info.ativa) {
                info.tratador.encerrar();
            }
        }
        System.out.println("Servidor parado.");
        painelGeral.evento("Servidor", "Servidor parado.");

        campoPorta.setDisable(false);
        botaoIniciar.setDisable(false);
        botaoParar.setDisable(true);
        indicador.setText("Parado");
        indicador.getStyleClass().remove("ativo");
    }

    private void erro(String titulo, String texto) {
        Alert alerta = new Alert(Alert.AlertType.ERROR, texto);
        alerta.setHeaderText(titulo);
        alerta.showAndWait();
    }

    // ------------------------------------------------------------------ monitor

    /** Recebe os eventos das threads de atendimento e os repassa para a thread da interface. */
    private final class MonitorGui implements MonitorServidor {

        @Override
        public void conexaoAberta(TratadorCliente tratador) {
            Platform.runLater(() -> {
                InfoThread info = new InfoThread(tratador);
                threads.add(info);
                threadsAtivas.set(threadsAtivas.get() + 1);
                totalConexoes.set(totalConexoes.get() + 1);
                registrar(info, Linha.evento(info.nome, "Nova thread de comunicacao iniciada com cliente " + info.remoto));
                if (tabela.getSelectionModel().isEmpty()) {
                    tabela.getSelectionModel().select(info);
                }
            });
        }

        @Override
        public void mensagem(TratadorCliente tratador, Direcao direcao, String texto) {
            Platform.runLater(() -> {
                InfoThread info = buscar(tratador);
                if (info == null) {
                    return;
                }
                info.mensagens.set(info.mensagens.get() + 1);
                totalMensagens.set(totalMensagens.get() + 1);
                atualizarResumo(info, direcao, texto);
                registrar(info, Linha.mensagem(info.nome, direcao, texto));
            });
        }

        @Override
        public void conexaoEncerrada(TratadorCliente tratador, String motivo) {
            Platform.runLater(() -> {
                InfoThread info = buscar(tratador);
                if (info == null) {
                    return;
                }
                info.ativa = false;
                info.estado.set("Encerrada");
                threadsAtivas.set(threadsAtivas.get() - 1);
                registrar(info, Linha.evento(info.nome, "Conexao encerrada com " + info.remoto + " (" + motivo + ")"));
                tabela.refresh();
            });
        }

        private InfoThread buscar(TratadorCliente tratador) {
            for (InfoThread info : threads) {
                if (info.tratador == tratador) {
                    return info;
                }
            }
            return null;
        }

        /** Guarda a linha na conversa da thread e no painel geral. */
        private void registrar(InfoThread info, Linha linha) {
            PainelJson.adicionar(info.conversa, linha);
            painelGeral.adicionar(linha);
        }

        /** Atualiza as colunas "Ultima op", "Status" e "Usuario logado" a partir do JSON trafegado. */
        private void atualizarResumo(InfoThread info, Direcao direcao, String texto) {
            JsonObject json;
            try {
                json = Json.parseObjeto(texto);
            } catch (JsonParseException e) {
                if (direcao == Direcao.RECEBIDA) {
                    info.ultimaOp.set("(invalida)");
                }
                return;
            }
            String op = texto(json, "op");
            if (direcao == Direcao.RECEBIDA) {
                info.ultimaOp.set(op.isEmpty() ? "(sem op)" : op);
                info.ultimoStatus.set("...");
                if ("login".equals(op)) {
                    info.emailPendente = texto(json, "email");
                }
                return;
            }
            String status = texto(json, "status");
            info.ultimoStatus.set(status);
            boolean ok = "200".equals(status);
            switch (op) {
                case "login_response" -> {
                    if (ok) {
                        info.usuario.set(info.emailPendente + " (" + texto(json, "role") + ")");
                    }
                }
                case "logout_response", "delete_user_response" -> {
                    if (ok) {
                        info.usuario.set("-");
                    }
                }
                default -> {
                    if ("401".equals(status)) {
                        info.usuario.set("-");
                    }
                }
            }
        }

        private static String texto(JsonObject obj, String chave) {
            JsonElement valor = obj.get(chave);
            return valor != null && valor.isJsonPrimitive() ? valor.getAsString() : "";
        }
    }
}
