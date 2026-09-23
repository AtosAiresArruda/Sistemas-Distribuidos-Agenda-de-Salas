package br.edu.utfpr.sd.servidor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Persistencia em SQLite. Uma unica conexao compartilhada; todos os metodos sao
 * synchronized, entao cada operacao (verificar + gravar) e atomica entre as threads dos clientes.
 */
public class BancoDados {

    // Protocolo 3.3: token expira apos 30 minutos sem uso
    private static final long VALIDADE_TOKEN_MS = 30 * 60 * 1000L;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SecureRandom random = new SecureRandom();
    private final Connection conexao;

    public record Usuario(long id, String user, String email, String role, String createdAt) {
    }

    public BancoDados(String arquivo) throws SQLException {
        conexao = DriverManager.getConnection("jdbc:sqlite:" + arquivo);
        criarTabelas();
    }

    private void criarTabelas() throws SQLException {
        try (Statement st = conexao.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS usuarios (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        user       TEXT NOT NULL UNIQUE,
                        email      TEXT NOT NULL UNIQUE,
                        senha_hash TEXT NOT NULL,
                        senha_salt TEXT NOT NULL,
                        role       TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )""");
            // Tokens invalidados continuam na tabela para nunca serem reutilizados (protocolo 3.4)
            st.execute("""
                    CREATE TABLE IF NOT EXISTS tokens (
                        token      TEXT PRIMARY KEY,
                        usuario_id INTEGER NOT NULL,
                        ultimo_uso INTEGER NOT NULL,
                        ativo      INTEGER NOT NULL
                    )""");
        }
    }

    /** Cadastra um usuario comum. Retorna false se o user ou o email ja existirem. */
    public synchronized boolean cadastrar(String user, String email, String senha) throws SQLException {
        try (PreparedStatement ps = conexao.prepareStatement("SELECT 1 FROM usuarios WHERE user = ? OR email = ?")) {
            ps.setString(1, user);
            ps.setString(2, email);
            if (ps.executeQuery().next()) {
                return false;
            }
        }
        String salt = HexFormat.of().formatHex(bytesAleatorios(16));
        try (PreparedStatement ps = conexao.prepareStatement(
                "INSERT INTO usuarios (user, email, senha_hash, senha_salt, role, created_at) VALUES (?, ?, ?, ?, 'user', ?)")) {
            ps.setString(1, user);
            ps.setString(2, email);
            ps.setString(3, hash(salt, senha));
            ps.setString(4, salt);
            ps.setString(5, LocalDateTime.now(FUSO).format(FORMATO_DATA_HORA));
            ps.executeUpdate();
        }
        return true;
    }

    /** Retorna o usuario se email e senha conferem; null caso contrario. */
    public synchronized Usuario autenticar(String email, String senha) throws SQLException {
        try (PreparedStatement ps = conexao.prepareStatement("SELECT id FROM usuarios WHERE email = ?")) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            long id = rs.getLong("id");
            return senhaConfere(id, senha) ? buscarUsuario(id) : null;
        }
    }

    public synchronized boolean senhaConfere(long usuarioId, String senha) throws SQLException {
        try (PreparedStatement ps = conexao.prepareStatement("SELECT senha_hash, senha_salt FROM usuarios WHERE id = ?")) {
            ps.setLong(1, usuarioId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && MessageDigest.isEqual(
                    rs.getString("senha_hash").getBytes(StandardCharsets.UTF_8),
                    hash(rs.getString("senha_salt"), senha).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Abre uma sessao e devolve o novo token.
     * Retorna null se o usuario ja tiver uma sessao ativa (protocolo 3.5).
     */
    public synchronized String abrirSessao(long usuarioId) throws SQLException {
        if (possuiSessaoAtiva(usuarioId)) {
            return null;
        }
        String token;
        do {
            token = HexFormat.of().formatHex(bytesAleatorios(32));
        } while (tokenJaEmitido(token));

        try (PreparedStatement ps = conexao.prepareStatement(
                "INSERT INTO tokens (token, usuario_id, ultimo_uso, ativo) VALUES (?, ?, ?, 1)")) {
            ps.setString(1, token);
            ps.setLong(2, usuarioId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
        return token;
    }

    /**
     * Valida o token e renova sua validade (protocolo 3.3).
     * Retorna o dono do token, ou null se o token nao existir, estiver expirado ou invalidado.
     */
    public synchronized Usuario usuarioDoToken(String token) throws SQLException {
        Long usuarioId = usuarioDeTokenAtivo(token);
        if (usuarioId == null) {
            return null;
        }
        try (PreparedStatement ps = conexao.prepareStatement("UPDATE tokens SET ultimo_uso = ? WHERE token = ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, token);
            ps.executeUpdate();
        }
        return buscarUsuario(usuarioId);
    }

    /** Invalida o token. Retorna false se ele ja nao era valido. */
    public synchronized boolean invalidarToken(String token) throws SQLException {
        if (usuarioDeTokenAtivo(token) == null) {
            return false;
        }
        try (PreparedStatement ps = conexao.prepareStatement("UPDATE tokens SET ativo = 0 WHERE token = ?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        }
        return true;
    }

    /**
     * Atualiza user e/ou senha; null significa "nao alterar".
     * Retorna false se o novo user ja pertencer a outro usuario.
     */
    public synchronized boolean atualizar(long usuarioId, String novoUser, String novaSenha) throws SQLException {
        if (novoUser != null) {
            try (PreparedStatement ps = conexao.prepareStatement("SELECT 1 FROM usuarios WHERE user = ? AND id <> ?")) {
                ps.setString(1, novoUser);
                ps.setLong(2, usuarioId);
                if (ps.executeQuery().next()) {
                    return false;
                }
            }
            try (PreparedStatement ps = conexao.prepareStatement("UPDATE usuarios SET user = ? WHERE id = ?")) {
                ps.setString(1, novoUser);
                ps.setLong(2, usuarioId);
                ps.executeUpdate();
            }
        }
        if (novaSenha != null) {
            String salt = HexFormat.of().formatHex(bytesAleatorios(16));
            try (PreparedStatement ps = conexao.prepareStatement(
                    "UPDATE usuarios SET senha_hash = ?, senha_salt = ? WHERE id = ?")) {
                ps.setString(1, hash(salt, novaSenha));
                ps.setString(2, salt);
                ps.setLong(3, usuarioId);
                ps.executeUpdate();
            }
        }
        return true;
    }

    /**
     * Remove o usuario e invalida seus tokens.
     * Retorna false se ele for o ultimo administrador.
     */
    public synchronized boolean removerUsuario(long usuarioId) throws SQLException {
        Usuario usuario = buscarUsuario(usuarioId);
        if (usuario != null && "admin".equals(usuario.role()) && contarAdmins() <= 1) {
            return false;
        }
        // TODO: cancelar as reservas futuras do usuario quando o CRUD de reservas existir
        try (PreparedStatement ps = conexao.prepareStatement("UPDATE tokens SET ativo = 0 WHERE usuario_id = ?")) {
            ps.setLong(1, usuarioId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conexao.prepareStatement("DELETE FROM usuarios WHERE id = ?")) {
            ps.setLong(1, usuarioId);
            ps.executeUpdate();
        }
        return true;
    }

    private Usuario buscarUsuario(long id) throws SQLException {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT id, user, email, role, created_at FROM usuarios WHERE id = ?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return new Usuario(rs.getLong("id"), rs.getString("user"), rs.getString("email"),
                    rs.getString("role"), rs.getString("created_at"));
        }
    }

    /** Dono do token se ele estiver ativo e dentro da validade; tokens vencidos sao desativados aqui. */
    private Long usuarioDeTokenAtivo(String token) throws SQLException {
        long usuarioId;
        long ultimoUso;
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT usuario_id, ultimo_uso FROM tokens WHERE token = ? AND ativo = 1")) {
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            usuarioId = rs.getLong("usuario_id");
            ultimoUso = rs.getLong("ultimo_uso");
        }
        if (System.currentTimeMillis() - ultimoUso > VALIDADE_TOKEN_MS) {
            desativarToken(token);
            return null;
        }
        return usuarioId;
    }

    private boolean possuiSessaoAtiva(long usuarioId) throws SQLException {
        List<String> tokens = new ArrayList<>();
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT token FROM tokens WHERE usuario_id = ? AND ativo = 1")) {
            ps.setLong(1, usuarioId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                tokens.add(rs.getString("token"));
            }
        }
        for (String token : tokens) {
            if (usuarioDeTokenAtivo(token) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean tokenJaEmitido(String token) throws SQLException {
        try (PreparedStatement ps = conexao.prepareStatement("SELECT 1 FROM tokens WHERE token = ?")) {
            ps.setString(1, token);
            return ps.executeQuery().next();
        }
    }

    private void desativarToken(String token) throws SQLException {
        try (PreparedStatement ps = conexao.prepareStatement("UPDATE tokens SET ativo = 0 WHERE token = ?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        }
    }

    private int contarAdmins() throws SQLException {
        try (Statement st = conexao.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM usuarios WHERE role = 'admin'");
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private byte[] bytesAleatorios(int quantidade) {
        byte[] bytes = new byte[quantidade];
        random.nextBytes(bytes);
        return bytes;
    }

    private static String hash(String salt, String senha) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(salt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha.digest(senha.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
