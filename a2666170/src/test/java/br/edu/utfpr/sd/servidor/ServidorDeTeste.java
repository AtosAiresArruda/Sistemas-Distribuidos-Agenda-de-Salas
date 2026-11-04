package br.edu.utfpr.sd.servidor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.edu.utfpr.sd.comum.ConexaoJson;
import br.edu.utfpr.sd.comum.Json;
import com.google.gson.JsonObject;
import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

/** Servidor real numa porta livre, com banco temporario, para os testes de ponta a ponta da EP-2. */
final class ServidorDeTeste implements Closeable {

    static final String ADMIN_EMAIL = "admin@admin.com";
    static final String ADMIN_SENHA = "admin";

    private final String arquivo;
    private final ServerSocket serverSocket;

    ServidorDeTeste(Path pasta) throws Exception {
        arquivo = pasta.resolve("teste.db").toString();
        BancoDados banco = new BancoDados(arquivo);
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
    }

    /** Nova conexao TCP, como um cliente a mais. */
    Cliente conectar() throws IOException {
        return new Cliente(new ConexaoJson(new Socket("127.0.0.1", serverSocket.getLocalPort()), "Teste"));
    }

    /** Insere uma reserva direto no banco, ja que o create_reservation ainda nao existe. */
    void reservar(String user, long salaId, String date, String inicio, String fim, int participants) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + arquivo);
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

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }

    /** Um cliente de teste: envia linhas cruas e devolve a resposta interpretada. */
    static final class Cliente implements Closeable {
        private final ConexaoJson conexao;

        Cliente(ConexaoJson conexao) {
            this.conexao = conexao;
        }

        JsonObject enviar(String linha) throws IOException {
            conexao.enviarTexto(linha);
            return Json.parseObjeto(conexao.receber());
        }

        JsonObject registrar(String user, String email, String senha) throws IOException {
            return enviar("{\"op\":\"register\",\"email\":\"" + email + "\",\"user\":\"" + user + "\",\"password\":\"" + senha + "\"}");
        }

        /** Faz login e devolve o token, conferindo o 200. */
        String logar(String email, String senha) throws IOException {
            JsonObject resp = enviar("{\"op\":\"login\",\"email\":\"" + email + "\",\"password\":\"" + senha + "\"}");
            assertResposta(resp, "login_response", "200", "Login realizado com sucesso");
            return resp.get("token").getAsString();
        }

        String logarAdmin() throws IOException {
            return logar(ADMIN_EMAIL, ADMIN_SENHA);
        }

        /** Cadastra um usuario comum com senha "senha123" e devolve o token dele. */
        String cadastrarELogar(String user) throws IOException {
            registrar(user, user + "@email.com", "senha123");
            return logar(user + "@email.com", "senha123");
        }

        JsonObject comToken(String op, String token) throws IOException {
            return enviar("{\"op\":\"" + op + "\",\"token\":\"" + token + "\"}");
        }

        @Override
        public void close() throws IOException {
            conexao.close();
        }
    }

    static void assertResposta(JsonObject resposta, String op, String status, String message) {
        assertEquals(op, resposta.get("op").getAsString(), resposta.toString());
        assertEquals(status, resposta.get("status").getAsString(), resposta.toString());
        assertEquals(message, resposta.get("message").getAsString(), resposta.toString());
        // Protocolo 2.1: 'op' e sempre a primeira chave
        assertEquals("op", resposta.keySet().iterator().next());
    }
}
