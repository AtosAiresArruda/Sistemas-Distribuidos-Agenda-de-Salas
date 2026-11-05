package br.edu.utfpr.sd.cliente;

import br.edu.utfpr.sd.comum.Horarios;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.HPos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;

/**
 * Grade da UTFPR para marcar os horarios da sala: uma linha por dia da semana (segunda a sabado) e uma
 * coluna por aula (M1 ... N5), com o inicio de cada aula no cabecalho. Gera e le o campo "slots"
 * no codigo da UTFPR (protocolo, secao 8): a caixa de segunda na coluna M1 vale "2M1".
 */
public class GradeHorarios extends GridPane {

    private final CheckBox[][] caixas = new CheckBox[Horarios.DIAS.size()][Horarios.AULAS.size()];

    public GradeHorarios() {
        setHgap(4);
        setVgap(4);
        for (int a = 0; a < Horarios.AULAS.size(); a++) {
            String aula = Horarios.AULAS.get(a);
            Label cabecalho = new Label(aula + "\n" + Horarios.INICIOS.get(a));
            cabecalho.getStyleClass().add("dica");
            cabecalho.setTooltip(new Tooltip(aula + ": " + Horarios.INICIOS.get(a) + " - " + Horarios.FINS.get(a)));
            GridPane.setHalignment(cabecalho, HPos.CENTER);
            add(cabecalho, a + 1, 0);
        }
        for (int d = 0; d < Horarios.DIAS.size(); d++) {
            add(new Label(Horarios.NOMES_DIAS.get(d)), 0, d + 1);
            for (int a = 0; a < Horarios.AULAS.size(); a++) {
                String slot = Horarios.codigo(Horarios.DIAS.get(d), Horarios.AULAS.get(a));
                CheckBox caixa = new CheckBox();
                caixa.setMnemonicParsing(false);
                caixa.setTooltip(new Tooltip(slot + " (" + Horarios.NOMES_DIAS.get(d) + ", "
                        + Horarios.INICIOS.get(a) + " - " + Horarios.FINS.get(a) + ")"));
                GridPane.setHalignment(caixa, HPos.CENTER);
                caixas[d][a] = caixa;
                add(caixa, a + 1, d + 1);
            }
        }
    }

    /** Horarios marcados no codigo da UTFPR, na ordem da tabela (dia e depois aula). */
    public List<String> selecionados() {
        List<String> slots = new ArrayList<>();
        for (int d = 0; d < caixas.length; d++) {
            for (int a = 0; a < caixas[d].length; a++) {
                if (caixas[d][a].isSelected()) {
                    slots.add(Horarios.codigo(Horarios.DIAS.get(d), Horarios.AULAS.get(a)));
                }
            }
        }
        return slots;
    }

    /** Valor do campo "slots": ["2M1", "4T3", ...]. */
    public JsonArray comoJson() {
        JsonArray lista = new JsonArray();
        selecionados().forEach(lista::add);
        return lista;
    }

    /** Marca exatamente os horarios informados; codigos fora do formato sao ignorados. */
    public void marcar(List<String> slots) {
        limpar();
        for (String slot : slots) {
            if (Horarios.valido(slot)) {
                caixas[Horarios.DIAS.indexOf(slot.substring(0, 1))][Horarios.AULAS.indexOf(Horarios.aula(slot))]
                        .setSelected(true);
            }
        }
    }

    public void limpar() {
        for (CheckBox[] linha : caixas) {
            for (CheckBox caixa : linha) {
                caixa.setSelected(false);
            }
        }
    }

    /** Le os "slots" de uma resposta; tolera servidores que omitam o campo ou mandem itens fora do formato. */
    public static List<String> lerSlots(JsonObject objeto) {
        List<String> slots = new ArrayList<>();
        JsonElement lista = objeto.get("slots");
        if (lista == null || !lista.isJsonArray()) {
            return slots;
        }
        for (JsonElement item : lista.getAsJsonArray()) {
            if (item.isJsonPrimitive()) {
                slots.add(item.getAsString());
            }
        }
        return slots;
    }
}
