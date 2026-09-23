package br.edu.utfpr.sd.servidor;

import br.edu.utfpr.sd.comum.ConexaoJson.Direcao;

/**
 * Observa as threads de atendimento. Os metodos sao chamados pela propria thread do cliente,
 * entao quem atualiza uma interface grafica deve repassar o evento para a thread da interface.
 */
public interface MonitorServidor {

    /** Monitor que ignora todos os eventos (servidor de console e testes). */
    MonitorServidor NENHUM = new MonitorServidor() {
    };

    default void conexaoAberta(TratadorCliente tratador) {
    }

    default void mensagem(TratadorCliente tratador, Direcao direcao, String texto) {
    }

    /** @param motivo descricao de por que a conexao terminou */
    default void conexaoEncerrada(TratadorCliente tratador, String motivo) {
    }
}
