package br.edu.utfpr.sd.comum;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Horarios das salas no codigo da UTFPR (protocolo, secao 8): dia + turno + aula, ex.: "2M1" = segunda,
 * manha, primeira aula (07:30-08:20). Usado pelo servidor (slots, reservas e agenda) e pelo cliente (grade).
 */
public final class Horarios {

    /** Regex do dicionario: dia de 2 (segunda) a 7 (sabado), turno M/T/N, aula 1-6 (M, T) ou 1-5 (N). */
    public static final Pattern SLOT = Pattern.compile("^[2-7](M[1-6]|T[1-6]|N[1-5])$");

    // Digito do dia no codigo e o nome exibido na interface, na mesma ordem
    public static final List<String> DIAS = List.of("2", "3", "4", "5", "6", "7");
    public static final List<String> NOMES_DIAS = List.of("Segunda", "Terca", "Quarta", "Quinta", "Sexta", "Sabado");

    // Tabela de aulas da secao 8, na ordem do dia: inicio e fim de cada aula
    public static final List<String> AULAS = List.of(
            "M1", "M2", "M3", "M4", "M5", "M6",
            "T1", "T2", "T3", "T4", "T5", "T6",
            "N1", "N2", "N3", "N4", "N5");
    public static final List<String> INICIOS = List.of(
            "07:30", "08:20", "09:10", "10:20", "11:10", "12:00",
            "13:00", "13:50", "14:40", "15:50", "16:40", "17:30",
            "18:40", "19:30", "20:20", "21:20", "22:10");
    public static final List<String> FINS = List.of(
            "08:20", "09:10", "10:00", "11:10", "12:00", "12:50",
            "13:50", "14:40", "15:30", "16:40", "17:30", "18:20",
            "19:30", "20:20", "21:10", "22:10", "23:00");

    /** Ordem da tabela: dia da semana e depois a aula do dia. */
    public static final Comparator<String> ORDEM = Comparator
            .comparingInt((String slot) -> DIAS.indexOf(slot.substring(0, 1)))
            .thenComparingInt(slot -> AULAS.indexOf(slot.substring(1)));

    private Horarios() {
    }

    public static boolean valido(String slot) {
        return slot != null && SLOT.matcher(slot).matches();
    }

    /** Codigo do horario, ex.: codigo("2", "M1") = "2M1". */
    public static String codigo(String dia, String aula) {
        return dia + aula;
    }

    /** Aula do codigo, sem o dia: "3T1" -> "T1". */
    public static String aula(String slot) {
        return slot.substring(1);
    }

    public static String inicio(String aula) {
        return INICIOS.get(AULAS.indexOf(aula));
    }

    public static String fim(String aula) {
        return FINS.get(AULAS.indexOf(aula));
    }

    /**
     * Protocolo 8.3: start_time e o inicio de uma aula, end_time o fim de uma aula, e o fim vem depois do inicio.
     * Os horarios da tabela crescem, entao basta a aula do fim nao ser anterior a aula do inicio.
     */
    public static boolean periodoValido(String inicio, String fim) {
        int a = INICIOS.indexOf(inicio);
        int b = FINS.indexOf(fim);
        return a >= 0 && b >= a;
    }

    /** Aulas ocupadas por um periodo valido, inclusive atravessando o intervalo (09:10-11:10 = M3 e M4). */
    public static List<String> aulasDoPeriodo(String inicio, String fim) {
        return AULAS.subList(INICIOS.indexOf(inicio), FINS.indexOf(fim) + 1);
    }

    /** Digito do dia da semana no codigo ("2" = segunda ... "7" = sabado); null no domingo. */
    public static String diaDaSemana(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        return dia == DayOfWeek.SUNDAY ? null : String.valueOf(dia.getValue() + 1);
    }

    /**
     * Protocolo 8.4: todas as aulas ocupadas pelo periodo, no dia da semana da data, estao nos slots da sala.
     * Domingo nunca esta no funcionamento.
     */
    public static boolean dentroDoFuncionamento(Collection<String> slots, LocalDate data, String inicio, String fim) {
        String dia = diaDaSemana(data);
        if (dia == null) {
            return false;
        }
        return aulasDoPeriodo(inicio, fim).stream().allMatch(aula -> slots.contains(codigo(dia, aula)));
    }

    /** Slots da sala num dia da semana, na ordem da tabela (agenda do read_room, protocolo 8.5). */
    public static List<String> slotsDoDia(Collection<String> slots, String dia) {
        List<String> doDia = new ArrayList<>();
        if (dia != null) {
            for (String aula : AULAS) {
                if (slots.contains(codigo(dia, aula))) {
                    doDia.add(codigo(dia, aula));
                }
            }
        }
        return doDia;
    }

    /** Resumo legivel, ex.: "Segunda: M1 M2 | Quarta: T3"; "-" se nao houver horarios. */
    public static String resumo(Collection<String> slots) {
        StringBuilder texto = new StringBuilder();
        for (int d = 0; d < DIAS.size(); d++) {
            String dia = DIAS.get(d);
            List<String> aulas = slots.stream().filter(s -> valido(s) && s.startsWith(dia)).sorted(ORDEM)
                    .map(Horarios::aula).toList();
            if (!aulas.isEmpty()) {
                texto.append(texto.isEmpty() ? "" : " | ").append(NOMES_DIAS.get(d)).append(": ")
                        .append(String.join(" ", aulas));
            }
        }
        return texto.isEmpty() ? "-" : texto.toString();
    }
}
