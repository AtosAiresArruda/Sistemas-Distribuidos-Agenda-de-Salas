package br.edu.utfpr.sd.comum;

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
    public static final Pattern DATE = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");
    public static final Pattern TIME = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");

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

    public static String date(String valor) {
        return confere(DATE, valor, "Data invalida: formato AAAA-MM-DD (ex.: 2026-09-15).");
    }

    public static String time(String rotulo, String valor) {
        return confere(TIME, valor, rotulo + " invalido: formato HH:MM de 00:00 a 23:59 (ex.: 14:00).");
    }

    public static String minCapacity(String valor) {
        return confere(CAPACITY, valor, "Capacidade minima invalida: numero de 1 a 9999, sem zeros a esquerda.");
    }

    private static String confere(Pattern formato, String valor, String problema) {
        return formato.matcher(valor).matches() ? null : problema;
    }
}
