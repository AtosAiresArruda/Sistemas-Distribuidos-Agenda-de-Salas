# Plano de testes — Entrega Parcial 2 (Administrador e Salas)

Protocolo de referência: v2.6.0 (`asyncapi.yaml`, 22/10/2026). Execução: 2026-11-04, branch `dev`.

## Como rodar

Na pasta `a2666170/`:

```bash
mvn test
```
Servidor e protocolo (54 testes). Não abre janelas.

```bash
mvn test -Dgui=true -Dtest=ClienteGuiTest
```
Cliente JavaFX (9 testes). Abre janelas do cliente durante uns 30 s.

## Estratégia

| Direção | Como é testado | Arquivo |
|---|---|---|
| Cliente → Servidor (cliente monta a mensagem certa) | O `ClienteApp` real é aberto e operado pelo teste; um monitor no servidor captura o JSON recebido e confere chaves, valores e `op` como primeira chave. | `cliente/ClienteGuiTest` |
| Servidor → Cliente (cliente reage certo à resposta) | A tela é conferida depois de cada resposta do servidor real; um servidor falso manda respostas fora do padrão. | `cliente/ClienteGuiTest` |
| Servidor recebe e responde (fluxo principal) | Um cliente TCP de teste executa cada item e lê o resultado de volta. | `servidor/FluxoAdminTest` |
| Servidor sob ataque | JSONs maliciosos, tokens falsos, identidade falsa, injeção, corrida. | `servidor/InvasaoAdminTest` |
| Erros do protocolo (item h) | Cada status com a `message` exata. | `AdminTest`, `SalasTest`, `InvasaoAdminTest` |

## Rastreabilidade: item avaliado → teste

| Item | Requisito | Testes | Resultado |
|---|---|---|---|
| a | Cliente: ADM lista todos os usuários | `ClienteGuiTest.itemA_admListaTodosOsUsuarios` | OK |
| b | Cliente: ADM atualiza outro usuário | `ClienteGuiTest.itemB_admAtualizaOutroUsuario` | OK |
| c | Cliente: ADM exclui usuário comum | `ClienteGuiTest.itemC_admExcluiUsuarioComum` | OK |
| d–g | Cliente: ADM cadastra, lê, atualiza e remove sala (nome, dias, horários, capacidade, status, quem reservou) | `ClienteGuiTest.itensDaG_admCadastraLeAtualizaERemoveSala` | OK |
| h | Servidor: mensagens de erro corretas | `AdminTest` (9), `SalasTest` (6), `InvasaoAdminTest` (12), `ServidorTest.errosDeProtocolo` | OK |
| i | Servidor: lista todos os usuários | `FluxoAdminTest.itemI_listaTodosOsUsuarios`, `AdminTest.adminListUsers` | OK |
| j | Servidor: atualiza outro usuário | `FluxoAdminTest.itemJ_atualizaDadosDeOutroUsuario`, `AdminTest.adminUpdateUserAlteraOutroUsuario` | OK |
| k | Servidor: exclui usuário comum | `FluxoAdminTest.itemK_excluiCadastroDeUsuarioComum`, `AdminTest.adminDeleteUserApagaCadastroESessao` | OK |
| l | Servidor: cadastra sala | `FluxoAdminTest.itemL_cadastraSalaComNomeDiasHorariosECapacidade`, `SalasTest.createRoom` | OK |
| m | Servidor: lê salas e quem reservou | `FluxoAdminTest.itemM_leSalasHorariosCapacidadeStatusEQuemReservou`, `SalasTest.readRoom/listRooms/listReservations` | OK |
| n | Servidor: atualiza sala | `FluxoAdminTest.itemN_atualizaNomeHorariosCapacidadeEStatus`, `SalasTest.updateRoom` | OK |
| o | Servidor: remove sala | `FluxoAdminTest.itemO_removeSalaDosRegistros`, `SalasTest.deleteRoom` | OK |

Também testado no cliente:
- O login leva cada perfil à sua interface.
- O usuário comum não tem as abas de admin. O JSON manual de admin enviado por ele é recusado e a sessão continua.

## Tentativas de invasão (`InvasaoAdminTest`)

| Ataque | Esperado | Resultado |
|---|---|---|
| Usuário comum envia `"user":"admin"`, `"role":"admin"`, `"email":"admin@admin.com"`, `"is_admin"` etc. nas 8 operações de admin | 403 "Permissao insuficiente"; nada muda | OK |
| `role` no `register`, `login` e `update_user` | Ignorado: a conta continua `user` | OK |
| Cadastrar ou renomear para `admin` / `ADMIN` / `admin@admin.com` | 409 | OK |
| `delete_user` com `target_user: "admin"` | Só a conta do dono do token sai | OK |
| Token ausente, forjado (64 hex), vazio, maiúsculo, curto, longo, com espaço, `null`, número, booleano, array, objeto `{"$ne":""}`, `' OR '1'='1` | 401 (ausente/forjado) ou 400 | OK |
| Token de admin depois de rebaixamento, remoção da conta ou logout | 403 / 401 na hora | OK |
| Chave repetida (`token` duas vezes, `op` duas vezes, `role` duas vezes) | Vale o último valor; continua 403 | OK |
| Injeção de SQL, homoglifo cirílico, NUL, `*`, `%` em `target_user`, `name`, `room_id`, `min_capacity`, `scope` | 400; banco intacto | OK |
| `op` em maiúsculas, com espaço, com hífen, como array; dois JSONs na linha; linha > 8192 bytes | `error` 400; conexão continua | OK |
| Dois admins se rebaixando ao mesmo tempo | Só um consegue; sempre sobra 1 admin | OK |
| Último admin saindo por `delete_user`, `admin_delete_user` ou rebaixamento | 403 "Nao e possivel remover o ultimo administrador" | OK |

## Servidor → Cliente com servidor falso (`ClienteGuiTest`)

| Resposta do servidor | Esperado no cliente | Resultado |
|---|---|---|
| `users` que não é lista | Tabela vazia, sem travar | OK |
| Linha que não é JSON | Aviso "Resposta do servidor nao e um JSON valido." | OK |
| `read_room` sem `schedule` (servidor de colega) | "nao informado pelo servidor" | OK |
| 403 | Mostra o motivo e mantém o token | OK |
| 401 | Descarta o token, volta ao Login e remove as abas de admin | OK |
| `read_user` com `role: "user"` numa sessão admin | Remove as abas de admin | OK |
| Conexão fechada sem resposta (regra 1.11) | Reconecta e reenvia a mesma requisição, com o mesmo token | OK |

## Achados

1. **Token de outra pessoa derruba a sessão dela.** Pela regra 1.11, o token vale em qualquer conexão. Pela decisão do projeto, a queda de qualquer conexão que usou o token é logout. Então quem tiver o token de outra pessoa pode usá-lo e, ao desconectar, encerrar a sessão do dono. Isso exige já ter o token, então não dá poder novo. O comportamento está documentado em `InvasaoAdminTest.tokenUsadoEmOutraConexaoCaiJuntoComEla`. **Decisão (2026-11-04): manter como está.**
2. **Chave repetida no JSON:** o Gson fica com o último valor. Não é uma brecha, porque a validação acontece sobre o valor que fica.
3. **Avisos de rede no cliente** ("nao e um JSON valido", "conexao caiu") aparecem na aba que está aberta. No uso normal essa é a aba da operação.
4. **Listas longas** (`admin_list_users` com cerca de 100 usuários, `list_rooms` com muitos horários) podem passar de 8192 bytes. O protocolo não tem paginação. É um risco conhecido, sem efeito na demonstração.

## Roteiro manual (o que a automação não cobre)

1. **Aparência:** a grade de horários (6 × 17) e as tabelas cabem na janela e são legíveis.
2. **Interoperabilidade:** o nosso cliente no servidor de um colega e o cliente de um colega no nosso servidor, repetindo os itens a–g.
3. **Regra 1.11 com o servidor real:** deixar o cliente parado por mais de 300 s e clicar numa operação. O esperado é reconectar, reenviar, receber 401 (queda de conexão = logout) e voltar ao Login.
4. **Testador oficial** do protocolo (`node scripts/testar-servidor.mjs IP PORTA`): falta o Node.js nesta máquina e falta autorizar o download.
