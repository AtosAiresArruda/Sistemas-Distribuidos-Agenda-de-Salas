# Sistemas-Distribuidos-Agenda-de-Salas

Essa aplicação foi desenvolvida em conjunto da matéria Sistemas Distribuídos. O objetivo dessa matéria é que diferentes alunos construam um software que se comunique plenamente por toda a sala. Esse repositório é o meu software.

O sistema é um agendamento de salas cliente/servidor. Cliente e servidor trocam mensagens **JSON sobre TCP**, seguindo o protocolo definido pela turma no Drive compartilhado SD-BCC (índice em [INDEX.md](INDEX.md)). Como todos seguem o mesmo protocolo, o meu cliente conversa com o servidor de qualquer colega, e o meu servidor atende o cliente de qualquer colega.

## Funcionamento

**Servidor** ([Servidor.java](a2666170/src/main/java/br/edu/utfpr/sd/servidor/Servidor.java))
- Pede a porta ao iniciar e cria uma thread para cada cliente que se conecta.
- Guarda usuários e sessões em SQLite, no arquivo `agenda.db` da pasta de onde foi iniciado. As senhas ficam em hash.
- Gera um token de 64 caracteres hexadecimais no login. Cada usuário tem uma única sessão ativa, e o token expira após 30 minutos sem uso.
- Fecha conexões inativas por 300 segundos. O token continua válido, e o cliente pode reconectar.
- Responde a toda requisição com `op`, `status` e `message`, usando exatamente os textos da planilha de protocolo.

**Cliente** ([Cliente.java](a2666170/src/main/java/br/edu/utfpr/sd/cliente/Cliente.java))
- Pede o IP e a porta do servidor e mostra um menu com as operações.
- Guarda o token em memória depois do login e o envia nas operações seguintes.
- Descarta o token ao receber status 401, como manda o protocolo.

Os dois lados exibem no console **todas as mensagens JSON enviadas e recebidas**, no formato:

```
[Cliente] enviou para 127.0.0.1:23456: {"op":"login","email":"joao.silva@email.com","password":"senha123"}
[Cliente] recebeu de 127.0.0.1:23456: {"op":"login_response","status":"200","message":"Login realizado com sucesso","token":"...","role":"user"}
```

### Operações implementadas (Entrega Parcial 01)

| Operação | Mensagem | Precisa de login |
|---|---|---|
| Cadastrar usuário | `register` | não |
| Login | `login` | não |
| Ler o próprio cadastro | `read_user` | sim |
| Atualizar o próprio cadastro | `update_user` | sim |
| Apagar o próprio cadastro | `delete_user` | sim |
| Logout | `logout` | sim |

### Estrutura

```
a2666170/
├── pom.xml
└── src/
    ├── main/java/br/edu/utfpr/sd/
    │   ├── comum/      ConexaoJson (envio/recebimento e log das mensagens), Json
    │   ├── servidor/   Servidor, TratadorCliente (uma thread por cliente), BancoDados (SQLite),
    │   │               ServidorApp (interface JavaFX), MonitorServidor
    │   ├── cliente/    Cliente (menu de console), ClienteApp (interface JavaFX)
    │   └── gui/        PainelJson (área de JSONs usada pelas duas interfaces)
    ├── main/resources/ estilo.css das interfaces
    ├── test/java/...   ServidorTest (testes de integração)
    └── servidor_eco_tcp_json/   código base de exemplo da disciplina
```

## Requisitos

- **JDK 26** (o projeto usa o Temurin 26.0.2).
- **Maven 3.9** ou superior.

Confira as versões com `java -version` e `mvn -version`. Se a IDE usar outro JDK, aponte o projeto para o JDK 26 nas configurações dela.

Todos os comandos abaixo são executados dentro da pasta `a2666170`:

```bash
cd a2666170
```

## Interface gráfica (JavaFX)

Cliente e servidor também têm interface gráfica. Ela usa as mesmas classes de rede, então continua exibindo no console todas as mensagens JSON enviadas e recebidas.

```bash
mvn -q exec:java@servidor-gui
```

```bash
mvn -q exec:java@cliente-gui
```

**Servidor**: digite a porta e clique em *Iniciar servidor*. A tela principal mostra:
- no topo, o total de threads ativas, de conexões desde o início e de mensagens JSON;
- a tabela de threads, com cliente (ip:porta), estado, usuário logado, última operação, último status e número de mensagens;
- a conversa da thread selecionada na tabela;
- embaixo, a comunicação acumulada de todas as threads.

O botão *Desconectar cliente* fecha a conexão da thread selecionada, e *Remover encerradas* limpa as threads que já terminaram.

**Cliente**: informe IP e porta e clique em *Conectar* (as operações também conectam sozinhas).
- Aba **Login**: login com email e senha, e cadastro de novo usuário (`register`).
- Aba **Minha conta**, liberada após o login: `read_user`, `update_user` (campo em branco = não alterar), `delete_user` (pede a senha) e `logout`.
- À direita fica a área com todos os JSONs enviados e recebidos. O campo *Enviar JSON manual* manda uma linha exatamente como foi digitada.

Ao receber `401`, ou após logout e exclusão, o cliente descarta o token e volta para a aba Login.

## Testes locais

### Testes automatizados

Os testes sobem um servidor numa porta livre, com um banco temporário, e verificam as respostas de cada operação: sucesso, dados inválidos, token inválido, conflitos e erros de protocolo.

```bash
mvn test
```

### Cliente e servidor na mesma máquina

Abra dois terminais na pasta `a2666170`.

**Terminal 1 — servidor:**

```bash
mvn -q exec:java@servidor
```

Digite a porta, por exemplo `23456`. O servidor mostra `Servidor carregado na porta 23456. Aguardando conexoes...`.

**Terminal 2 — cliente:**

```bash
mvn -q exec:java@cliente
```

Informe o IP `127.0.0.1` e a mesma porta do servidor. Um roteiro que passa por todas as operações:

1. `1` (register) com email `joao.silva@email.com`, usuário `joao` e senha `senha123`. Resposta esperada: `201`.
2. `2` (login) com o mesmo email e senha. Resposta esperada: `200` e um token.
3. `3` (read_user). Resposta esperada: `200` com os dados do cadastro.
4. `4` (update_user) com um novo usuário, por exemplo `joana`, e senha em branco. Resposta esperada: `200`.
5. `6` (logout). Resposta esperada: `200`.
6. `2` (login) de novo, depois `5` (delete_user) com a senha. Resposta esperada: `200`.

A opção `7 - Enviar JSON manual` envia uma linha digitada sem alterações. Serve para testar como o servidor reage a JSON inválido, campos faltando ou operações desconhecidas.

Para começar com o banco vazio, pare o servidor e apague o `agenda.db`.

## Testar o meu cliente com o servidor de um colega

1. Peça ao colega o **IP** da máquina dele e a **porta** em que o servidor está rodando. As duas máquinas precisam estar na mesma rede, por exemplo a rede do laboratório ou as VMs.
2. Inicie o cliente:

   ```bash
   mvn -q exec:java@cliente
   ```

3. Informe o IP e a porta do colega e execute o roteiro da seção anterior.
4. Confira no console cada mensagem enviada e recebida. Se a resposta do servidor dele tiver um `status` ou `message` diferente da planilha de protocolo, anote e combine a correção com o colega.

Se o cliente mostrar `Erro de comunicacao` ao conectar, verifique se o IP e a porta estão certos, se o servidor do colega está rodando e se o firewall dele libera a porta.

## Deixar o meu servidor disponível para outros clientes

1. Inicie o servidor e escolha uma porta livre acima de 1024, por exemplo `23456`:

   ```bash
   mvn -q exec:java@servidor
   ```

2. Descubra o IP da sua máquina na rede e passe o IP e a porta para os colegas:

   ```bash
   hostname -I
   ```

   Use o endereço da rede em que os colegas estão; `127.0.0.1` só funciona na própria máquina. Nas VMs do laboratório, os endereços estão na planilha "IPs das VMs" do Drive.

3. Se o firewall estiver ativo, libere a porta. No Ubuntu, com o `ufw`:

   ```bash
   sudo ufw allow 23456/tcp
   ```

4. Deixe o terminal do servidor aberto. Cada conexão aparece como `Nova thread de comunicacao iniciada com cliente: <ip>:<porta>`, seguida de todas as mensagens trocadas com aquele cliente.

Para encerrar o servidor, use `Ctrl+C`.
