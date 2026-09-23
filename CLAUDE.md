# Sistema de Agendamento de Salas — Sistemas Distribuídos

Trabalho da disciplina de Sistemas Distribuídos (UTFPR). Sistema distribuído cliente/servidor de agendamento de salas, com CRUD de usuários e administradores.

## Stack
- Linguagem: **Java 26.0.2**.
- Comunicação cliente ↔ servidor: **JSON** sobre TCP, usando Gson (`a2666170/src/libs/gson-2.14.0.jar`).
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

## Especificação (Drive compartilhado SD-BCC)
Índice em `INDEX.md`. Leia os documentos pelo conector do Google Drive, usando os IDs abaixo.

| Arquivo | ID | Conteúdo |
|---|---|---|
| Protocolo de troca de Mensagens (planilha) | `1CK27K-MR88xDL7VikYjhQYnJoeDnppd9rFUuso_ZXwg` | Formato de **todas** as mensagens JSON. Cliente e servidor devem implementar todas. |
| Requisitos (documento) | `1hKXp-2MkBlhInwCq8vIMsbpZ92BujFues9ma3d-VwfI` | Requisitos funcionais e não funcionais, e as regras de negócio de cada operação. |
| IPs das VMs (planilha) | `1u1Oc7BuLEMcmyGXzp8YxMQd79LtMrRjfhIPrL4n6Xqw` | IPs das máquinas virtuais do laboratório. O login nelas exige credenciais do usuário: nunca as digite, peça que o usuário faça o login. |

**Requisito crítico:** as especificações do Drive devem ser seguidas **à risca, em todos os detalhes**: nomes de campos, valores, códigos de resposta e regras de negócio. Os documentos são alterados ao longo do semestre. **Antes de implementar ou alterar uma operação, releia o protocolo** e os requisitos atualizados, e não presuma formatos.

## Entrega-Parcial-01
EP-1 vale 2,0 pontos: 1,0 do cliente e 1,0 do servidor. O escopo é só o **CRUD do usuário comum, com login e logout**. O formato de cada mensagem segue a planilha de protocolo.

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

## GitHub
1. Para tarefas envolvendo git, utilize apenas ações bash. Evite ações via browser.
2. Sempre que for realizar commit e push, pergunte se estou conectado à rede da UTFPR ou a uma rede home. Na rede UTFPR, realize apenas alterações locais (como `git pull`, `git commit` e outras ações que alteram só o repositório local). Na rede home, realize qualquer alteração.

