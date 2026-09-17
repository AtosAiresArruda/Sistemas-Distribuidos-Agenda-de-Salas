package servidor_eco_tcp_json;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.net.*;
import java.io.*;

public class EchoServer_TCP_Thread_GSON_Server extends Thread {

    protected Socket clientSocket;
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws IOException {
        
        // Use a single BufferedReader for console input
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int porta;

        // 1. Get Port and validate
        System.out.println("Qual porta o servidor deve usar? ");
        try {
            porta = Integer.parseInt(br.readLine());
        } catch (NumberFormatException e) {
            System.err.println("Porta inválida. O valor deve ser um número.");
            return;
        }

        System.out.println("Servidor carregado na porta " + porta);
        System.out.println("Aguardando conexao....\n");

        // --- Core Fix: Using try-with-resources for automatic ServerSocket closure ---
        try (ServerSocket serverSocket = new ServerSocket(porta)) {
            System.out.println("Criado Socket de Conexao.\n");
            System.out.println("Esperando por uma conexao...\n");
            
            while (true) {
                // The accept() call blocks until a client connects.
                Socket newClientSocket = serverSocket.accept();
                // Pass the new socket to a new thread instance and start it
                new EchoServer_TCP_Thread_GSON_Server(newClientSocket).start(); //recebe conexão e aguarda mais uma
                System.out.println("Nova conexao aceita. Esperando por outra conexao...\n");
            }
        } catch (BindException e) {
            System.err.println("Erro: A porta " + porta + " está ocupada. Escolha outra porta.");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Erro fatal no servidor principal: " + e.getMessage());
            System.exit(1);
        } 
        // ServerSocket closes automatically here. The manual finally block is no longer needed.
    }

    // Constructor
    private EchoServer_TCP_Thread_GSON_Server(Socket clientSoc) {
        // Assign the client socket received from the main thread
        clientSocket = clientSoc;
    }

    /**
     * Override java.language.Thread
     */
    @Override
    public void run() {
        String clientInfo = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        System.out.println("Nova thread de comunicacao iniciada com cliente: " + clientInfo);

        // --- Core Fix: Include clientSocket in try-with-resources for guaranteed closure ---
        try (
            // 1. Client Socket: Use the assigned clientSocket and ensure it closes
            Socket client = clientSocket;
            // 2. Output Stream
            PrintWriter out = new PrintWriter(client.getOutputStream(), true);
            // 3. Input Stream
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()))
        ) {
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                System.out.println("Servidor recebeu JSON: " + inputLine + " do cliente: " + clientInfo);
                try {
                    JsonObject jsonObject = gson.fromJson(inputLine, JsonObject.class);
                    // Handle missing "message" field gracefully
                    if (!jsonObject.has("message") || jsonObject.get("message").isJsonNull()) {
                         throw new NullPointerException("Campo 'message' ausente ou nulo.");
                    }

                    //Recolher texto do json
                    String message = jsonObject.get("message").getAsString();

                    JsonObject responseJson = new JsonObject();
                    responseJson.addProperty("echo", message.toUpperCase());
                    out.println(gson.toJson(responseJson));
                    
                    String jsonStringOut = gson.toJson(responseJson);
                    System.out.println("Servidor enviou JSON: " + jsonStringOut + " para o cliente: " + clientInfo);

                    if (message.equalsIgnoreCase("bye"))
                        break;

                }

                catch (JsonParseException e) {
                    System.err.println("Erro ao analisar JSON recebido do cliente " + clientInfo + ": " + e.getMessage());
                    JsonObject errorJson = new JsonObject();
                    errorJson.addProperty("error", "Formato JSON inválido");
                    out.println(gson.toJson(errorJson));
                }

                catch (NullPointerException e) {
                    System.err.println("Erro: " + e.getMessage() + " do cliente " + clientInfo + "!");
                    JsonObject errorJson = new JsonObject();
                    errorJson.addProperty("error", e.getMessage());
                    out.println(gson.toJson(errorJson));
                }
            }
            System.out.println("Conexao encerrada com cliente: " + clientInfo);
        }

        catch (IOException e) {
            System.err.println("Problema de I/O na thread do cliente " + clientInfo + ": " + e.getMessage());
        }
        // All resources (clientSocket, PrintWriter, BufferedReader) are automatically closed here.
    }
}