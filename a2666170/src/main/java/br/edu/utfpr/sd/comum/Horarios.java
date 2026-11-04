package br.edu.utfpr.sd.comum;

import java.util.Comparator;
import java.util.List;

/**
 * Dias e blocos de horario das salas (extensao do protocolo decidida no projeto, campo "schedule").
 * Cada bloco (M1 ... N5) e uma flag fixa, sem hora em numero, para padronizar as mensagens.
 */
public final class Horarios {

    // Valores enviados no JSON: minusculas e sem acento
    public static final List<String> DIAS = List.of("segunda", "terca", "quarta", "quinta", "sexta", "sabado");
    public static final List<String> NOMES_DIAS = List.of("Segunda", "Terca", "Quarta", "Quinta", "Sexta", "Sabado");
    public static final List<String> SLOTS = List.of(
            "M1", "M2", "M3", "M4", "M5", "M6",
            "T1", "T2", "T3", "T4", "T5", "T6",
            "N1", "N2", "N3", "N4", "N5");

    /** Um bloco de horario em um dia da semana: {"day": "segunda", "slot": "M1"}. */
    public record Horario(String day, String slot) {
    }

    /** Ordem natural: dia da semana e depois o bloco do dia. */
    public static final Comparator<Horario> ORDEM = Comparator
            .comparingInt((Horario h) -> DIAS.indexOf(h.day()))
            .thenComparingInt(h -> SLOTS.indexOf(h.slot()));

    private Horarios() {
    }

    public static boolean valido(Horario horario) {
        return DIAS.contains(horario.day()) && SLOTS.contains(horario.slot());
    }

    /** Resumo legivel, ex.: "segunda: M1 M2 | quarta: T3"; "-" se nao houver horarios. */
    public static String resumo(List<Horario> horarios) {
        StringBuilder texto = new StringBuilder();
        for (String dia : DIAS) {
            List<String> slots = horarios.stream().filter(h -> h.day().equals(dia)).sorted(ORDEM)
                    .map(Horario::slot).toList();
            if (!slots.isEmpty()) {
                texto.append(texto.isEmpty() ? "" : " | ").append(dia).append(": ").append(String.join(" ", slots));
            }
        }
        return texto.isEmpty() ? "-" : texto.toString();
    }
}
