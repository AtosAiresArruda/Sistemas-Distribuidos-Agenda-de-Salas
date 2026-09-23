package br.edu.utfpr.sd.servidor;

import javafx.application.Application;

/**
 * Ponto de entrada da interface grafica do servidor. Fica separado de {@link ServidorApp}
 * para o JavaFX iniciar a partir do classpath, sem exigir o module path.
 */
public class ServidorGui {

    public static void main(String[] args) {
        Application.launch(ServidorApp.class, args);
    }
}
