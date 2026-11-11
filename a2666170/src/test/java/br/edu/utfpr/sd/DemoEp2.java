package br.edu.utfpr.sd;

import br.edu.utfpr.sd.cliente.ClienteApp;
import br.edu.utfpr.sd.cliente.GradeHorarios;
import br.edu.utfpr.sd.comum.ConexaoJson;
import br.edu.utfpr.sd.gui.PainelJson;
import br.edu.utfpr.sd.servidor.ServidorApp;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javax.imageio.ImageIO;

/**
 * Demonstracao visual da EP-2: abre as janelas reais do Servidor e do Cliente lado a lado e executa, com pausas,
 * o fluxo principal (itens a-o) e o fluxo secundario (erros do cliente). Cada passo confere o retorno mostrado
 * na tela e grava uma captura das duas janelas.
 *
 * <p>Uso (na pasta a2666170, depois de {@code mvn test-compile}): o banco "agenda.db" e criado na pasta de trabalho,
 * entao rode a partir de uma pasta vazia. Argumentos: [porta] [pasta das capturas] [pausa em ms].
 */
public final class DemoEp2 {

    private static final String DIA = "2099-03-02"; // segunda-feira (dia 2 no codigo da UTFPR)

    private final int porta;
    private final Path capturas;
    private final long pausa;
    private ServidorApp servidor;
    private ClienteApp cliente;
    private Stage palcoServidor;
    private Stage palcoCliente;
    private int passo;
    private final List<String> falhas = new ArrayList<>();

    private DemoEp2(int porta, Path capturas, long pausa) {
        this.porta = porta;
        this.capturas = capturas;
        this.pausa = pausa;
    }

    public static void main(String[] args) throws Exception {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 23456;
        Path capturas = Path.of(args.length > 1 ? args[1] : "capturas");
        long pausa = args.length > 2 ? Long.parseLong(args[2]) : 1500;
        Files.createDirectories(capturas);
        Platform.startup(() -> {
        });
        Platform.setImplicitExit(false);
        DemoEp2 demo = new DemoEp2(porta, capturas, pausa);
        try {
            demo.executar();
        } catch (Throwable t) {
            t.printStackTrace();
            demo.falhas.add("Excecao: " + t);
        }
        System.out.println();
        System.out.println(demo.falhas.isEmpty() ? "DEMO: todos os passos conferidos na tela."
                : "DEMO: " + demo.falhas.size() + " passo(s) com retorno diferente do esperado:");
        demo.falhas.forEach(f -> System.out.println("  - " + f));
        // As janelas ficam abertas para o usuario olhar; o processo encerra quando as duas forem fechadas
        Platform.setImplicitExit(true);
        long limite = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10);
        while (System.currentTimeMillis() < limite && demo.algumaJanelaAberta()) {
            Thread.sleep(500);
        }
        System.exit(demo.falhas.isEmpty() ? 0 : 1);
    }

    private boolean algumaJanelaAberta() throws Exception {
        return fx(() -> palcoServidor.isShowing() || palcoCliente.isShowing());
    }

    private void executar() throws Exception {
        abrirJanelas();

        // ---------------------------------------------------------------- servidor
        titulo("Servidor: informa a porta e inicia");
        fx(() -> {
            ((TextInputControl) campo(servidor, "campoPorta")).setText(String.valueOf(porta));
            invocar(servidor, "iniciarServidor");
            return null;
        });
        captura("servidor-iniciado");

        // ---------------------------------------------------------------- preparacao (EP-1)
        titulo("Cliente: informa IP e porta e cadastra dois usuarios comuns");
        texto("campoIp", "127.0.0.1");
        texto("campoPorta", String.valueOf(porta));
        cadastrar("joao", "joao@email.com", "senha123");
        cadastrar("maria", "maria@email.com", "senha123");

        // ---------------------------------------------------------------- fluxo principal
        titulo("Login do admin padrao (admin@admin.com / admin)");
        texto("loginEmail", "admin@admin.com");
        texto("loginSenha", "admin");
        acionar("login", "retornoAdmin", "info");
        captura("login-admin");

        titulo("a/i) admin_list_users: lista TODOS os usuarios");
        acionar("adminListUsers", "retornoAdmin", "200 - Consulta realizada com sucesso");
        captura("a-lista-usuarios");

        titulo("b/j) admin_read_user + admin_update_user: altera outro usuario (joao -> joana, nova senha)");
        texto("alvo", "joao");
        acionar("adminReadUser", "retornoAdmin", "200 - Consulta realizada com sucesso");
        texto("adminNovoUsuario", "joana");
        texto("adminNovaSenha", "nova123");
        acionar("adminUpdateUser", "retornoAdmin", "200 - Dados atualizados com sucesso");
        captura("b-atualiza-usuario");

        titulo("c/k) admin_delete_user: exclui o usuario comum maria");
        texto("alvo", "maria");
        acionarComConfirmacao("adminDeleteUser", "retornoAdmin", "200 - Usuario removido com sucesso");
        captura("c-exclui-usuario");

        titulo("d/l) create_room: nome, capacidade, local, recursos e horarios (grade UTFPR)");
        aba("abaSalas");
        texto("novaSalaNome", "Sala B12");
        texto("novaSalaCapacidade", "12");
        texto("novaSalaLocal", "Bloco B - 1 andar");
        texto("novaSalaRecursos", "projetor, tv");
        fx(() -> {
            ((GradeHorarios) campo(cliente, "novaSalaHorarios")).marcar(List.of("2M1", "2M2", "2M3", "4T3"));
            return null;
        });
        pausar();
        acionar("createRoom", "retornoSalas", "201 - Sala cadastrada com sucesso");
        captura("d-cadastra-sala");

        titulo("e/m) list_rooms, read_room com agenda do dia (quem reservou) e list_reservations scope all");
        reservarDireto("joana", 1, DIA, "07:30", "09:10", 6);
        acionar("listRooms", "retornoSalas", "200 - Consulta realizada com sucesso");
        texto("salaAlvo", "1");
        texto("leituraData", DIA);
        acionar("readRoom", "retornoSalas", "200 - Consulta realizada com sucesso");
        captura("e-le-sala-agenda");
        texto("agendaData", "");
        acionar("listReservations", "retornoSalas", "200 - Consulta realizada com sucesso");
        captura("e-lista-reservas");

        titulo("n) update_room: nome, capacidade, status inactive e novos horarios");
        texto("editSalaNome", "Sala B13");
        texto("editSalaCapacidade", "20");
        fx(() -> {
            ((ComboBox<?>) campo(cliente, "editSalaStatus")).getSelectionModel().select(2); // inactive
            ((CheckBox) campo(cliente, "editSalaEnviarHorarios")).setSelected(true);
            ((GradeHorarios) campo(cliente, "editSalaHorarios")).marcar(List.of("2M1", "2M2", "3N1"));
            return null;
        });
        pausar();
        acionar("updateRoom", "retornoSalas", "200 - Sala atualizada com sucesso");
        acionar("readRoom", "retornoSalas", "200 - Consulta realizada com sucesso");
        captura("n-atualiza-sala");

        titulo("n) update_room: capacidade abaixo dos 6 participantes da reserva ativa -> 409");
        texto("editSalaNome", "");
        texto("editSalaLocal", "");
        texto("editSalaCapacidade", "5");
        fx(() -> {
            ((CheckBox) campo(cliente, "editSalaEnviarHorarios")).setSelected(false);
            return null;
        });
        acionar("updateRoom", "retornoSalas", "409 - Capacidade menor que participantes de reservas ativas");
        captura("n-capacidade-409");

        titulo("n) update_room: apagar todos os recursos (resources [\"0\"], v2.9)");
        texto("editSalaCapacidade", "");
        fx(() -> {
            ((CheckBox) campo(cliente, "editSalaApagarRecursos")).setSelected(true);
            return null;
        });
        acionar("updateRoom", "retornoSalas", "200 - Sala atualizada com sucesso");
        acionar("readRoom", "retornoSalas", "200 - Consulta realizada com sucesso");
        captura("n-apaga-recursos");

        titulo("o) delete_room: com reserva ativa -> 409; sem reserva -> removida");
        acionarComConfirmacao("deleteRoom", "retornoSalas", "409 - Sala possui reservas ativas");
        apagarReservasDireto();
        texto("salaAlvo", "1");
        acionarComConfirmacao("deleteRoom", "retornoSalas", "200 - Sala removida com sucesso");
        captura("o-remove-sala");

        // ---------------------------------------------------------------- fluxo secundario
        titulo("h) Erros como admin: tudo vazio no update_room, sala inexistente, regex de sala");
        jsonManual("{\"op\":\"update_room\",\"token\":\"" + token() + "\",\"room_id\":\"1\",\"name\":\"\",\"capacity\":\"\","
                + "\"location\":\"\",\"room_status\":\"\"}", "400", "Nenhum dado para atualizar");
        jsonManual("{\"op\":\"read_room\",\"token\":\"" + token() + "\",\"room_id\":\"1\"}", "404", "Sala nao encontrada");
        jsonManual("{\"op\":\"create_room\",\"token\":\"" + token() + "\",\"name\":\"Sala C\",\"capacity\":\"10000\","
                + "\"location\":\"B\",\"resources\":[],\"slots\":[\"2M1\"]}", "400", "Dados da sala em formato invalido");
        jsonManual("{\"op\":\"create_room\",\"token\":\"" + token() + "\",\"name\":\"Sala C\",\"capacity\":\"10\","
                + "\"location\":\"B\",\"resources\":[\"0\"],\"slots\":[\"2M1\"]}", "400", "Dados da sala em formato invalido");
        jsonManual("{\"op\":\"admin_read_user\",\"token\":\"" + token() + "\",\"target_user\":\"JOANA\"}", "400",
                "Dados em formato invalido");
        jsonManual("{\"op\":\"admin_delete_user\",\"token\":\"" + token() + "\",\"target_user\":\"maria\"}", "404",
                "Usuario nao encontrado");
        jsonManual("{\"op\":\"admin_delete_user\",\"token\":\"" + token() + "\",\"target_user\":\"admin\"}", "403",
                "Nao e possivel remover o ultimo administrador");
        captura("h-erros-admin");

        titulo("Logout do admin e login do usuario comum joana");
        aba("abaConta");
        acionar("logout", "retornoLogin", "info");
        texto("loginEmail", "joao@email.com");
        texto("loginSenha", "nova123");
        acionar("login", "retornoConta", "info");
        captura("login-usuario-comum");

        titulo("h) Usuario comum tenta operacoes de admin com \"role\":\"admin\" no JSON -> 403");
        jsonManual("{\"op\":\"admin_list_users\",\"token\":\"" + token() + "\",\"role\":\"admin\"}", "403",
                "Permissao insuficiente");
        jsonManual("{\"op\":\"create_room\",\"token\":\"" + token() + "\",\"role\":\"admin\",\"name\":\"Sala X\","
                + "\"capacity\":\"5\",\"location\":\"B\",\"resources\":[],\"slots\":[\"2M1\"]}", "403", "Permissao insuficiente");
        jsonManual("{\"op\":\"admin_update_user\",\"token\":\"" + token() + "\",\"target_user\":\"joana\",\"user\":\"\","
                + "\"password\":\"\",\"role\":\"admin\"}", "403", "Permissao insuficiente");
        jsonManual("{\"op\":\"list_reservations\",\"token\":\"" + token() + "\",\"scope\":\"all\",\"role\":\"admin\"}", "403",
                "Permissao insuficiente");
        // Secao 4: o 400 de formato vem antes do 403 de perfil
        jsonManual("{\"op\":\"create_room\",\"token\":\"" + token() + "\",\"name\":\"\",\"capacity\":\"5\",\"location\":\"B\","
                + "\"resources\":[],\"slots\":[\"2M1\"]}", "400", "Dados da sala em formato invalido");
        captura("h-usuario-comum-403");

        titulo("h) Token ausente, forjado e linhas fora do protocolo");
        jsonManual("{\"op\":\"admin_list_users\"}", "400", "Token em formato invalido");
        jsonManual("{\"op\": \"login\", \"email\": ", "400", "Requisicao invalida");
        jsonManual("{\"op\":\"reservar_tudo\"}", "400", "Operacao desconhecida");
        jsonManual("{\"op\":\"register\",\"email\":\"Ze@email.com\",\"user\":\"ze\",\"password\":\"senha1\"}", "400",
                "Dados de cadastro em formato invalido");
        // Depois de tudo isso a conexao e a sessao continuam valendo (regra 1.12)
        jsonManual("{\"op\":\"read_user\",\"token\":\"" + token() + "\"}", "200", "Consulta realizada com sucesso");
        captura("h-linhas-invalidas");
        // Token forjado (64 hex sem sessao) -> 401; o cliente descarta o token e volta ao Login (regra 3.9)
        jsonManual("{\"op\":\"read_user\",\"token\":\"" + "f".repeat(64) + "\"}", "401", "Token invalido ou expirado");
        captura("h-token-forjado-401");

        titulo("Fim: as duas janelas continuam abertas para conferencia");
        fx(() -> {
            ((TabPane) palcoServidor.getScene().getRoot()).getSelectionModel().select(1);
            return null;
        });
        captura("fim");
    }

    // ------------------------------------------------------------------ janelas

    private void abrirJanelas() throws Exception {
        fx(() -> {
            Rectangle2D tela = Screen.getPrimary().getVisualBounds();
            double metade = Math.max(900, tela.getWidth() / 2);
            servidor = new ServidorApp();
            palcoServidor = new Stage();
            servidor.start(palcoServidor);
            palcoServidor.setX(tela.getMinX());
            palcoServidor.setY(tela.getMinY());
            palcoServidor.setWidth(metade);
            palcoServidor.setHeight(tela.getHeight());
            cliente = new ClienteApp();
            palcoCliente = new Stage();
            cliente.start(palcoCliente);
            palcoCliente.setX(tela.getMaxX() - metade);
            palcoCliente.setY(tela.getMinY());
            palcoCliente.setWidth(metade);
            palcoCliente.setHeight(tela.getHeight());
            return null;
        });
        Thread.sleep(pausa);
    }

    private void titulo(String texto) {
        System.out.println();
        System.out.println("==== " + texto);
    }

    private void cadastrar(String user, String email, String senha) throws Exception {
        texto("cadastroEmail", email);
        texto("cadastroUsuario", user);
        texto("cadastroSenha", senha);
        acionar("register", "retornoLogin", "201 - Usuario cadastrado com sucesso");
    }

    private void aba(String nome) throws Exception {
        fx(() -> {
            ((TabPane) campo(cliente, "abas")).getSelectionModel().select((Tab) campo(cliente, nome));
            return null;
        });
        pausar();
    }

    private void texto(String nome, String valor) throws Exception {
        fx(() -> {
            ((TextInputControl) campo(cliente, nome)).setText(valor);
            return null;
        });
    }

    /** Executa uma operacao do cliente e confere o retorno mostrado (prefixo; "info" so confere que nao e erro). */
    private void acionar(String metodo, String retorno, String esperado) throws Exception {
        fx(() -> {
            invocar(cliente, metodo);
            return null;
        });
        esperarRede();
        conferir(metodo, retorno, esperado);
        pausar();
    }

    private void acionarComConfirmacao(String metodo, String retorno, String esperado) throws Exception {
        Platform.runLater(() -> invocar(cliente, metodo));
        long limite = System.currentTimeMillis() + 10_000;
        while (fx(DemoEp2::dialogo) == null) {
            if (System.currentTimeMillis() > limite) {
                throw new IllegalStateException("janela de confirmacao nao apareceu");
            }
            Thread.sleep(50);
        }
        Thread.sleep(pausa / 2);
        fx(() -> {
            ((Button) dialogo().lookupButton(ButtonType.OK)).fire();
            return null;
        });
        esperarRede();
        conferir(metodo, retorno, esperado);
        pausar();
    }

    private void conferir(String metodo, String retorno, String esperado) throws Exception {
        String mostrado = fx(() -> ((Label) campo(cliente, retorno)).getText());
        boolean ok = "info".equals(esperado) ? !mostrado.matches("^[45]\\d\\d.*") : mostrado.startsWith(esperado);
        System.out.println((ok ? "  OK     " : "  FALHOU ") + metodo + " -> " + mostrado);
        if (!ok) {
            falhas.add(metodo + ": esperado '" + esperado + "', tela mostrou '" + mostrado + "'");
        }
    }

    /** Envia a linha pelo campo "JSON manual" do cliente e confere status e message da resposta recebida. */
    private void jsonManual(String linha, String status, String message) throws Exception {
        texto("campoJsonManual", linha);
        pausar();
        int antes = fx(() -> recebidas().size());
        fx(() -> {
            invocar(cliente, "jsonManual");
            return null;
        });
        esperarRede();
        String resposta = fx(() -> {
            List<PainelJson.Linha> linhas = recebidas();
            return linhas.size() > antes ? linhas.get(linhas.size() - 1).texto() : "(sem resposta)";
        });
        boolean ok = resposta.contains("\"status\":\"" + status + "\"") && resposta.contains("\"message\":\"" + message + "\"");
        System.out.println((ok ? "  OK     " : "  FALHOU ") + linha + "\n           <- " + resposta);
        if (!ok) {
            falhas.add(linha + ": esperado " + status + " '" + message + "', recebeu " + resposta);
        }
        pausar();
    }

    @SuppressWarnings("unchecked")
    private List<PainelJson.Linha> recebidas() {
        PainelJson painel = (PainelJson) campo(cliente, "painelJson");
        ListView<PainelJson.Linha> lista = (ListView<PainelJson.Linha>) campo(painel, "lista");
        return lista.getItems().stream().filter(l -> l.direcao() == ConexaoJson.Direcao.RECEBIDA).toList();
    }

    private String token() throws Exception {
        return fx(() -> (String) campo(cliente, "token"));
    }

    private void esperarRede() throws Exception {
        int livre = 0;
        long limite = System.currentTimeMillis() + 15_000;
        while (livre < 4 && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
            boolean ocupado = fx(() -> ((BooleanProperty) campo(cliente, "ocupado")).get());
            livre = ocupado ? 0 : livre + 1;
        }
    }

    private void pausar() throws InterruptedException {
        Thread.sleep(pausa);
    }

    /** Grava a imagem das duas janelas (snapshot do JavaFX), uma ao lado da outra. */
    private void captura(String nome) throws Exception {
        String arquivo = String.format("%02d-%s", ++passo, nome);
        for (String lado : new String[] {"servidor", "cliente"}) {
            WritableImage imagem = fx(() -> ("servidor".equals(lado) ? palcoServidor : palcoCliente).getScene()
                    .snapshot(null));
            gravarPng(imagem, capturas.resolve(arquivo + "-" + lado + ".png").toFile());
        }
    }

    private static void gravarPng(WritableImage imagem, File destino) throws Exception {
        int w = (int) imagem.getWidth();
        int h = (int) imagem.getHeight();
        java.awt.image.BufferedImage saida = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        PixelReader leitor = imagem.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                saida.setRGB(x, y, leitor.getArgb(x, y));
            }
        }
        ImageIO.write(saida, "png", destino);
    }

    // ------------------------------------------------------------------ banco (create_reservation ainda nao existe)

    private void reservarDireto(String user, long sala, String date, String inicio, String fim, int participantes)
            throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:agenda.db");
             var ps = c.prepareStatement("INSERT INTO reservas (sala_id, usuario_id, date, start_time, end_time, topic, "
                     + "participants) VALUES (?, (SELECT id FROM usuarios WHERE user = ?), ?, ?, ?, 'Reuniao de projeto', ?)")) {
            ps.setLong(1, sala);
            ps.setString(2, user);
            ps.setString(3, date);
            ps.setString(4, inicio);
            ps.setString(5, fim);
            ps.setInt(6, participantes);
            ps.executeUpdate();
        }
        System.out.println("  (reserva de " + user + " em " + date + " " + inicio + "-" + fim + " inserida no banco)");
    }

    private void apagarReservasDireto() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:agenda.db")) {
            c.createStatement().executeUpdate("DELETE FROM reservas");
        }
        System.out.println("  (reservas apagadas do banco)");
    }

    // ------------------------------------------------------------------ reflexao e thread do JavaFX

    private static DialogPane dialogo() {
        for (Window janela : Window.getWindows()) {
            if (janela.isShowing() && janela.getScene() != null && janela.getScene().getRoot() instanceof DialogPane d) {
                return d;
            }
        }
        return null;
    }

    private static void invocar(Object alvo, String metodo) {
        try {
            Method m = alvo.getClass().getDeclaredMethod(metodo);
            m.setAccessible(true);
            m.invoke(alvo);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object campo(Object alvo, String nome) {
        try {
            Field f = alvo.getClass().getDeclaredField(nome);
            f.setAccessible(true);
            return f.get(alvo);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T fx(Callable<T> acao) throws Exception {
        CompletableFuture<T> resultado = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                resultado.complete(acao.call());
            } catch (Throwable t) {
                resultado.completeExceptionally(t);
            }
        });
        return resultado.get(20, TimeUnit.SECONDS);
    }
}
