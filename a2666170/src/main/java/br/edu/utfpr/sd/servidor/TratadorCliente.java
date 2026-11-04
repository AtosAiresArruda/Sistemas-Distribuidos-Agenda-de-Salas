package br.edu.utfpr.sd.servidor;

import br.edu.utfpr.sd.comum.ConexaoJson;
import br.edu.utfpr.sd.comum.Horarios;
import br.edu.utfpr.sd.comum.Horarios.Horario;
import br.edu.utfpr.sd.comum.Json;
import br.edu.utfpr.sd.servidor.BancoDados.Sala;
import br.edu.utfpr.sd.servidor.BancoDados.Usuario;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/** Thread que atende um cliente: le cada requisicao, processa e envia exatamente uma resposta. */
public class TratadorCliente extends Thread {

    // Protocolo 1.9: conexao encerrada apos 300 s de inatividade. Por decisao do projeto,
    // o token da conexao e invalidado nesse caso tambem (ver encerrarSessao)
    private static final int TIMEOUT_INATIVIDADE_MS = 300_000;

    // Aba "Dicionario" do protocolo
    private static final Pattern USER = Pattern.compile("^[a-z]{1,30}$");
    private static final Pattern PASSWORD = Pattern.compile("^[A-Za-z0-9]{1,20}$");
    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9._-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$");
    private static final Pattern TOKEN = Pattern.compile("^[a-f0-9]{64}$");

    private static final Pattern ROOM_ID = Pattern.compile("^[0-9]{1,10}$");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9 -]{1,30}$");
    private static final Pattern CAPACITY = Pattern.compile("^[1-9][0-9]{0,3}$");
    private static final Pattern LOCATION = Pattern.compile("^[A-Za-z0-9 ,.-]{1,60}$");
    private static final Pattern DATE = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");
    private static final Pattern TIME = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");

    // Dicionario: valores fixos (case-sensitive)
    private static final Set<String> ROLES = Set.of("user", "admin");
    private static final Set<String> ROOM_STATUS = Set.of("active", "inactive");
    private static final Set<String> SCOPES = Set.of("mine", "all");

    private static final String DADOS_SALA = "Dados da sala em formato invalido";
    private static final String SALA_NAO_ENCONTRADA = "Sala nao encontrada";
    private static final String RESERVAS_ATIVAS = "Sala possui reservas ativas";

    private static final String TOKEN_INVALIDO = "Token invalido ou expirado";
    private static final String USUARIO_NAO_ENCONTRADO = "Usuario nao encontrado";
    private static final String ULTIMO_ADMIN = "Nao e possivel remover o ultimo administrador";

    /** Interrompe a operacao com uma resposta de erro (status e message da aba de mensagens). */
    private static final class Falha extends Exception {
        final String status;

        Falha(String status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    private final Socket socket;
    private final BancoDados banco;
    private final MonitorServidor monitor;
    private final String remoto;

    // Tokens validos usados nesta conexao; sao invalidados quando a conexao encerra
    private final Set<String> tokensDaConexao = new HashSet<>();

    public TratadorCliente(Socket socket, BancoDados banco) {
        this(socket, banco, MonitorServidor.NENHUM);
    }

    public TratadorCliente(Socket socket, BancoDados banco, MonitorServidor monitor) {
        super("Cliente-" + CONTADOR.incrementAndGet());
        this.socket = socket;
        this.banco = banco;
        this.monitor = monitor;
        this.remoto = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }

    /** Endereco ip:porta do cliente atendido. */
    public String getRemoto() {
        return remoto;
    }

    /** Fecha a conexao; a thread termina ao perceber o socket fechado. */
    public void encerrar() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // A conexao ja estava fechada
        }
    }

    @Override
    public void run() {
        System.out.println("Nova thread de comunicacao iniciada com cliente: " + remoto);
        monitor.conexaoAberta(this);
        String motivo = "Cliente encerrou a conexao";

        try (ConexaoJson conexao = new ConexaoJson(socket, "Servidor")) {
            conexao.setOuvinte((direcao, texto) -> monitor.mensagem(this, direcao, texto));
            socket.setSoTimeout(TIMEOUT_INATIVIDADE_MS);
            while (true) {
                String linha;
                try {
                    linha = conexao.receber();
                } catch (ConexaoJson.MensagemMuitoGrandeException e) {
                    conexao.enviar(Json.resposta("error", "400", "Mensagem excede o tamanho maximo"));
                    continue;
                } catch (SocketTimeoutException e) {
                    System.out.println("Cliente " + remoto + " inativo por 300 s; encerrando a conexao.");
                    motivo = "Inativo por 300 s";
                    break;
                }
                if (linha == null) {
                    break;
                }
                conexao.enviar(processar(linha));
            }
        } catch (IOException e) {
            System.err.println("Problema de I/O com o cliente " + remoto + ": " + e.getMessage());
            motivo = socket.isClosed() ? "Conexao fechada pelo servidor" : "Erro de I/O: " + e.getMessage();
        }
        System.out.println("Conexao encerrada com cliente: " + remoto);
        encerrarSessao();
        monitor.conexaoEncerrada(this, motivo);
    }

    /**
     * Toda queda de conexao conta como logout: o token usado nesta conexao sai da whitelist.
     * Cobre fechamento do app sem logout, processo morto, erro de I/O, timeout e fechamento pelo servidor.
     */
    private void encerrarSessao() {
        for (String token : tokensDaConexao) {
            try {
                if (banco.invalidarToken(token)) {
                    System.out.println("Logout forcado do cliente " + remoto + ": token invalidado por queda de conexao.");
                }
            } catch (Exception e) {
                System.err.println("Erro ao invalidar o token do cliente " + remoto + ": " + e.getMessage());
            }
        }
        tokensDaConexao.clear();
    }

    /** Processa uma requisicao e devolve a resposta; nunca lanca excecao (protocolo 4.5). */
    JsonObject processar(String linha) {
        JsonObject requisicao;
        try {
            requisicao = Json.parseObjeto(linha);
        } catch (JsonParseException e) {
            return Json.resposta("error", "400", "Requisicao invalida");
        }

        JsonElement opElemento = requisicao.get("op");
        if (!ehString(opElemento)) {
            return Json.resposta("error", "400", "Requisicao invalida");
        }
        String op = opElemento.getAsString();
        String opResposta = op + "_response";

        try {
            return switch (op) {
                case "register" -> register(requisicao);
                case "login" -> login(requisicao);
                case "logout" -> logout(requisicao);
                case "read_user" -> readUser(requisicao);
                case "update_user" -> updateUser(requisicao);
                case "delete_user" -> deleteUser(requisicao);
                case "admin_list_users" -> adminListUsers(requisicao);
                case "admin_read_user" -> adminReadUser(requisicao);
                case "admin_update_user" -> adminUpdateUser(requisicao);
                case "admin_delete_user" -> adminDeleteUser(requisicao);
                case "create_room" -> createRoom(requisicao);
                case "list_rooms" -> listRooms(requisicao);
                case "read_room" -> readRoom(requisicao);
                case "update_room" -> updateRoom(requisicao);
                case "delete_room" -> deleteRoom(requisicao);
                case "list_reservations" -> listReservations(requisicao);
                default -> Json.resposta("error", "400", "Operacao desconhecida");
            };
        } catch (Falha f) {
            return Json.resposta(opResposta, f.status, f.getMessage());
        } catch (Exception e) {
            System.err.println("Erro interno ao processar '" + op + "': " + e);
            return Json.resposta(opResposta, "500", "Erro interno do servidor");
        }
    }

    private JsonObject register(JsonObject req) throws Exception {
        String formatoInvalido = "Dados de cadastro em formato invalido";
        // O email e validado como chegou: letra maiuscula nao casa com a regex e responde 400
        String email = texto(req, "email", formatoInvalido);
        String user = minusculas(texto(req, "user", formatoInvalido));
        String password = texto(req, "password", formatoInvalido);
        if (!confere(EMAIL, email) || !confere(USER, user) || !confere(PASSWORD, password)) {
            throw new Falha("400", formatoInvalido);
        }

        if (!banco.cadastrar(user, email, password)) {
            throw new Falha("409", "Usuario ou email ja cadastrado");
        }
        return Json.resposta("register_response", "201", "Usuario cadastrado com sucesso");
    }

    private JsonObject login(JsonObject req) throws Exception {
        String formatoInvalido = "Email ou senha em formato invalido";
        String email = minusculas(texto(req, "email", formatoInvalido));
        String password = texto(req, "password", formatoInvalido);
        if (!confere(EMAIL, email) || !confere(PASSWORD, password)) {
            throw new Falha("400", formatoInvalido);
        }

        // Usuario inexistente e senha errada tem a mesma resposta, para nao revelar quem existe
        Usuario usuario = banco.autenticar(email, password);
        if (usuario == null) {
            throw new Falha("401", "Email ou senha incorretos");
        }
        String token = banco.abrirSessao(usuario.id());
        if (token == null) {
            throw new Falha("409", "Usuario ja possui sessao ativa");
        }

        tokensDaConexao.add(token);
        JsonObject resposta = Json.resposta("login_response", "200", "Login realizado com sucesso");
        resposta.addProperty("token", token);
        resposta.addProperty("role", usuario.role());
        return resposta;
    }

    private JsonObject logout(JsonObject req) throws Exception {
        String token = token(req, "Token em formato invalido");
        if (!banco.invalidarToken(token)) {
            throw new Falha("401", TOKEN_INVALIDO);
        }
        esquecerToken(token);
        return Json.resposta("logout_response", "200", "Logout realizado com sucesso");
    }

    private JsonObject readUser(JsonObject req) throws Exception {
        Usuario usuario = sessao(token(req, "Token em formato invalido"));

        JsonObject resposta = Json.resposta("read_user_response", "200", "Consulta realizada com sucesso");
        resposta.addProperty("user", usuario.user());
        resposta.addProperty("email", usuario.email());
        resposta.addProperty("role", usuario.role());
        resposta.addProperty("created_at", usuario.createdAt());
        return resposta;
    }

    private JsonObject updateUser(JsonObject req) throws Exception {
        String formatoInvalido = "Dados em formato invalido";
        // O email nao pode ser alterado: a simples presenca da chave e rejeitada
        if (req.has("email")) {
            throw new Falha("400", formatoInvalido);
        }
        String token = token(req, formatoInvalido);
        String user = minusculas(texto(req, "user", formatoInvalido));
        String password = texto(req, "password", formatoInvalido);
        // String vazia significa "nao alterar"
        if ((!user.isEmpty() && !confere(USER, user)) || (!password.isEmpty() && !confere(PASSWORD, password))) {
            throw new Falha("400", formatoInvalido);
        }
        // Regra 2.13 (v2.4): os dois campos vazios nao alteram nada
        if (user.isEmpty() && password.isEmpty()) {
            throw new Falha("400", "Nenhum dado para atualizar");
        }

        Usuario usuario = sessao(token);
        if (!banco.atualizar(usuario.id(), user.isEmpty() ? null : user, password.isEmpty() ? null : password)) {
            throw new Falha("409", "Usuario ja esta em uso");
        }
        return Json.resposta("update_user_response", "200", "Dados atualizados com sucesso");
    }

    private JsonObject deleteUser(JsonObject req) throws Exception {
        String formatoInvalido = "Senha em formato invalido";
        String token = token(req, formatoInvalido);
        String password = texto(req, "password", formatoInvalido);
        if (!confere(PASSWORD, password)) {
            throw new Falha("400", formatoInvalido);
        }

        Usuario usuario = sessao(token);
        // Senha incorreta com token valido responde 403 e a sessao continua (protocolo 3.9)
        if (!banco.senhaConfere(usuario.id(), password)) {
            throw new Falha("403", "Senha incorreta");
        }
        if (!banco.removerUsuario(usuario.id())) {
            throw new Falha("403", ULTIMO_ADMIN);
        }
        esquecerToken(token);
        return Json.resposta("delete_user_response", "200", "Usuario removido com sucesso");
    }

    private JsonObject adminListUsers(JsonObject req) throws Exception {
        sessaoAdmin(token(req, "Token em formato invalido"));

        // A senha nunca e devolvida
        JsonArray lista = new JsonArray();
        for (Usuario usuario : banco.listarUsuarios()) {
            JsonObject item = new JsonObject();
            item.addProperty("user", usuario.user());
            item.addProperty("email", usuario.email());
            item.addProperty("role", usuario.role());
            lista.add(item);
        }
        JsonObject resposta = Json.resposta("admin_list_users_response", "200", "Consulta realizada com sucesso");
        resposta.addProperty("count", String.valueOf(lista.size()));
        resposta.add("users", lista);
        return resposta;
    }

    private JsonObject adminReadUser(JsonObject req) throws Exception {
        String formatoInvalido = "Dados em formato invalido";
        String token = token(req, formatoInvalido);
        String alvo = minusculas(texto(req, "target_user", formatoInvalido));
        if (!confere(USER, alvo)) {
            throw new Falha("400", formatoInvalido);
        }

        sessaoAdmin(token);
        Usuario usuario = banco.buscarPorUser(alvo);
        if (usuario == null) {
            throw new Falha("404", USUARIO_NAO_ENCONTRADO);
        }
        JsonObject resposta = Json.resposta("admin_read_user_response", "200", "Consulta realizada com sucesso");
        resposta.addProperty("user", usuario.user());
        resposta.addProperty("email", usuario.email());
        resposta.addProperty("role", usuario.role());
        resposta.addProperty("created_at", usuario.createdAt());
        return resposta;
    }

    private JsonObject adminUpdateUser(JsonObject req) throws Exception {
        String formatoInvalido = "Dados em formato invalido";
        // Mesmas regras do update_user: o email nao pode ser alterado
        if (req.has("email")) {
            throw new Falha("400", formatoInvalido);
        }
        String token = token(req, formatoInvalido);
        String alvo = minusculas(texto(req, "target_user", formatoInvalido));
        String user = minusculas(texto(req, "user", formatoInvalido));
        String password = texto(req, "password", formatoInvalido);
        String role = texto(req, "role", formatoInvalido);
        // String vazia significa "nao alterar"
        if (!confere(USER, alvo)
                || (!user.isEmpty() && !confere(USER, user))
                || (!password.isEmpty() && !confere(PASSWORD, password))
                || (!role.isEmpty() && !ROLES.contains(role))) {
            throw new Falha("400", formatoInvalido);
        }
        // Regra 2.13 (v2.4): os tres campos vazios nao alteram nada
        if (user.isEmpty() && password.isEmpty() && role.isEmpty()) {
            throw new Falha("400", "Nenhum dado para atualizar");
        }

        sessaoAdmin(token);
        switch (banco.atualizarComoAdmin(alvo, vazioComoNulo(user), vazioComoNulo(password), vazioComoNulo(role))) {
            case NAO_ENCONTRADO -> throw new Falha("404", USUARIO_NAO_ENCONTRADO);
            case ULTIMO_ADMIN -> throw new Falha("403", ULTIMO_ADMIN);
            case USER_EM_USO -> throw new Falha("409", "Usuario ja esta em uso");
            case OK -> {
            }
        }
        return Json.resposta("admin_update_user_response", "200", "Dados atualizados com sucesso");
    }

    private JsonObject adminDeleteUser(JsonObject req) throws Exception {
        String formatoInvalido = "Dados em formato invalido";
        String token = token(req, formatoInvalido);
        String alvo = minusculas(texto(req, "target_user", formatoInvalido));
        if (!confere(USER, alvo)) {
            throw new Falha("400", formatoInvalido);
        }

        Usuario autor = sessaoAdmin(token);
        switch (banco.removerComoAdmin(alvo)) {
            case NAO_ENCONTRADO -> throw new Falha("404", USUARIO_NAO_ENCONTRADO);
            case ULTIMO_ADMIN -> throw new Falha("403", ULTIMO_ADMIN);
            default -> {
            }
        }
        // O admin removeu a propria conta: o token ja foi invalidado junto
        if (autor.user().equals(alvo)) {
            esquecerToken(token);
        }
        return Json.resposta("admin_delete_user_response", "200", "Usuario removido com sucesso");
    }

    private JsonObject createRoom(JsonObject req) throws Exception {
        String formatoInvalido = DADOS_SALA;
        String token = token(req, formatoInvalido);
        String name = texto(req, "name", formatoInvalido);
        String capacity = texto(req, "capacity", formatoInvalido);
        String location = texto(req, "location", formatoInvalido);
        List<String> resources = recursos(req, formatoInvalido);
        // schedule (extensao do projeto) e opcional: sem ele a sala nasce sem horarios
        List<Horario> schedule = req.has("schedule") ? horarios(req, formatoInvalido) : List.of();
        // Em criacao, "" e invalido (protocolo 2.11): as regex ja exigem pelo menos 1 caractere
        if (!confere(NAME, name) || !confere(CAPACITY, capacity) || !confere(LOCATION, location)) {
            throw new Falha("400", formatoInvalido);
        }

        sessaoAdmin(token);
        Long roomId = banco.criarSala(name, Integer.parseInt(capacity), location, resources, schedule);
        if (roomId == null) {
            throw new Falha("409", "Sala ja cadastrada");
        }
        JsonObject resposta = Json.resposta("create_room_response", "201", "Sala cadastrada com sucesso");
        resposta.addProperty("room_id", String.valueOf(roomId));
        return resposta;
    }

    private JsonObject listRooms(JsonObject req) throws Exception {
        String formatoInvalido = "Filtros em formato invalido";
        String token = token(req, formatoInvalido);
        String date = opcional(req, "date", formatoInvalido);
        String startTime = opcional(req, "start_time", formatoInvalido);
        String endTime = opcional(req, "end_time", formatoInvalido);
        String minCapacity = opcional(req, "min_capacity", formatoInvalido);
        // date, start_time e end_time vem juntos ou nenhum deles
        int trio = (date == null ? 0 : 1) + (startTime == null ? 0 : 1) + (endTime == null ? 0 : 1);
        if ((trio != 0 && trio != 3)
                || (date != null && !confere(DATE, date))
                || (startTime != null && !confere(TIME, startTime))
                || (endTime != null && !confere(TIME, endTime))
                || (minCapacity != null && !confere(CAPACITY, minCapacity))) {
            throw new Falha("400", formatoInvalido);
        }

        sessao(token);
        JsonArray lista = new JsonArray();
        for (Sala sala : banco.listarSalas(minCapacity == null ? null : Integer.parseInt(minCapacity))) {
            // available so aparece quando o trio de horario e enviado
            String available = trio == 3 ? String.valueOf(banco.disponivel(sala, date, startTime, endTime)) : null;
            lista.add(salaJson(new JsonObject(), sala, available));
        }
        JsonObject resposta = Json.resposta("list_rooms_response", "200", "Consulta realizada com sucesso");
        resposta.addProperty("count", String.valueOf(lista.size()));
        resposta.add("rooms", lista);
        return resposta;
    }

    private JsonObject readRoom(JsonObject req) throws Exception {
        String formatoInvalido = "Dados em formato invalido";
        String token = token(req, formatoInvalido);
        String roomId = texto(req, "room_id", formatoInvalido);
        if (!confere(ROOM_ID, roomId)) {
            throw new Falha("400", formatoInvalido);
        }

        sessao(token);
        Sala sala = banco.buscarSala(Long.parseLong(roomId));
        if (sala == null) {
            throw new Falha("404", SALA_NAO_ENCONTRADA);
        }
        return salaJson(Json.resposta("read_room_response", "200", "Consulta realizada com sucesso"), sala, null);
    }

    private JsonObject updateRoom(JsonObject req) throws Exception {
        String formatoInvalido = DADOS_SALA;
        String token = token(req, formatoInvalido);
        String roomId = texto(req, "room_id", formatoInvalido);
        String name = texto(req, "name", formatoInvalido);
        String capacity = texto(req, "capacity", formatoInvalido);
        String location = texto(req, "location", formatoInvalido);
        String roomStatus = texto(req, "room_status", formatoInvalido);
        // resources e sempre a lista completa; schedule ausente mantem os horarios
        List<String> resources = recursos(req, formatoInvalido);
        List<Horario> schedule = req.has("schedule") ? horarios(req, formatoInvalido) : null;
        // String vazia significa "nao alterar"
        if (!confere(ROOM_ID, roomId)
                || (!name.isEmpty() && !confere(NAME, name))
                || (!capacity.isEmpty() && !confere(CAPACITY, capacity))
                || (!location.isEmpty() && !confere(LOCATION, location))
                || (!roomStatus.isEmpty() && !ROOM_STATUS.contains(roomStatus))) {
            throw new Falha("400", formatoInvalido);
        }

        sessaoAdmin(token);
        BancoDados.AlteracaoSala alteracao = new BancoDados.AlteracaoSala(vazioComoNulo(name),
                capacity.isEmpty() ? null : Integer.parseInt(capacity), vazioComoNulo(location), resources,
                vazioComoNulo(roomStatus), schedule);
        switch (banco.atualizarSala(Long.parseLong(roomId), alteracao)) {
            case NAO_ENCONTRADO -> throw new Falha("404", SALA_NAO_ENCONTRADA);
            case NOME_EM_USO -> throw new Falha("409", "Sala ja cadastrada");
            // Pendencia 3 do protocolo: sem message definida; decidido usar a do catalogo de 409
            case RESERVAS_ATIVAS -> throw new Falha("409", RESERVAS_ATIVAS);
            default -> {
            }
        }
        return Json.resposta("update_room_response", "200", "Sala atualizada com sucesso");
    }

    private JsonObject deleteRoom(JsonObject req) throws Exception {
        String formatoInvalido = "Dados em formato invalido";
        String token = token(req, formatoInvalido);
        String roomId = texto(req, "room_id", formatoInvalido);
        if (!confere(ROOM_ID, roomId)) {
            throw new Falha("400", formatoInvalido);
        }

        sessaoAdmin(token);
        switch (banco.removerSala(Long.parseLong(roomId))) {
            case NAO_ENCONTRADO -> throw new Falha("404", SALA_NAO_ENCONTRADA);
            case RESERVAS_ATIVAS -> throw new Falha("409", RESERVAS_ATIVAS);
            default -> {
            }
        }
        return Json.resposta("delete_room_response", "200", "Sala removida com sucesso");
    }

    private JsonObject listReservations(JsonObject req) throws Exception {
        String formatoInvalido = "Filtros em formato invalido";
        String token = token(req, formatoInvalido);
        String scope = opcional(req, "scope", formatoInvalido);
        String date = opcional(req, "date", formatoInvalido);
        String roomId = opcional(req, "room_id", formatoInvalido);
        if ((scope != null && !SCOPES.contains(scope))
                || (date != null && !confere(DATE, date))
                || (roomId != null && !confere(ROOM_ID, roomId))) {
            throw new Falha("400", formatoInvalido);
        }

        Usuario usuario = sessao(token);
        // Sem scope vale "mine"; "all" e so para admin (whitelist, protocolo 3.14)
        boolean todas = "all".equals(scope);
        if (todas && !banco.tokenEhAdmin(token)) {
            throw new Falha("403", "Permissao insuficiente");
        }
        JsonArray lista = new JsonArray();
        for (BancoDados.Reserva reserva : banco.listarReservas(todas ? null : usuario.id(), date,
                roomId == null ? null : Long.parseLong(roomId))) {
            JsonObject item = new JsonObject();
            item.addProperty("reservation_id", String.valueOf(reserva.id()));
            item.addProperty("room_id", String.valueOf(reserva.roomId()));
            item.addProperty("name", reserva.roomName());
            item.addProperty("user", reserva.user());
            item.addProperty("date", reserva.date());
            item.addProperty("start_time", reserva.startTime());
            item.addProperty("end_time", reserva.endTime());
            item.addProperty("topic", reserva.topic());
            item.addProperty("participants", String.valueOf(reserva.participants()));
            lista.add(item);
        }
        JsonObject resposta = Json.resposta("list_reservations_response", "200", "Consulta realizada com sucesso");
        resposta.addProperty("count", String.valueOf(lista.size()));
        resposta.add("reservations", lista);
        return resposta;
    }

    /** Campos da sala na ordem do protocolo; available so entra quando informado; schedule e a extensao do projeto. */
    private static JsonObject salaJson(JsonObject destino, Sala sala, String available) {
        destino.addProperty("room_id", String.valueOf(sala.id()));
        destino.addProperty("name", sala.name());
        destino.addProperty("capacity", String.valueOf(sala.capacity()));
        destino.addProperty("location", sala.location());
        JsonArray resources = new JsonArray();
        sala.resources().forEach(resources::add);
        destino.add("resources", resources);
        destino.addProperty("room_status", sala.roomStatus());
        if (available != null) {
            destino.addProperty("available", available);
        }
        JsonArray schedule = new JsonArray();
        for (Horario horario : sala.schedule()) {
            JsonObject item = new JsonObject();
            item.addProperty("day", horario.day());
            item.addProperty("slot", horario.slot());
            schedule.add(item);
        }
        destino.add("schedule", schedule);
        return destino;
    }

    /** resources: array obrigatorio (pode ser vazio) cujos itens sao todos strings. */
    private static List<String> recursos(JsonObject req, String formatoInvalido) throws Falha {
        JsonElement valor = req.get("resources");
        if (valor == null || !valor.isJsonArray()) {
            throw new Falha("400", formatoInvalido);
        }
        List<String> recursos = new ArrayList<>();
        for (JsonElement item : valor.getAsJsonArray()) {
            if (!ehString(item)) {
                throw new Falha("400", formatoInvalido);
            }
            recursos.add(item.getAsString());
        }
        return recursos;
    }

    /** schedule: array de {"day", "slot"} com valores da lista de dias e blocos; par repetido e invalido. */
    private static List<Horario> horarios(JsonObject req, String formatoInvalido) throws Falha {
        JsonElement valor = req.get("schedule");
        if (valor == null || !valor.isJsonArray()) {
            throw new Falha("400", formatoInvalido);
        }
        Set<Horario> horarios = new HashSet<>();
        for (JsonElement item : valor.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                throw new Falha("400", formatoInvalido);
            }
            JsonObject objeto = item.getAsJsonObject();
            Horario horario = new Horario(texto(objeto, "day", formatoInvalido), texto(objeto, "slot", formatoInvalido));
            if (!Horarios.valido(horario) || !horarios.add(horario)) {
                throw new Falha("400", formatoInvalido);
            }
        }
        return List.copyOf(horarios);
    }

    /**
     * Le o token da requisicao. Token ausente responde 401 (aba "Codigos de Status");
     * nulo, de outro tipo ou fora do formato responde 400.
     */
    private static String token(JsonObject req, String formatoInvalido) throws Falha {
        if (!req.has("token")) {
            throw new Falha("401", TOKEN_INVALIDO);
        }
        String token = texto(req, "token", formatoInvalido);
        if (!confere(TOKEN, token)) {
            throw new Falha("400", formatoInvalido);
        }
        return token;
    }

    /** Dono do token, renovando sua validade; token inexistente, expirado ou invalidado responde 401. */
    private Usuario sessao(String token) throws Exception {
        Usuario usuario = banco.usuarioDoToken(token);
        if (usuario == null) {
            throw new Falha("401", TOKEN_INVALIDO);
        }
        // Um token valido pode chegar por uma conexao nova (ex.: cliente reconectou); passa a ser desta
        tokensDaConexao.add(token);
        return usuario;
    }

    /**
     * Dono de um token de administrador. Protocolo 3.14: so a whitelist de admin libera a operacao;
     * token valido que esta so na lista de usuarios comuns responde 403.
     */
    private Usuario sessaoAdmin(String token) throws Exception {
        Usuario usuario = sessao(token);
        if (!banco.tokenEhAdmin(token)) {
            throw new Falha("403", "Permissao insuficiente");
        }
        return usuario;
    }

    /** O token ja foi invalidado pela propria operacao; nada a fazer quando a conexao cair. */
    private void esquecerToken(String token) {
        tokensDaConexao.remove(token);
    }

    /** Campo opcional: ausente devolve null; presente precisa ser string (null ou outro tipo responde 400). */
    private static String opcional(JsonObject req, String chave, String formatoInvalido) throws Falha {
        return req.has(chave) ? texto(req, chave, formatoInvalido) : null;
    }

    /** Campo obrigatorio do tipo string; ausente, nulo ou de outro tipo responde 400 (protocolo 2.10). */
    private static String texto(JsonObject req, String chave, String formatoInvalido) throws Falha {
        JsonElement valor = req.get(chave);
        if (!ehString(valor)) {
            throw new Falha("400", formatoInvalido);
        }
        return valor.getAsString();
    }

    private static boolean ehString(JsonElement valor) {
        return valor != null && valor.isJsonPrimitive() && valor.getAsJsonPrimitive().isString();
    }

    private static boolean confere(Pattern formato, String valor) {
        return formato.matcher(valor).matches();
    }

    // Protocolo 2.12: 'user' e 'email' sao sempre gravados em minusculas
    private static String minusculas(String valor) {
        return valor.toLowerCase(Locale.ROOT);
    }

    /** Em atualizacao, "" significa "nao alterar" (protocolo 2.11); o banco recebe null nesse caso. */
    private static String vazioComoNulo(String valor) {
        return valor.isEmpty() ? null : valor;
    }
}
