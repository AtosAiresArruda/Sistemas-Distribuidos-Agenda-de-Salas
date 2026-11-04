package br.edu.utfpr.sd.cliente;

import br.edu.utfpr.sd.comum.Horarios;
import br.edu.utfpr.sd.comum.Horarios.Horario;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.HPos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

/**
 * Grade de caixas de selecao: uma linha por dia da semana e uma coluna por bloco (M1 ... N5).
 * Gera e le o campo "schedule" das mensagens de sala.
 */
public class GradeHorarios extends GridPane {

    private final CheckBox[][] caixas = new CheckBox[Horarios.DIAS.size()][Horarios.SLOTS.size()];

    public GradeHorarios() {
        setHgap(4);
        setVgap(4);
        for (int s = 0; s < Horarios.SLOTS.size(); s++) {
            Label slot = new Label(Horarios.SLOTS.get(s));
            slot.getStyleClass().add("dica");
            GridPane.setHalignment(slot, HPos.CENTER);
            add(slot, s + 1, 0);
        }
        for (int d = 0; d < Horarios.DIAS.size(); d++) {
            add(new Label(Horarios.NOMES_DIAS.get(d)), 0, d + 1);
            for (int s = 0; s < Horarios.SLOTS.size(); s++) {
                CheckBox caixa = new CheckBox();
                caixa.setMnemonicParsing(false);
                GridPane.setHalignment(caixa, HPos.CENTER);
                caixas[d][s] = caixa;
                add(caixa, s + 1, d + 1);
            }
        }
    }

    /** Blocos marcados, em ordem de dia e bloco. */
    public List<Horario> selecionados() {
        List<Horario> horarios = new ArrayList<>();
        for (int d = 0; d < caixas.length; d++) {
            for (int s = 0; s < caixas[d].length; s++) {
                if (caixas[d][s].isSelected()) {
                    horarios.add(new Horario(Horarios.DIAS.get(d), Horarios.SLOTS.get(s)));
                }
            }
        }
        return horarios;
    }

    /** Valor do campo "schedule": [{"day": "...", "slot": "..."}, ...]. */
    public JsonArray comoJson() {
        JsonArray lista = new JsonArray();
        for (Horario horario : selecionados()) {
            JsonObject item = new JsonObject();
            item.addProperty("day", horario.day());
            item.addProperty("slot", horario.slot());
            lista.add(item);
        }
        return lista;
    }

    /** Marca exatamente os horarios informados; itens fora do formato sao ignorados. */
    public void marcar(List<Horario> horarios) {
        limpar();
        for (Horario horario : horarios) {
            int d = Horarios.DIAS.indexOf(horario.day());
            int s = Horarios.SLOTS.indexOf(horario.slot());
            if (d >= 0 && s >= 0) {
                caixas[d][s].setSelected(true);
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

    /** Le o "schedule" de uma resposta; servidores que nao usam a extensao nao mandam o campo (lista vazia). */
    public static List<Horario> lerSchedule(JsonObject objeto) {
        List<Horario> horarios = new ArrayList<>();
        JsonElement lista = objeto.get("schedule");
        if (lista == null || !lista.isJsonArray()) {
            return horarios;
        }
        for (JsonElement item : lista.getAsJsonArray()) {
            if (item.isJsonObject()) {
                JsonObject o = item.getAsJsonObject();
                JsonElement day = o.get("day");
                JsonElement slot = o.get("slot");
                if (day != null && day.isJsonPrimitive() && slot != null && slot.isJsonPrimitive()) {
                    horarios.add(new Horario(day.getAsString(), slot.getAsString()));
                }
            }
        }
        return horarios;
    }
}
