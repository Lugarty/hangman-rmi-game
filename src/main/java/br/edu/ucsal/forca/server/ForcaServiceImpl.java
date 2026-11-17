package br.edu.ucsal.forca.server;

import br.edu.ucsal.forca.shared.Conexao;
import br.edu.ucsal.forca.shared.EstadoPublico;
import br.edu.ucsal.forca.shared.Fase;
import br.edu.ucsal.forca.shared.ForcaRemote;
import br.edu.ucsal.forca.shared.ResultadoAcao;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Uma sala e uma partida compartilhada por dois clientes.
 * Operacoes synchronized tornam validacao e atualizacao atomicas.
 */
public final class ForcaServiceImpl extends UnicastRemoteObject implements ForcaRemote {
    private static final long serialVersionUID = 1L;
    public static final int LIMITE_ERROS = 6;
    public static final long TEMPO_LIMITE_SESSAO_MS = 30_000L;

    private static final String[] PALAVRAS = {
        "ARQUITETURA", "SERVIDOR", "CLIENTE", "ALGORITMO",
        "PROTOCOLO", "PROCESSO", "CONCORRENCIA", "SISTEMA",
        "COMPILADOR", "SOFTWARE", "JOGADOR", "DISTRIBUIDO"
    };

    private static final class Participante {
        final int numero;
        final String nome;
        long ultimoContato;
        boolean pediuReinicio;

        Participante(int numero, String nome, long agora) {
            this.numero = numero;
            this.nome = nome;
            this.ultimoContato = agora;
        }
    }

    private final Map<String, Participante> participantes = new LinkedHashMap<>();
    private final Set<Character> letrasTentadas = new LinkedHashSet<>();
    private final Supplier<String> sorteador;
    private final LongSupplier relogio;
    private final long tempoLimiteMs;

    private String palavraSecreta = "";
    private Fase fase = Fase.AGUARDANDO;
    private int erros;
    private int jogadorDaVez;
    private String mensagem = "Aguardando dois jogadores.";
    private long versao;

    /** Porta fixa para o objeto remoto facilita testes entre maquinas na LAN. */
    public ForcaServiceImpl(int portaObjeto) throws RemoteException {
        this(portaObjeto,
                () -> PALAVRAS[ThreadLocalRandom.current().nextInt(PALAVRAS.length)],
                System::currentTimeMillis, TEMPO_LIMITE_SESSAO_MS);
    }

    /** Permite injetar palavra e relogio deterministas nos testes. */
    ForcaServiceImpl(int portaObjeto, Supplier<String> sorteador,
                     LongSupplier relogio, long tempoLimiteMs) throws RemoteException {
        super(portaObjeto);
        if (sorteador == null || relogio == null || tempoLimiteMs <= 0) {
            throw new IllegalArgumentException("Configuracao invalida do servidor.");
        }
        this.sorteador = sorteador;
        this.relogio = relogio;
        this.tempoLimiteMs = tempoLimiteMs;
    }

    @Override
    public synchronized Conexao conectar(String nome) throws RemoteException {
        expirarInativos();
        String apelido = nome == null ? "" : nome.strip();
        if (apelido.isEmpty() || apelido.length() > 24) {
            return new Conexao(false, 0, "", "Informe um nome com 1 a 24 caracteres.");
        }
        if (participantes.size() == 2) {
            return new Conexao(false, 0, "", "Sala cheia: ja existem dois jogadores.");
        }

        int numero = participantes.values().stream().anyMatch(p -> p.numero == 1) ? 2 : 1;
        String token = UUID.randomUUID().toString();
        participantes.put(token, new Participante(numero, apelido, relogio.getAsLong()));
        if (participantes.size() == 2) {
            iniciarPartida();
        } else {
            fase = Fase.AGUARDANDO;
            mensagem = "Aguardando o segundo jogador.";
            versao++;
        }
        System.out.printf("Jogador %d (%s) entrou. Sala: %d/2%n",
                numero, apelido, participantes.size());
        return new Conexao(true, numero, token, "Conectado como Jogador " + numero + ".");
    }

    @Override
    public synchronized EstadoPublico consultarEstado(String token) throws RemoteException {
        Participante jogador = autenticar(token);
        return estadoPara(jogador);
    }

    @Override
    public synchronized ResultadoAcao tentarLetra(String token, char letra) throws RemoteException {
        Participante jogador = autenticar(token);
        if (fase != Fase.EM_ANDAMENTO) {
            return rejeitar(jogador, "Nao existe uma partida ativa.");
        }
        if (jogador.numero != jogadorDaVez) {
            return rejeitar(jogador, "Nao e a sua vez.");
        }

        char tentativa = Character.toUpperCase(letra);
        if (tentativa < 'A' || tentativa > 'Z') {
            return rejeitar(jogador, "Informe apenas uma letra entre A e Z.");
        }
        if (letrasTentadas.contains(tentativa)) {
            return rejeitar(jogador, "Essa letra ja foi tentada.");
        }

        letrasTentadas.add(tentativa);
        if (palavraSecreta.indexOf(tentativa) >= 0) {
            mensagem = "Jogador " + jogador.numero + " acertou a letra " + tentativa + ".";
            if (venceu()) {
                fase = Fase.VITORIA;
                jogadorDaVez = 0;
                mensagem = "Vitoria! Os dois jogadores descobriram a palavra.";
            }
        } else {
            erros++;
            mensagem = "Letra " + tentativa + " nao encontrada. Erros: "
                    + erros + "/" + LIMITE_ERROS + ".";
            if (erros >= LIMITE_ERROS) {
                fase = Fase.DERROTA;
                jogadorDaVez = 0;
                mensagem = "Derrota! A palavra era " + palavraSecreta + ".";
            }
        }
        if (fase == Fase.EM_ANDAMENTO) {
            jogadorDaVez = jogador.numero == 1 ? 2 : 1;
        }
        versao++;
        return new ResultadoAcao(true, mensagem, estadoPara(jogador));
    }

    @Override
    public synchronized ResultadoAcao solicitarReinicio(String token) throws RemoteException {
        Participante jogador = autenticar(token);
        if (fase != Fase.VITORIA && fase != Fase.DERROTA) {
            return rejeitar(jogador, "Nova partida disponivel somente apos o resultado.");
        }
        if (jogador.pediuReinicio) {
            return rejeitar(jogador, "Seu voto ja foi registrado. Aguardando o outro jogador.");
        }

        jogador.pediuReinicio = true;
        versao++;
        boolean todosConfirmaram = participantes.size() == 2
                && participantes.values().stream().allMatch(p -> p.pediuReinicio);
        if (todosConfirmaram) {
            iniciarPartida();
            return new ResultadoAcao(true, "Nova partida iniciada.", estadoPara(jogador));
        }
        return new ResultadoAcao(true, "Voto registrado. Aguardando o outro jogador.",
                estadoPara(jogador));
    }

    @Override
    public synchronized void sair(String token) throws RemoteException {
        expirarInativos();
        Participante jogador = participantes.remove(token);
        if (jogador != null) {
            registrarSaida("Jogador " + jogador.numero + " saiu.");
            System.out.printf("Jogador %d saiu. Sala: %d/2%n",
                    jogador.numero, participantes.size());
        }
    }

    private Participante autenticar(String token) {
        expirarInativos();
        Participante jogador = participantes.get(token);
        if (jogador == null) {
            throw new IllegalArgumentException("Sessao expirada ou inexistente. Conecte-se novamente.");
        }
        jogador.ultimoContato = relogio.getAsLong();
        return jogador;
    }

    private void expirarInativos() {
        long agora = relogio.getAsLong();
        Iterator<Map.Entry<String, Participante>> it = participantes.entrySet().iterator();
        String mensagemExpiracao = "";
        while (it.hasNext()) {
            Participante jogador = it.next().getValue();
            if (agora - jogador.ultimoContato > tempoLimiteMs) {
                mensagemExpiracao = "Jogador " + jogador.numero + " perdeu a conexao.";
                System.out.println(mensagemExpiracao);
                it.remove();
            }
        }
        if (!mensagemExpiracao.isEmpty()) {
            registrarSaida(mensagemExpiracao);
        }
    }

    private void registrarSaida(String motivo) {
        fase = participantes.isEmpty() ? Fase.AGUARDANDO : Fase.INTERROMPIDA;
        jogadorDaVez = 0;
        mensagem = participantes.isEmpty()
                ? "Aguardando dois jogadores."
                : motivo + " Partida interrompida. Aguardando novo participante.";
        versao++;
    }

    private void iniciarPartida() {
        String escolhida = sorteador.get();
        if (escolhida == null) {
            throw new IllegalStateException("Sorteador nao devolveu palavra.");
        }
        escolhida = escolhida.strip().toUpperCase(Locale.ROOT);
        if (!escolhida.matches("[A-Z]{3,}")) {
            throw new IllegalStateException("A palavra sorteada deve ter apenas A-Z e ao menos 3 letras.");
        }

        palavraSecreta = escolhida;
        letrasTentadas.clear();
        erros = 0;
        jogadorDaVez = 1;
        fase = Fase.EM_ANDAMENTO;
        mensagem = "Partida iniciada! Vez do Jogador 1.";
        for (Participante jogador : participantes.values()) {
            jogador.pediuReinicio = false;
        }
        versao++;
        System.out.printf("Nova partida iniciada (%d letras).%n", palavraSecreta.length());
    }

    private boolean venceu() {
        for (int i = 0; i < palavraSecreta.length(); i++) {
            if (!letrasTentadas.contains(palavraSecreta.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String mascara() {
        if (palavraSecreta.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder(palavraSecreta.length() * 2);
        for (int i = 0; i < palavraSecreta.length(); i++) {
            if (i > 0) sb.append(' ');
            char letra = palavraSecreta.charAt(i);
            sb.append(letrasTentadas.contains(letra) ? letra : '_');
        }
        return sb.toString();
    }

    private EstadoPublico estadoPara(Participante jogador) {
        StringBuilder tentadas = new StringBuilder();
        for (char letra : letrasTentadas) {
            if (!tentadas.isEmpty()) tentadas.append(' ');
            tentadas.append(letra);
        }
        return new EstadoPublico(
                participantes.size(),
                fase,
                mascara(),
                tentadas.toString(),
                erros,
                LIMITE_ERROS,
                jogadorDaVez,
                jogador.pediuReinicio,
                mensagem,
                versao);
    }

    private ResultadoAcao rejeitar(Participante jogador, String motivo) {
        return new ResultadoAcao(false, motivo, estadoPara(jogador));
    }
}
