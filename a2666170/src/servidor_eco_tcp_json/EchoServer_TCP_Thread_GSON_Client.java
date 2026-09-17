package servidor_eco_tcp_json;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.*;
import java.net.*;

public class EchoServer_TCP_Thread_GSON_Client {

    private static final Gson gson = new Gson();

    public static void main(String[] args) throws IOException {
        
        // Use a single BufferedReader for all keyboard input (br)
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        
        // 1. Get IP
        System.out.println("Qual o IP do servidor? ");
        String serverIP = br.readLine();

        // 2. Get Port and validate
        System.out.println("Qual a Porta do servidor? ");
        int serverPort;
        try {
            serverPort = Integer.parseInt(br.readLine());
        } catch (NumberFormatException e) {
            System.err.println("Porta inválida. O valor deve ser um número.");
            return;
        }

        System.out.println("Tentando conectar com servidor " + serverIP + " na porta " + serverPort);

        // --- Core Fix: Using try-with-resources for automatic resource closing ---
        try (
            // 1. Socket: Opens the connection
            Socket echoSocket = new Socket(serverIP, serverPort);
            // 2. Output Stream: Handles sending text to the server (auto-flush = true)
            PrintWriter out = new PrintWriter(echoSocket.getOutputStream(), true);
            // 3. Input Stream: Handles reading text from the server
            BufferedReader in = new BufferedReader(new InputStreamReader(echoSocket.getInputStream()));
            // 4. Keyboard Input (for conversation): Reads user input
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))
        ) {
            System.out.println("Conectado. Digite (\"Bye\" para sair)");
            String userInput;

            while ((userInput = stdIn.readLine()) != null) {
                // --- 1. Send JSON Request ---
                JsonObject jsonOut = new JsonObject();
                jsonOut.addProperty("message", userInput);
                String jsonStringOut = gson.toJson(jsonOut);
                
                out.println(jsonStringOut);
                System.out.println("Cliente enviou JSON: " + jsonStringOut);

                // Check for exit condition BEFORE reading the response
                if (userInput.equalsIgnoreCase("BYE")) {
                    System.out.println("Saindo...");
                    break;
                }

                // --- 2. Receive JSON Response ---
                try {
                    String serverResponse = in.readLine();
                    
                    if (serverResponse != null) {
                        JsonObject jsonResponse = gson.fromJson(serverResponse, JsonObject.class);
                        
                        // Handle server response structure
                        if (jsonResponse.has("echo")) {
                            System.out.println("Recebido do servidor JSON: " + gson.toJson(jsonResponse));
                        } else if (jsonResponse.has("error")) {
                            System.err.println("Erro do servidor: " + jsonResponse.get("error").getAsString());
                        } else {
                            System.out.println("Resposta do servidor não reconhecida: " + serverResponse);
                        }
                    } else {
                        System.out.println("Servidor fechou a conexão.");
                        break;
                    }
                } catch (JsonParseException e) {
                    System.err.println("Erro ao analisar JSON recebido do servidor. Resposta inválida.");
                    System.err.println("Erro detalhe: " + e.getMessage());
                }
            }
        } catch (UnknownHostException e) {
            System.err.println("Servidor " + serverIP + " não encontrado!");
        } catch (IOException e) {
            System.err.println("Não foi possível conectar ou houve um erro de I/O com " + serverIP + ":" + serverPort + ". Mensagem: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Ocorreu um erro inesperado: " + e.getMessage());
        }
        // All resources are closed automatically by try-with-resources.
    }
}