package br.edu.utfpr.sd.comum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tabela de aulas e codigo dos horarios da UTFPR (protocolo, secao 8). */
class HorariosTest {

    @Test
    void codigoDoHorario() {
        for (String valido : List.of("2M1", "7N5", "4T3", "3M6", "6T6")) {
            assertTrue(Horarios.valido(valido), valido);
        }
        for (String invalido : List.of("1M1", "8M1", "2M7", "2M0", "2T7", "2N6", "2m1", "M1", "2M", "22M1", "2M1 ", "")) {
            assertFalse(Horarios.valido(invalido), invalido);
        }
        assertEquals(17, Horarios.AULAS.size());
        assertEquals(17, Horarios.INICIOS.size());
        assertEquals(17, Horarios.FINS.size());
    }

    @Test
    void periodoUsaInicioEFimDeAula() {
        assertTrue(Horarios.periodoValido("07:30", "08:20"));
        assertTrue(Horarios.periodoValido("13:00", "14:40"));
        assertTrue(Horarios.periodoValido("07:30", "23:00"));
        // Fim antes do inicio, igual ao inicio ou fora da tabela
        assertFalse(Horarios.periodoValido("13:50", "13:50"));
        assertFalse(Horarios.periodoValido("14:40", "13:50"));
        assertFalse(Horarios.periodoValido("08:00", "09:10"));
        assertFalse(Horarios.periodoValido("07:30", "09:00"));
        assertFalse(Horarios.periodoValido("23:00", "23:00"));
        // 8.3: o periodo atravessa o intervalo (09:10-11:10 = M3 e M4)
        assertEquals(List.of("M3", "M4"), Horarios.aulasDoPeriodo("09:10", "11:10"));
        assertEquals(List.of("T1", "T2"), Horarios.aulasDoPeriodo("13:00", "14:40"));
    }

    @Test
    void funcionamentoPeloDiaDaSemana() {
        LocalDate terca = LocalDate.of(2099, 1, 13);
        assertEquals("3", Horarios.diaDaSemana(terca));
        assertNull(Horarios.diaDaSemana(LocalDate.of(2099, 1, 11)));
        // 8.4: reservar 13:00-14:40 numa terca exige 3T1 e 3T2
        assertTrue(Horarios.dentroDoFuncionamento(List.of("3T1", "3T2"), terca, "13:00", "14:40"));
        assertFalse(Horarios.dentroDoFuncionamento(List.of("3T1"), terca, "13:00", "14:40"));
        assertFalse(Horarios.dentroDoFuncionamento(List.of("2T1", "2T2"), terca, "13:00", "14:40"));
        assertFalse(Horarios.dentroDoFuncionamento(List.of("3T1", "3T2"), LocalDate.of(2099, 1, 11), "13:00", "14:40"));
    }

    @Test
    void ordemEResumo() {
        List<String> slots = List.of("4T3", "2M2", "7N5", "2M1", "2T1");
        assertEquals(List.of("2M1", "2M2", "2T1", "4T3", "7N5"), slots.stream().sorted(Horarios.ORDEM).toList());
        assertEquals(List.of("2M1", "2M2", "2T1"), Horarios.slotsDoDia(slots, "2"));
        assertEquals("Segunda: M1 M2 T1 | Quarta: T3 | Sabado: N5", Horarios.resumo(slots));
        assertEquals("-", Horarios.resumo(List.of()));
    }
}
