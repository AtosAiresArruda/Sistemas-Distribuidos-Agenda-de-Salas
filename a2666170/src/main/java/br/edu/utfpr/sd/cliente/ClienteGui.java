package br.edu.utfpr.sd.cliente;

import javafx.application.Application;

/**
 * Ponto de entrada da interface grafica do cliente. Fica separado de {@link ClienteApp}
 * para o JavaFX iniciar a partir do classpath, sem exigir o module path.
 */
public class ClienteGui {

    public static void main(String[] args) {
        Application.launch(ClienteApp.class, args);
    }
}
