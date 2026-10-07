package br.edu.utfpr.sd.comum;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ValidacaoCadastroTest {

    @Test
    void aceitaDadosNoFormatoDoProtocolo() {
        assertNull(ValidacaoCadastro.email("joao.silva@email.com"));
        assertNull(ValidacaoCadastro.email("atos@utfpr.edu.br"));
        assertNull(ValidacaoCadastro.email("maria.souza@alunos.utfpr.edu.br"));
        assertNull(ValidacaoCadastro.email("at_os-1@meu-provedor.com.br"));
        assertNull(ValidacaoCadastro.user("atos"));
        assertNull(ValidacaoCadastro.password("atos"));
        assertNull(ValidacaoCadastro.password("Senha123"));
    }

    @Test
    void explicaEmailComMaiusculas() {
        assertTrue(ValidacaoCadastro.email("Joao.Silva@email.com").contains("letras maiusculas"));
        assertTrue(ValidacaoCadastro.email("joao@EMAIL.COM").contains("letras maiusculas"));
    }

    @Test
    void explicaOutrosErrosDeEmail() {
        assertTrue(ValidacaoCadastro.email("").contains("vazio"));
        assertTrue(ValidacaoCadastro.email("atos.email.com").contains("exatamente um @"));
        assertTrue(ValidacaoCadastro.email("@email.com").contains("antes do @"));
        assertTrue(ValidacaoCadastro.email("at+os@email.com").contains("antes do @"));
        assertTrue(ValidacaoCadastro.email("atos@em_ail.com").contains("depois do @"));
        assertTrue(ValidacaoCadastro.email("atos@localhost").contains("pelo menos um sufixo"));
        assertTrue(ValidacaoCadastro.email("atos@email..com").contains("repetido"));
        assertTrue(ValidacaoCadastro.email("atos@email.c").contains("2 ou mais letras"));
        assertTrue(ValidacaoCadastro.email("atos@email.c0m").contains("2 ou mais letras"));
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
