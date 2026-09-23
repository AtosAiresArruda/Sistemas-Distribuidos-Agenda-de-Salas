package br.edu.utfpr.sd.gui;

import br.edu.utfpr.sd.comum.ConexaoJson.Direcao;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Area retangular que exibe as mensagens JSON enviadas e recebidas, uma por linha,
 * com horario, origem e sentido. Todos os metodos devem ser chamados na thread da interface.
 */
public class PainelJson extends VBox {

    /** Limite de linhas guardadas, para o painel nao crescer sem fim. */
    private static final int MAXIMO_LINHAS = 5000;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Uma linha do painel.
     *
     * @param origem  quem trocou a mensagem (ex.: nome da thread); pode ser vazio
     * @param direcao sentido da mensagem, ou null para eventos (conexao aberta, encerrada...)
     */
    public record Linha(String hora, String origem, Direcao direcao, String texto) {

        public static Linha mensagem(String origem, Direcao direcao, String texto) {
            return new Linha(LocalTime.now().format(HORA), origem, direcao, texto);
        }

        public static Linha evento(String origem, String texto) {
            return new Linha(LocalTime.now().format(HORA), origem, null, texto);
        }

        String seta() {
            if (direcao == null) {
                return "  ·  ";
            }
            return direcao == Direcao.ENVIADA ? "ENVIOU" : "RECEBEU";
        }

        String formatada() {
            String prefixo = origem.isEmpty() ? "" : "[" + origem + "] ";
            return hora + "  " + prefixo + seta() + "  " + texto;
        }
    }

    private final ListView<Linha> lista = new ListView<>();
    private final Label titulo = new Label();
    private final Label contador = new Label();
    private ObservableList<Linha> linhas = FXCollections.observableArrayList();
    private final ListChangeListener<Linha> rolarParaFim = mudanca -> rolarParaFim();

    public PainelJson(String titulo) {
        getStyleClass().add("painel-json");
        this.titulo.setText(titulo);
        this.titulo.getStyleClass().add("titulo-secao");
        contador.getStyleClass().add("contador");

        Button limpar = new Button("Limpar");
        limpar.setOnAction(e -> linhas.clear());
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox cabecalho = new HBox(8, this.titulo, espaco, contador, limpar);
        cabecalho.setAlignment(Pos.CENTER_LEFT);

        lista.getStyleClass().add("lista-json");
        lista.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        lista.setCellFactory(v -> new CelulaJson());
        lista.setPlaceholder(new Label("Nenhuma mensagem trocada ainda."));
        VBox.setVgrow(lista, Priority.ALWAYS);

        MenuItem copiar = new MenuItem("Copiar JSON selecionado");
        copiar.setOnAction(e -> copiarSelecionadas(false));
        MenuItem copiarLinha = new MenuItem("Copiar linha completa");
        copiarLinha.setOnAction(e -> copiarSelecionadas(true));
        lista.setContextMenu(new ContextMenu(copiar, copiarLinha));
        KeyCombination ctrlC = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
        lista.setOnKeyPressed(e -> {
            if (ctrlC.match(e)) {
                copiarSelecionadas(false);
            }
        });

        setSpacing(6);
        setPadding(new Insets(8));
        getChildren().addAll(cabecalho, lista);
        usarLinhas(linhas);
    }

    public void setTitulo(String texto) {
        titulo.setText(texto);
    }

    public void mensagem(String origem, Direcao direcao, String texto) {
        adicionar(Linha.mensagem(origem, direcao, texto));
    }

    public void evento(String origem, String texto) {
        adicionar(Linha.evento(origem, texto));
    }

    public void adicionar(Linha linha) {
        adicionar(linhas, linha);
    }

    /** Acrescenta a linha em uma lista qualquer, respeitando o limite de linhas. */
    public static void adicionar(ObservableList<Linha> destino, Linha linha) {
        destino.add(linha);
        if (destino.size() > MAXIMO_LINHAS) {
            destino.remove(0, destino.size() - MAXIMO_LINHAS);
        }
    }

    /** Passa a exibir outra lista (usado para trocar a thread observada no servidor). */
    public void usarLinhas(ObservableList<Linha> novas) {
        linhas.removeListener(rolarParaFim);
        contador.textProperty().unbind();
        linhas = novas;
        lista.setItems(novas);
        novas.addListener(rolarParaFim);
        contador.textProperty().bind(Bindings.size(novas).asString("%d linhas"));
        rolarParaFim();
    }

    private void rolarParaFim() {
        if (!linhas.isEmpty()) {
            lista.scrollTo(linhas.size() - 1);
        }
    }

    private void copiarSelecionadas(boolean linhaCompleta) {
        StringBuilder texto = new StringBuilder();
        for (Linha l : lista.getSelectionModel().getSelectedItems()) {
            texto.append(linhaCompleta ? l.formatada() : l.texto()).append('\n');
        }
        if (!texto.isEmpty()) {
            ClipboardContent conteudo = new ClipboardContent();
            conteudo.putString(texto.toString().stripTrailing());
            Clipboard.getSystemClipboard().setContent(conteudo);
        }
    }

    /** Celula com quebra de linha e cor conforme o sentido da mensagem. */
    private final class CelulaJson extends ListCell<Linha> {

        CelulaJson() {
            setWrapText(true);
            prefWidthProperty().bind(lista.widthProperty().subtract(24));
            setMaxWidth(Region.USE_PREF_SIZE);
        }

        @Override
        protected void updateItem(Linha linha, boolean vazia) {
            super.updateItem(linha, vazia);
            getStyleClass().removeAll("json-enviada", "json-recebida", "json-evento");
            if (vazia || linha == null) {
                setText(null);
                return;
            }
            setText(linha.formatada());
            if (linha.direcao() == null) {
                getStyleClass().add("json-evento");
            } else {
                getStyleClass().add(linha.direcao() == Direcao.ENVIADA ? "json-enviada" : "json-recebida");
            }
        }
    }
}
