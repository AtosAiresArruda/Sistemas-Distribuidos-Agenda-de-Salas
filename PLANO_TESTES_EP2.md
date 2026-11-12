# Plano de testes — Entrega Parcial 2 (Administrador e Salas)

Protocolo de referência: **v2.9.1** (`asyncapi.yaml`, 05/11/2026). Execução: 2026-11-04, revisada em 2026-11-11 (v2.9.1), branch `dev`.

## Como rodar

Na pasta `a2666170/`:

```bash
mvn test
```
Servidor e protocolo (71 testes, 11 deles das regras 2.9.x em `Protocolo291Test`). Não abre janelas.

```bash
mvn test -Dgui=true -Dtest=ClienteGuiTest
```
Cliente JavaFX (10 testes). Abre janelas do cliente durante uns 30 s.

Demonstração visual (abre as janelas reais do Servidor e do Cliente lado a lado e executa os itens a–o e os erros, com pausa de 1,5 s por passo, conferindo o retorno de cada tela). Rode numa pasta vazia, porque o servidor cria `agenda.db` na pasta de trabalho:

```bash
mvn test-compile dependency:build-classpath -Dmdep.outputFile=cp.txt
```

```bash
mkdir -p /tmp/demo && cd /tmp/demo && java -cp "$OLDPWD/target/test-classes:$OLDPWD/target/classes:$(cat $OLDPWD/cp.txt)" br.edu.utfpr.sd.DemoEp2 23456 capturas 1500
```

## Estratégia

| Direção | Como é testado | Arquivo |
|---|---|---|
| Cliente → Servidor (cliente monta a mensagem certa) | O `ClienteApp` real é aberto e operado pelo teste; um monitor no servidor captura o JSON recebido e confere chaves, valores e `op` como primeira chave. | `cliente/ClienteGuiTest` |
| Servidor → Cliente (cliente reage certo à resposta) | A tela é conferida depois de cada resposta do servidor real; um servidor falso manda respostas fora do padrão. | `cliente/ClienteGuiTest` |
| Servidor recebe e responde (fluxo principal) | Um cliente TCP de teste executa cada item e lê o resultado de volta. | `servidor/FluxoAdminTest` |
| Servidor sob ataque | JSONs maliciosos, tokens falsos, identidade falsa, injeção, corrida. | `servidor/InvasaoAdminTest` |
| Erros do protocolo (item h) | Cada status com a `message` exata. | `AdminTest`, `SalasTest`, `InvasaoAdminTest`, `Protocolo291Test` |
| Regras novas da v2.9.x e fronteiras das regex | Token ausente, maiúsculas, tamanhos máximos, `resources` `[]`/`["0"]`, ordem de validação, datas, filtros, resposta > 8192 bytes. | `servidor/Protocolo291Test` |
| Responsividade das duas interfaces | Janelas reais lado a lado, passo a passo, com capturas. | `DemoEp2` |

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
| Cadastrar ou renomear para `admin` / `admin@admin.com` | 409 (`ADMIN` → 400: maiúscula não é convertida, v2.9) | OK |
| `delete_user` com `target_user: "admin"` | Só a conta do dono do token sai | OK |
| Token ausente, forjado (64 hex), vazio, maiúsculo, curto, longo, com espaço, `null`, número, booleano, array, objeto `{"$ne":""}`, `' OR '1'='1` | 401 (forjado) ou 400 (ausente e formato, v2.9) | OK |
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
| `read_room` sem `slots` (servidor de colega) | "nao informado pelo servidor" | OK |
| 403 | Mostra o motivo e mantém o token | OK |
| 401 | Descarta o token, volta ao Login e remove as abas de admin | OK |
| `read_user` com `role: "user"` numa sessão admin | Remove as abas de admin | OK |
| Conexão fechada sem resposta (regra 1.11) | Reconecta e reenvia a mesma requisição, com o mesmo token | OK |

## Achados

1. **Token de outra pessoa derruba a sessão dela.** Pela regra 1.11, o token vale em qualquer conexão. Pela decisão do projeto, a queda de qualquer conexão que usou o token é logout. Então quem tiver o token de outra pessoa pode usá-lo e, ao desconectar, encerrar a sessão do dono. Isso exige já ter o token, então não dá poder novo. O comportamento está documentado em `InvasaoAdminTest.tokenUsadoEmOutraConexaoCaiJuntoComEla`. **Decisão (2026-11-04): manter como está.**
2. **Chave repetida no JSON:** o Gson fica com o último valor. Não é uma brecha, porque a validação acontece sobre o valor que fica.
3. **Avisos de rede no cliente** ("nao e um JSON valido", "conexao caiu") aparecem na aba que está aberta. No uso normal essa é a aba da operação.
4. **Listas longas** (`admin_list_users` com cerca de 100 usuários) passam de 8192 bytes. **Resolvido na v2.9.1:** respostas não têm limite; o cliente agora lê até o `\n` sem limite (`Protocolo291Test.respostaMaiorQue8192BytesChegaInteira`).

## Roteiro manual (o que a automação não cobre)

1. **Aparência:** a grade de horários (6 × 17) e as tabelas cabem na janela e são legíveis.
2. **Interoperabilidade:** o nosso cliente no servidor de um colega e o cliente de um colega no nosso servidor, repetindo os itens a–g.
3. **Regra 1.11 com o servidor real:** deixar o cliente parado por mais de 300 s e clicar numa operação. O esperado é reconectar, reenviar, receber 401 (queda de conexão = logout) e voltar ao Login.
4. **Testador oficial** do protocolo (`node scripts/testar-servidor.mjs IP PORTA`): falta o Node.js nesta máquina e falta autorizar o download.

## Revisão para o protocolo 2.9.1 (2026-11-11)

| Regra | O que mudou no código | Teste |
|---|---|---|
| 2.10 / 4.2 | Token ausente → 400 com a message de formato da operação ("Token em formato invalido" em `logout`, `read_user`, `admin_list_users`, `delete_user`) | `tokenAusenteResponde400ComAMessageDaOperacao` |
| 2.12 | Servidor não converte mais `user`, `email`, `target_user` para minúsculas: maiúscula → 400 | `maiusculasEmUserEEmailRespondem400` |
| Dicionário | `email` ≤ 100 caracteres; `date` com mês 01–12 e dia 01–31 | `fronteirasDoCadastro`, `filtrosEDatas` |
| 2.13 | `update_room` sem nada para mudar → 400 "Nenhum dado para atualizar" | `resourcesENadaParaAtualizarNoUpdateRoom` |
| 9.7 | `resources`: opcional no `update_room`, `[]` mantém, `["0"]` apaga; itens com regex e diferentes de `"0"` (também no `create_room`) | idem; cliente: caixa "Apagar todos os recursos" |
| 9.6 | 409 "Capacidade menor que participantes de reservas ativas" | `capacidadeAbaixoDeReservaAtiva` |
| 9.2 | `list_reservations` com data inexistente → 400 | `filtrosEDatas` |
| 2.15 / 3.4 / seção 4 | Já atendidas; agora com teste | `valorIgualAoAtualEAlteracao`, `alterarUsuarioPeloAdminNaoEncerraASessao`, `ordemDeValidacao` |
| 1.6 | Cliente lê respostas de qualquer tamanho | `respostaMaiorQue8192BytesChegaInteira` |
