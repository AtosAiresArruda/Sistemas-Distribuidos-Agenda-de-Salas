package br.edu.utfpr.sd.comum;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Confere os campos de cadastro com as regex da aba "Dicionario" do protocolo e explica
 * o que esta errado. Usado pelo cliente antes de enviar, pois o servidor so responde
 * a mensagem generica definida no protocolo.
 * Cada metodo retorna null se o valor for valido, ou a explicacao do problema.
 */
public final class ValidacaoCadastro {

    public static final Pattern USER = Pattern.compile("^[a-z]{1,30}$");
    public static final Pattern PASSWORD = Pattern.compile("^[A-Za-z0-9]{1,20}$");
    public static final Pattern EMAIL = Pattern.compile("^[a-z0-9._-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$");

    private static final String FORMATO_EMAIL =
            "Formato esperado: letras minusculas, numeros, ponto, '_' ou '-' antes do @; depois do @, um dominio "
                    + "terminado em 2 ou mais letras (ex.: joao.silva@email.com ou maria@alunos.utfpr.edu.br).";
    private static final String FORMATO_USER =
            "Formato esperado: de 1 a 30 letras minusculas (a-z), sem numeros, espacos, acentos ou simbolos.";
    private static final String FORMATO_PASSWORD =
            "Formato esperado: de 1 a 20 letras e numeros (A-Z, a-z, 0-9), sem espacos, acentos ou simbolos.";

    private ValidacaoCadastro() {
    }

    public static String email(String email) {
        if (EMAIL.matcher(email).matches()) {
            return null;
        }
        String problema;
        int arroba = email.indexOf('@');
        if (email.isEmpty()) {
            problema = "o email esta vazio";
        } else if (!email.equals(email.toLowerCase(Locale.ROOT))) {
            problema = "o email nao aceita letras maiusculas";
        } else if (arroba < 0 || arroba != email.lastIndexOf('@')) {
            problema = "o email deve ter exatamente um @";
        } else if (arroba == 0) {
            problema = "falta o nome antes do @";
        } else if (!email.substring(0, arroba).matches("[a-z0-9._-]+")) {
            problema = "antes do @ so sao aceitos letras minusculas, numeros, ponto, '_' e '-'";
        } else {
            String dominio = email.substring(arroba + 1);
            String[] partes = dominio.split("\\.", -1);
            if (!dominio.matches("[a-z0-9.-]+")) {
                problema = "depois do @ so sao aceitos letras minusculas, numeros, ponto e '-'";
            } else if (partes.length == 1) {
                problema = "o dominio precisa de pelo menos um sufixo, como .com";
            } else if (Arrays.stream(partes).anyMatch(String::isEmpty)) {
                problema = "o dominio '" + dominio + "' tem ponto no inicio, no fim ou repetido";
            } else {
                problema = "a terminacao do dominio '" + dominio + "' deve ter 2 ou mais letras (ex.: .com, .br)";
            }
        }
        return "Email invalido: " + problema + ". " + FORMATO_EMAIL;
    }

    public static String user(String user) {
        if (USER.matcher(user).matches()) {
            return null;
        }
        String problema;
        if (user.isEmpty()) {
            problema = "o usuario esta vazio";
        } else if (user.length() > 30) {
            problema = "o usuario tem " + user.length() + " caracteres (maximo 30)";
        } else {
            problema = "o usuario contem caracteres nao permitidos";
        }
        return "Usuario invalido: " + problema + ". " + FORMATO_USER;
    }

    public static String password(String password) {
        if (PASSWORD.matcher(password).matches()) {
            return null;
        }
        String problema;
        if (password.isEmpty()) {
            problema = "a senha esta vazia";
        } else if (password.length() > 20) {
            problema = "a senha tem " + password.length() + " caracteres (maximo 20)";
        } else {
            problema = "a senha contem caracteres nao permitidos";
        }
        return "Senha invalida: " + problema + ". " + FORMATO_PASSWORD;
    }
}
