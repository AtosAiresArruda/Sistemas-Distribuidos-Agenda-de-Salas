# Sistema de Agendamento de Salas — Sistemas Distribuídos

Trabalho da disciplina de Sistemas Distribuídos (UTFPR). Sistema distribuído cliente/servidor de agendamento de salas, com CRUD de usuários e administradores.

## Stack
- Linguagem: **Java 26.0.2**.
- Comunicação cliente ↔ servidor: **JSON** sobre TCP, usando Gson (`a2666170/src/libs/gson-2.14.0.jar`). Os Dados contidos no JSON sempre são STRING
- Projeto IntelliJ em `a2666170/` (`a2666170.iml`, fontes em `a2666170/src`).
- Banco de dados: **SQlite**
- interface: **Javafx**
- Sistemas de arquivos: **maven**

## O que construir
1. **Cliente**: conecta e faz login em um servidor qualquer. O foco é logar no servidor **de um colega**, mas também deve funcionar com o meu próprio servidor.
2. **Servidor**: recebe login de usuários, processa agendamentos e demais operações e responde em JSON.

Cliente e servidor devem conseguir **enviar e responder todas** as mensagens definidas no protocolo, para interoperar com as implementações dos colegas.

## Código base
Os arquivos em `a2666170/src/servidor_eco_tcp_json/` são a base para o sistema e mostram o padrão de comunicação a seguir:
- `EchoServer_TCP_Thread_GSON_Server.java`: servidor TCP com uma thread por cliente, que troca JSON via Gson.
- `EchoServer_TCP_Thread_GSON_Client.java`: cliente TCP que envia e recebe JSON.

## Especificação — fonte principal
**Fonte principal do protocolo:** https://pedrosalvatori.github.io/protocolo-reserva-salas/
A página monta a documentação no navegador a partir de `asyncapi.yaml` (AsyncAPI 3.0). Para ler o conteúdo, baixe o arquivo direto: https://pedrosalvatori.github.io/protocolo-reserva-salas/asyncapi.yaml (o HTML da página sozinho não traz as mensagens).

Regras de consulta:
1. **Sempre consulte esse link primeiro** para qualquer informação do projeto: formato das mensagens, campos, regex, códigos de status, mensagens de erro e regras de negócio.
2. Se a informação **não estiver** no link, **não presuma**: pergunte ao usuário. Ele pode responder diretamente ou pedir que se consulte o Drive antigo (abaixo).
3. O Drive **não** é mais consultado por iniciativa própria, só quando o usuário pedir.

**Requisito crítico:** a especificação deve ser seguida **à risca, em todos os detalhes**: nomes de campos, valores, códigos de resposta e regras de negócio. O protocolo é alterado ao longo do semestre. **Antes de implementar ou alterar uma operação, releia o protocolo atualizado** e não presuma formatos.

### Decisões do usuário que divergem do protocolo
- **Queda de conexão = logout.** Qualquer encerramento da conexão (cliente fecha o app, processo morto, erro de I/O, timeout de inatividade de 300 s ou servidor fechando a conexão) invalida o token da sessão daquela conexão (remove da whitelist). Isso substitui a regra 1.9, que mantinha o token válido após o timeout. Pelo mesmo motivo, **reiniciar o servidor invalida todas as sessões** que ficaram ativas no banco. Vale para qualquer conexão que usou o token, não só a do login: quem usar o token de outra pessoa e desconectar encerra a sessão dela (achado 1 do `PLANO_TESTES_EP2.md`; decidido manter assim).
- **Admin padrão a cada inicialização** (mais forte que a regra 3.15, que só cria quando não há nenhum admin). Ao abrir o banco e a cada "Iniciar servidor":
  - conta `admin@admin.com` existe → volta a ter role `admin` (senha e user não mudam);
  - não existe, mas outra conta usa o user `admin` (ex.: banco da EP-1) → essa conta é **apagada** (cadastro, sessões e, quando existirem, reservas) e a conta padrão é criada;
  - nenhuma das duas → cria `admin` / `admin@admin.com` / senha `admin` / role `admin`.
- **Duas listas de tokens** (`ListasTokens`): usuários comuns e administradores. Todo token é validado por elas; só a lista de admin (whitelist da regra 3.14) libera as operações de admin. As contas ficam numa única tabela `usuarios` com `role`, então o `register` recusa (409) email ou user já usado por um usuário comum ou por um admin.
- **Horários das salas como flags (EP-2.2, extensão do protocolo).** Campo opcional `schedule: [{"day": "...", "slot": "..."}]` em `create_room`, `update_room`, `read_room` e `list_rooms`. `day` ∈ segunda, terca, quarta, quinta, sexta, sabado; `slot` ∈ M1–M6, T1–T6, N1–N5 (blocos fixos, sem hora em número). No `create_room`, ausente = sala sem horários; no `update_room`, ausente = mantém, presente = substitui a lista inteira. Par inválido ou repetido → 400 "Dados da sala em formato invalido". O cliente aceita resposta sem `schedule` (servidores dos colegas).

### Pontos que o protocolo não define (decididos com o usuário)
- `update_room` reduzindo `capacity` abaixo dos `participants` de uma reserva futura → 409 "Sala possui reservas ativas" (pendência 3 do protocolo).
- `delete_room` apaga junto as reservas **passadas** e os horários da sala; reserva ativa = `date` + `end_time` ainda não passou.
- `list_rooms` com o trio de filtros: sala `inactive` → `"available": "false"`. O `schedule` não entra no cálculo.
- Token ausente → 401 (pendência 1 do protocolo).
- **A revisar:** nome de sala "único" com comparação exata ("Sala A" ≠ "sala a"), como está descrito no protocolo.

### Drive antigo (SD-BCC) — só quando o usuário pedir
Índice em `INDEX.md`. Leia pelo conector do Google Drive, usando os IDs abaixo.

| Arquivo | ID | Conteúdo |
|---|---|---|
| Protocolo de troca de Mensagens (planilha, antiga) | `1CK27K-MR88xDL7VikYjhQYnJoeDnppd9rFUuso_ZXwg` | Versão antiga do protocolo. |
| Requisitos (documento) | `1hKXp-2MkBlhInwCq8vIMsbpZ92BujFues9ma3d-VwfI` | Requisitos funcionais e não funcionais, e as regras de negócio de cada operação. |
| IPs das VMs (planilha) | `1u1Oc7BuLEMcmyGXzp8YxMQd79LtMrRjfhIPrL4n6Xqw` | IPs das máquinas virtuais do laboratório. O login nelas exige credenciais do usuário: nunca as digite, peça que o usuário faça o login. |

## Entrega-Parcial-01
**Status: concluída (registrado em 2026-10-22).** O código entregue corresponde ao commit `9d06e38`; o zip enviado está em `entrega/a2666170.zip`.

EP-1 vale 2,0 pontos: 1,0 do cliente e 1,0 do servidor. O escopo é só o **CRUD do usuário comum, com login e logout**. O formato de cada mensagem segue o protocolo (fonte principal acima).

| Operação | Cliente | Pts | Servidor | Pts |
|---|---|---|---|---|
| Cadastro (C) | a) Enviar dados de cadastro de usuário comum | 0,2 | g) Receber e realizar o cadastro | 0,2 |
| Leitura (R) | b) Ler os dados do próprio cadastro | 0,2 | h) Enviar os dados do cadastro | 0,2 |
| Login | c) Enviar dados de login | 0,2 | i) Receber e tratar o pedido de login | 0,2 |
| Logout | d) Enviar dados de logout | 0,1 | j) Receber e tratar o pedido de logout | 0,1 |
| Atualização (U) | e) Atualizar o próprio cadastro | 0,2 | k) Atualizar o cadastro | 0,2 |
| Exclusão (D) | f) Apagar o próprio cadastro | 0,1 | l) Apagar o cadastro | 0,1 |

Condições obrigatórias da avaliação:
- O protocolo de troca de mensagens precisa ter sido entregue antes da avaliação, senão a entrega não é avaliada.
- O código-fonte precisa ter sido enviado até o momento da avaliação. Cada dia de atraso no envio desconta 0,5 ponto.
- Cliente e servidor devem **exibir no console todas as mensagens JSON enviadas e recebidas**.
- O servidor deve pedir a **porta** na inicialização.
- O cliente deve pedir o **IP** e a **porta** do servidor.
- Todo cliente deve trocar mensagens com qualquer servidor da turma, e todo servidor com qualquer cliente. Por isso o protocolo precisa ser seguido à risca.
- Nenhuma alteração no código é permitida durante a avaliação, nem para corrigir erro crítico. Teste tudo antes.

## Entrega-Parcial-02
Dividida em duas partes:
- **EP-2.1 — Administradores.** Status: implementada e commitada (`1a89147`, 2026-11-04). Login, `admin_list_users` e `admin_read_user` conferidos na interface; falta conferir update, delete e os erros.
- **EP-2.2 — Salas.** Status: implementada (2026-11-04), aguardando teste nas interfaces e commit. Inclui as tabelas `salas`, `sala_horarios` e `reservas` e o `list_reservations` (só leitura); o `create_reservation` fica para a entrega seguinte. No cliente, a aba Salas só aparece para admin (no servidor, `list_rooms` e `read_room` valem para qualquer usuário logado). No `list_rooms`, os filtros só passam pela regex do protocolo; `end_time <= start_time` não é recusado (o protocolo não define).

Itens avaliados:

| Item | Cliente | Item | Servidor | Parte | Mensagens |
|---|---|---|---|---|---|
| — | — | h) | Envia corretamente as mensagens de erro | 2.1 e 2.2 | todas |
| a) | ADM lista todos os usuários (R) | i) | idem | 2.1 | `admin_list_users` |
| b) | ADM atualiza dados de usuário diferente do seu (U) | j) | idem | 2.1 | `admin_update_user` (+ `admin_read_user`) |
| c) | ADM exclui cadastro de usuário comum (D) | k) | idem | 2.1 | `admin_delete_user` |
| d) | ADM cadastra sala (C): nome, dias da semana, horários, capacidade | l) | idem | 2.2 | `create_room` |
| e) | ADM lê as salas (R): horários, capacidade, status, quem reservou e quando | m) | idem | 2.2 | `list_rooms`, `read_room`, `list_reservations` (`scope: "all"`) |
| f) | ADM atualiza sala (U): nome, horários, capacidade, status | n) | idem | 2.2 | `update_room` |
| g) | ADM remove sala (D) | o) | idem | 2.2 | `delete_room` |

Status "livre ou indisponível" = `room_status` `active` / `inactive`.

## GitHub
1. Para tarefas envolvendo git, utilize apenas ações bash. 
2. Sempre que for realizar commit e push, pergunte se estou conectado à rede da UTFPR ou a uma rede home. Na rede UTFPR, realize apenas alterações locais (como `git pull`, `git commit` e outras ações que alteram só o repositório local). Na rede home, realize qualquer alteração.

## Atualização de Código
1. Toda e qualquer atualização deve ser explicada.
2. Quando estiver criando código, quando estiver em dúvida, me pergunte o que fazer.
