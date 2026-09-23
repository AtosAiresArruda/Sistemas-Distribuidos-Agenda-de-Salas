package br.edu.utfpr.sd.comum;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ValidacaoCadastroTest {

    @Test
    void aceitaDadosNoFormatoDoProtocolo() {
        assertNull(ValidacaoCadastro.email("joao.silva@email.com"));
        assertNull(ValidacaoCadastro.email("atos@utfpr.edu.br"));
        assertNull(ValidacaoCadastro.user("atos"));
        assertNull(ValidacaoCadastro.password("atos"));
        assertNull(ValidacaoCadastro.password("Senha123"));
    }

    @Test
    void explicaEmailComSufixosDemais() {
        String problema = ValidacaoCadastro.email("atos@alunos.utfpr.edu.br");
        assertTrue(problema.contains("tem 3 sufixos"), problema);
        assertTrue(problema.contains("no maximo 2"), problema);
    }

    @Test
    void explicaOutrosErrosDeEmail() {
        assertTrue(ValidacaoCadastro.email("").contains("vazio"));
        assertTrue(ValidacaoCadastro.email("atos.email.com").contains("exatamente um @"));
        assertTrue(ValidacaoCadastro.email("@email.com").contains("antes do @"));
        assertTrue(ValidacaoCadastro.email("at_os@email.com").contains("antes do @"));
        assertTrue(ValidacaoCadastro.email("atos@localhost").contains("pelo menos um sufixo"));
        assertTrue(ValidacaoCadastro.email("atos@email.c0m").contains("apenas letras"));
    }

    @Test
    void explicaErrosDeUsuarioESenha() {
        assertTrue(ValidacaoCadastro.user("").contains("vazio"));
        assertTrue(ValidacaoCadastro.user("atos1").contains("caracteres nao permitidos"));
        assertTrue(ValidacaoCadastro.user("a".repeat(31)).contains("maximo 30"));
        assertTrue(ValidacaoCadastro.password("").contains("vazia"));
        assertTrue(ValidacaoCadastro.password("sen ha").contains("caracteres nao permitidos"));
        assertTrue(ValidacaoCadastro.password("a".repeat(21)).contains("maximo 20"));
    }
}
