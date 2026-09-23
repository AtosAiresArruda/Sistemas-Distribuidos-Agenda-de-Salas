package br.edu.utfpr.sd.comum;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;

public final class Json {

    // Serializa em linha unica (protocolo 1.4) sem escapar caracteres como '=' e '<'
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Json() {
    }

    /**
     * Converte uma linha recebida em objeto JSON.
     * Lanca JsonParseException se a linha nao for exatamente um objeto JSON valido.
     */
    public static JsonObject parseObjeto(String linha) {
        try {
            JsonReader reader = new JsonReader(new StringReader(linha));
            reader.setStrictness(Strictness.STRICT);
            JsonElement elemento = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonParseException("Conteudo extra apos o JSON");
            }
            if (!elemento.isJsonObject()) {
                throw new JsonParseException("A mensagem nao e um objeto JSON");
            }
            return elemento.getAsJsonObject();
        } catch (IOException e) {
            throw new JsonParseException(e);
        }
    }

    /** Cria uma resposta com os campos minimos do protocolo (2.6), com 'op' como primeira chave. */
    public static JsonObject resposta(String op, String status, String message) {
        JsonObject resposta = new JsonObject();
        resposta.addProperty("op", op);
        resposta.addProperty("status", status);
        resposta.addProperty("message", message);
        return resposta;
    }
}
