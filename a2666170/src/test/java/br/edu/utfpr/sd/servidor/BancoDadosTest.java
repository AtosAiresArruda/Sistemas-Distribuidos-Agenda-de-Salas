package br.edu.utfpr.sd.servidor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Testa o banco: tabela usada pela interface, administrador padrao e listas de tokens. */
class BancoDadosTest {

    @TempDir
    Path pasta;

    @Test
    void lerTabelaUsuarios() throws Exception {
        BancoDados banco = new BancoDados(arquivo());
        // O administrador padrao ja existe ao abrir o banco
        assertEquals(List.of("admin"), tabela(banco.lerTabelaUsuarios(), 1));

        banco.cadastrar("joao", "joao@email.com", "senha123");
        banco.cadastrar("maria", "maria@email.com", "senha456");
        BancoDados.Tabela tabela = banco.lerTabelaUsuarios();

        assertEquals(List.of("id", "user", "email", "senha_hash", "senha_salt", "role", "created_at"), tabela.colunas());
        assertEquals(3, tabela.linhas().size());
        assertEquals(List.of("2", "joao", "joao@email.com"), tabela.linhas().get(1).subList(0, 3));
        assertEquals("user", tabela.linhas().get(2).get(5));

        // Reflete o banco no momento da leitura: apos a exclusao, a linha some
        banco.removerUsuario(2);
        assertEquals(List.of("admin", "maria"), tabela(banco.lerTabelaUsuarios(), 1));
    }

    @Test
    void adminPadraoVoltaASerAdminAoReiniciar() throws Exception {
        BancoDados banco = new BancoDados(arquivo());
        banco.cadastrar("maria", "maria@email.com", "senha123");
        banco.atualizarComoAdmin("maria", null, null, "admin");
        assertEquals(BancoDados.Resultado.OK, banco.atualizarComoAdmin("admin", null, "trocada1", "user"));
        assertEquals("user", banco.buscarPorUser("admin").role());

        // Nova inicializacao: o role volta a ser admin e a senha nao muda
        BancoDados reiniciado = new BancoDados(arquivo());
        assertEquals("admin", reiniciado.buscarPorUser("admin").role());
        assertNotNull(reiniciado.autenticar(BancoDados.ADMIN_EMAIL, "trocada1"));
        assertNull(reiniciado.autenticar(BancoDados.ADMIN_EMAIL, BancoDados.ADMIN_SENHA));
    }

    @Test
    void contaAntigaComUserAdminERemovida() throws Exception {
        // Banco da EP-1: o user "admin" pertence a um usuario comum com outro email
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + arquivo());
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE usuarios (id INTEGER PRIMARY KEY AUTOINCREMENT, user TEXT NOT NULL UNIQUE, "
                    + "email TEXT NOT NULL UNIQUE, senha_hash TEXT NOT NULL, senha_salt TEXT NOT NULL, "
                    + "role TEXT NOT NULL, created_at TEXT NOT NULL)");
            st.execute("INSERT INTO usuarios (user, email, senha_hash, senha_salt, role, created_at) "
                    + "VALUES ('admin', 'antigo@email.com', 'x', 'y', 'user', '2026-09-01 10:00:00')");
            st.execute("INSERT INTO usuarios (user, email, senha_hash, senha_salt, role, created_at) "
                    + "VALUES ('joao', 'joao@email.com', 'x', 'y', 'user', '2026-09-01 10:00:00')");
        }

        BancoDados banco = new BancoDados(arquivo());
        assertEquals(List.of("joao", "admin"), tabela(banco.lerTabelaUsuarios(), 1));
        assertEquals(List.of("joao@email.com", BancoDados.ADMIN_EMAIL), tabela(banco.lerTabelaUsuarios(), 2));
        assertEquals("admin", banco.buscarPorUser("admin").role());
        assertNotNull(banco.autenticar(BancoDados.ADMIN_EMAIL, BancoDados.ADMIN_SENHA));
    }

    @Test
    void tokenFicaNaListaDoPerfil() throws Exception {
        BancoDados banco = new BancoDados(arquivo());
        banco.cadastrar("joao", "joao@email.com", "senha123");
        String tokenJoao = banco.abrirSessao(banco.buscarPorUser("joao").id());
        String tokenAdmin = banco.abrirSessao(banco.buscarPorUser("admin").id());

        assertFalse(banco.tokenEhAdmin(tokenJoao));
        assertTrue(banco.tokenEhAdmin(tokenAdmin));
        assertEquals(List.of("joao"), banco.sessoesUsuarios().stream().map(BancoDados.Sessao::user).toList());
        assertEquals(List.of("admin"), banco.sessoesAdmins().stream().map(BancoDados.Sessao::user).toList());

        // Logout tira o token das listas
        banco.invalidarToken(tokenAdmin);
        assertFalse(banco.tokenEhAdmin(tokenAdmin));
        assertTrue(banco.sessoesAdmins().isEmpty());
    }

    @Test
    void reiniciarInvalidaAsSessoes() throws Exception {
        BancoDados banco = new BancoDados(arquivo());
        String token = banco.abrirSessao(banco.buscarPorUser("admin").id());
        assertNotNull(banco.usuarioDoToken(token));

        BancoDados reiniciado = new BancoDados(arquivo());
        assertNull(reiniciado.usuarioDoToken(token));
        // Sem sessao presa: o login seguinte abre uma sessao nova
        assertNotNull(reiniciado.abrirSessao(reiniciado.buscarPorUser("admin").id()));
    }

    @Test
    void horariosDaEp22ViramCodigoDaUtfpr() throws Exception {
        // Banco da EP-2.2: horarios como (day, slot) na tabela sala_horarios
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + arquivo());
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE salas (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, "
                    + "capacity INTEGER NOT NULL, location TEXT NOT NULL, resources TEXT NOT NULL, room_status TEXT NOT NULL)");
            st.execute("INSERT INTO salas (name, capacity, location, resources, room_status) "
                    + "VALUES ('Sala A', 10, 'Bloco A', '[]', 'active')");
            st.execute("CREATE TABLE sala_horarios (sala_id INTEGER NOT NULL, day TEXT NOT NULL, slot TEXT NOT NULL, "
                    + "PRIMARY KEY (sala_id, day, slot))");
            st.execute("INSERT INTO sala_horarios VALUES (1, 'sabado', 'N5'), (1, 'segunda', 'M1'), (1, 'quarta', 'T3')");
        }

        BancoDados banco = new BancoDados(arquivo());
        assertEquals(List.of("2M1", "4T3", "7N5"), banco.buscarSala(1).slots());
        // A tabela antiga sai; reabrir o banco nao repete a migracao
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + arquivo());
             Statement st = c.createStatement()) {
            assertFalse(st.executeQuery("SELECT 1 FROM sqlite_master WHERE name = 'sala_horarios'").next());
        }
        assertEquals(List.of("2M1", "4T3", "7N5"), new BancoDados(arquivo()).buscarSala(1).slots());
    }

    private String arquivo() {
        return pasta.resolve("teste.db").toString();
    }

    private static List<String> tabela(BancoDados.Tabela tabela, int coluna) {
        return tabela.linhas().stream().map(l -> l.get(coluna)).toList();
    }
}
