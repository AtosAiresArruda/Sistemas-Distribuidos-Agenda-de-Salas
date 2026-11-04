package br.edu.utfpr.sd.servidor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tokens das sessoes ativas, separados em duas listas: usuarios comuns e administradores.
 * Um token ativo esta em exatamente uma das listas. Todo token e validado por elas, e so a
 * lista de admin (whitelist, protocolo 3.14) libera as operacoes de administrador.
 * As alteracoes sao feitas pelo BancoDados, junto com a gravacao no banco.
 */
public class ListasTokens {

    private final Set<String> usuarios = ConcurrentHashMap.newKeySet();
    private final Set<String> admins = ConcurrentHashMap.newKeySet();

    /** Coloca o token na lista do perfil ("admin" ou "user"). */
    public void adicionar(String token, String role) {
        remover(token);
        ("admin".equals(role) ? admins : usuarios).add(token);
    }

    public void remover(String token) {
        usuarios.remove(token);
        admins.remove(token);
    }

    /** Promocao ou rebaixamento: o token passa para a lista do novo perfil, se estiver ativo. */
    public void mover(String token, String role) {
        if (ativo(token)) {
            adicionar(token, role);
        }
    }

    public boolean ativo(String token) {
        return usuarios.contains(token) || admins.contains(token);
    }

    public boolean ehAdmin(String token) {
        return admins.contains(token);
    }

    public List<String> tokensUsuarios() {
        return List.copyOf(usuarios);
    }

    public List<String> tokensAdmins() {
        return List.copyOf(admins);
    }
}
