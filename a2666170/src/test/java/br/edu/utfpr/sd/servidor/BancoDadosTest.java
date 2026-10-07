package br.edu.utfpr.sd.servidor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Testa a leitura da tabela usuarios usada pela aba "Banco de dados" do servidor. */
class BancoDadosTest {

    @TempDir
    Path pasta;

    @Test
    void lerTabelaUsuarios() throws Exception {
        BancoDados banco = new BancoDados(pasta.resolve("teste.db").toString());
        assertTrue(banco.lerTabelaUsuarios().linhas().isEmpty());

        banco.cadastrar("joao", "joao@email.com", "senha123");
        banco.cadastrar("maria", "maria@email.com", "senha456");
        BancoDados.Tabela tabela = banco.lerTabelaUsuarios();

        assertEquals(List.of("id", "user", "email", "senha_hash", "senha_salt", "role", "created_at"), tabela.colunas());
        assertEquals(2, tabela.linhas().size());
        assertEquals(List.of("1", "joao", "joao@email.com"), tabela.linhas().get(0).subList(0, 3));
        assertEquals("user", tabela.linhas().get(1).get(5));

        // Reflete o banco no momento da leitura: apos a exclusao, a linha some
        banco.removerUsuario(1);
        assertEquals(List.of("maria"), tabela(banco.lerTabelaUsuarios(), 1));
    }

    private static List<String> tabela(BancoDados.Tabela tabela, int coluna) {
        return tabela.linhas().stream().map(l -> l.get(coluna)).toList();
    }
}
