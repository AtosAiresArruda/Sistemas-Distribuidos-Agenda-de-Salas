package br.edu.utfpr.sd.comum;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Confere os campos de sala e de filtro com as regex do dicionario do protocolo e explica o problema.
 * Usado pelo cliente antes de enviar. Cada metodo retorna null se o valor for valido, ou a explicacao.
 */
public final class ValidacaoSala {

    public static final Pattern ROOM_ID = Pattern.compile("^[0-9]{1,10}$");
    public static final Pattern NAME = Pattern.compile("^[A-Za-z0-9 -]{1,30}$");
    public static final Pattern CAPACITY = Pattern.compile("^[1-9][0-9]{0,3}$");
    public static final Pattern LOCATION = Pattern.compile("^[A-Za-z0-9 ,.-]{1,60}$");
    // Dicionario (v2.9): mes 01-12 e dia 01-31; a data tambem precisa existir no calendario
    public static final Pattern DATE = Pattern.compile("^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$");
    // Regra 9.7: cada recurso com esta regex e diferente de "0"
    public static final Pattern RESOURCE = Pattern.compile("^[A-Za-z0-9 -]{1,30}$");

    private ValidacaoSala() {
    }

    public static String roomId(String valor) {
        return confere(ROOM_ID, valor, "Sala (room_id) invalida: de 1 a 10 digitos, gerado pelo servidor no cadastro.");
    }

    public static String name(String valor) {
        return confere(NAME, valor, "Nome da sala invalido: de 1 a 30 letras, numeros, espacos ou '-', sem acentos.");
    }

    public static String capacity(String valor) {
        return confere(CAPACITY, valor, "Capacidade invalida: numero de 1 a 9999, sem zeros a esquerda.");
    }

    public static String location(String valor) {
        return confere(LOCATION, valor,
                "Localizacao invalida: de 1 a 60 letras, numeros, espacos, ',', '.' ou '-', sem acentos.");
    }

    /** Regra 9.7: cada recurso de 1 a 30 letras, numeros, espacos ou '-', e nenhum pode ser "0". */
    public static String resources(JsonArray recursos) {
        for (JsonElement item : recursos) {
            String recurso = item.getAsString();
            if (!RESOURCE.matcher(recurso).matches() || "0".equals(recurso)) {
                return "Recurso invalido: '" + recurso + "'. Cada recurso tem de 1 a 30 letras, numeros, espacos ou "
                        + "'-', sem acentos, e nao pode ser \"0\" (separe os recursos por virgula).";
            }
        }
        return null;
    }

    public static String date(String valor) {
        String problema = "Data invalida: formato AAAA-MM-DD, com dia e mes existentes (ex.: 2026-09-15).";
        return confere(DATE, valor, problema) == null && dataExiste(valor) ? null : problema;
    }

    /** Protocolo 8.3: inicio de uma aula e fim de uma aula da UTFPR, com o fim depois do inicio. */
    public static String periodo(String inicio, String fim) {
        return Horarios.periodoValido(inicio, fim) ? null
                : "Periodo invalido: o inicio deve ser o comeco de uma aula, o fim o termino de uma aula, "
                        + "e o fim deve vir depois do inicio (ex.: 13:00 a 14:40 = T1 e T2).";
    }

    public static String minCapacity(String valor) {
        return confere(CAPACITY, valor, "Capacidade minima invalida: numero de 1 a 9999, sem zeros a esquerda.");
    }

    /** A regex aceita "2026-02-30"; a data tambem precisa existir no calendario. */
    public static boolean dataExiste(String valor) {
        try {
            LocalDate.parse(valor);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static String confere(Pattern formato, String valor, String problema) {
        return formato.matcher(valor).matches() ? null : problema;
    }
}
