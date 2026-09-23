package br.edu.utfpr.sd.comum;

import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Troca de mensagens JSON sobre um socket TCP, seguindo o protocolo:
 * UTF-8, uma mensagem por linha terminada em '\n' e no maximo 8192 bytes (incluindo o '\n').
 * Toda mensagem enviada e recebida e exibida no console.
 */
public class ConexaoJson implements Closeable {

    public static final int TAMANHO_MAXIMO = 8192;

    /** Mensagem recebida acima do limite; ja foi descartada ate o '\n'. */
    public static class MensagemMuitoGrandeException extends IOException {
        public MensagemMuitoGrandeException() {
            super("Mensagem excede " + TAMANHO_MAXIMO + " bytes");
        }
    }

    public enum Direcao { ENVIADA, RECEBIDA }

    /** Recebe uma copia de cada mensagem trafegada, para as interfaces graficas. */
    @FunctionalInterface
    public interface Ouvinte {
        void mensagem(Direcao direcao, String texto);
    }

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final String lado;
    private final String remoto;
    private volatile Ouvinte ouvinte;

    /** @param lado "Servidor" ou "Cliente", usado nos logs */
    public ConexaoJson(Socket socket, String lado) throws IOException {
        this.socket = socket;
        this.in = new BufferedInputStream(socket.getInputStream());
        this.out = socket.getOutputStream();
        this.lado = lado;
        this.remoto = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }

    public String getRemoto() {
        return remoto;
    }

    public void setOuvinte(Ouvinte ouvinte) {
        this.ouvinte = ouvinte;
    }

    public void enviar(JsonObject mensagem) throws IOException {
        enviarTexto(Json.GSON.toJson(mensagem));
    }

    /** Envia o texto como esta; usado tambem para testar servidores com mensagens arbitrarias. */
    public void enviarTexto(String mensagem) throws IOException {
        out.write(mensagem.getBytes(StandardCharsets.UTF_8));
        out.write('\n');
        out.flush();
        System.out.println("[" + lado + "] enviou para " + remoto + ": " + mensagem);
        notificar(Direcao.ENVIADA, mensagem);
    }

    /**
     * Le uma mensagem completa (ate o '\n').
     * Retorna null se a conexao foi encerrada antes de uma mensagem completa chegar.
     */
    public String receber() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int lidos = 0;
        boolean excedeu = false;
        int b;
        while ((b = in.read()) != -1) {
            lidos++;
            if (b == '\n') {
                break;
            }
            // Com mais este byte e o '\n' final, a mensagem passaria do limite
            if (lidos >= TAMANHO_MAXIMO) {
                excedeu = true;
            }
            if (!excedeu) {
                buffer.write(b);
            }
        }
        if (b == -1) {
            return null;
        }
        if (excedeu) {
            System.out.println("[" + lado + "] descartou mensagem de " + remoto + " com " + lidos + " bytes (acima do limite)");
            notificar(Direcao.RECEBIDA, "(mensagem de " + lidos + " bytes descartada: acima do limite)");
            throw new MensagemMuitoGrandeException();
        }

        String mensagem = buffer.toString(StandardCharsets.UTF_8);
        if (mensagem.endsWith("\r")) {
            mensagem = mensagem.substring(0, mensagem.length() - 1);
        }
        System.out.println("[" + lado + "] recebeu de " + remoto + ": " + mensagem);
        notificar(Direcao.RECEBIDA, mensagem);
        return mensagem;
    }

    private void notificar(Direcao direcao, String texto) {
        Ouvinte o = ouvinte;
        if (o != null) {
            o.mensagem(direcao, texto);
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
