package br.edu.utfpr.sd.servidor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

public class Servidor {

    private static final String ARQUIVO_BANCO = "agenda.db";

    public static void main(String[] args) throws IOException {
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        System.out.println("Qual porta o servidor deve usar? ");
        int porta;
        try {
            porta = Integer.parseInt(teclado.readLine().trim());
        } catch (NumberFormatException e) {
            System.err.println("Porta invalida. O valor deve ser um numero.");
            return;
        }

        BancoDados banco;
        try {
            banco = new BancoDados(ARQUIVO_BANCO);
        } catch (SQLException e) {
            System.err.println("Nao foi possivel abrir o banco " + ARQUIVO_BANCO + ": " + e.getMessage());
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(porta)) {
            System.out.println("Servidor carregado na porta " + porta + ". Aguardando conexoes...\n");
            atender(serverSocket, banco);
        } catch (BindException e) {
            System.err.println("Erro: a porta " + porta + " esta ocupada. Escolha outra porta.");
        }
    }

    /** Aceita conexoes ate o socket ser fechado, com uma thread por cliente (protocolo 1.1). */
    public static void atender(ServerSocket serverSocket, BancoDados banco) throws IOException {
        atender(serverSocket, banco, MonitorServidor.NENHUM);
    }

    /** Igual ao anterior, avisando o monitor sobre cada thread e cada mensagem. */
    public static void atender(ServerSocket serverSocket, BancoDados banco, MonitorServidor monitor) throws IOException {
        while (!serverSocket.isClosed()) {
            Socket cliente = serverSocket.accept();
            new TratadorCliente(cliente, banco, monitor).start();
        }
    }
}
