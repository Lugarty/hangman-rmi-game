package br.edu.ucsal.forca.server;

import br.edu.ucsal.forca.shared.Conexao;
import br.edu.ucsal.forca.shared.EstadoPublico;
import br.edu.ucsal.forca.shared.Fase;
import br.edu.ucsal.forca.shared.ForcaRemote;
import br.edu.ucsal.forca.shared.ResultadoAcao;

import java.net.ServerSocket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Testes reais pelo stub RMI, sem bibliotecas externas. */
public final class ForcaIntegrationTest {
    private static int verificacoes;

    private ForcaIntegrationTest() { }

    public static void main(String[] args) throws Exception {
        testarPartidaCompleta();
        testarExpiracaoEQuedaDeServidor();
        System.out.printf("SUCESSO: %d verificacoes aprovadas.%n", verificacoes);
    }

    private static void testarPartidaCompleta() throws Exception {
        int porta;
        try (ServerSocket reserva = new ServerSocket(0)) {
            porta = reserva.getLocalPort();
        }

        ForcaServiceImpl impl = new ForcaServiceImpl(0, () -> "BANANA",
                System::currentTimeMillis, 30000L);
        Registry registro = LocateRegistry.createRegistry(porta);
        registro.rebind("ForcaService", impl);
        try {
            ForcaRemote svc = (ForcaRemote) LocateRegistry
                    .getRegistry("127.0.0.1", porta).lookup("ForcaService");

            Conexao c1 = svc.conectar("Anisio");
            Conexao c2 = svc.conectar("Bernardo");
            conferir(c1.aceita() && c1.numeroJogador() == 1, "Jogador 1 registrado");
            conferir(c2.aceita() && c2.numeroJogador() == 2, "Jogador 2 registrado");
            conferir(!c1.token().equals(c2.token()), "Sessoes independentes");
            conferir(!svc.conectar("Terceiro").aceita(), "Sala nao aceita terceiro");

            EstadoPublico inicial = svc.consultarEstado(c1.token());
            conferir(inicial.fase() == Fase.EM_ANDAMENTO, "Partida inicia com dois");
            conferir(inicial.palavraMascarada().equals("_ _ _ _ _ _"),
                    "Segredo oculto");
            conferir(inicial.jogadorDaVez() == 1, "Jogador 1 comeca");

            long versao = inicial.versao();
            conferir(!svc.tentarLetra(c2.token(), 'B').aceita(), "Fora do turno rejeitado");
            conferir(!svc.tentarLetra(c1.token(), '!').aceita(), "Caractere invalido rejeitado");
            conferir(svc.consultarEstado(c1.token()).versao() == versao,
                    "Rejeicoes nao mudam o estado");

            ResultadoAcao r1 = svc.tentarLetra(c1.token(), 'a');
            conferir(r1.aceita(), "Aceita e normaliza letra minuscula");
            conferir(r1.estado().palavraMascarada().equals("_ A _ A _ A"),
                    "Revela todas as ocorrencias");
            conferir(r1.estado().jogadorDaVez() == 2, "Alterna apos acerto");
            conferir(!svc.tentarLetra(c2.token(), 'A').aceita(), "Letra repetida rejeitada");
            conferir(svc.consultarEstado(c2.token()).jogadorDaVez() == 2,
                    "Letra repetida nao consome turno");
            conferir(svc.tentarLetra(c2.token(), 'N').aceita(), "Segundo jogador acerta");

            ResultadoAcao vitoria = svc.tentarLetra(c1.token(), 'B');
            conferir(vitoria.estado().fase() == Fase.VITORIA, "Vitoria cooperativa");
            conferir(vitoria.estado().palavraMascarada().equals("B A N A N A"),
                    "Palavra completa revelada");
            conferir(!svc.tentarLetra(c2.token(), 'C').aceita(),
                    "Nao aceita palpites apos fim");

            conferir(svc.solicitarReinicio(c1.token()).aceita(), "Primeiro voto");
            conferir(svc.consultarEstado(c1.token()).meuVotoReinicio(), "Voto identificado");
            conferir(!svc.solicitarReinicio(c1.token()).aceita(), "Voto duplicado recusado");
            conferir(svc.consultarEstado(c2.token()).fase() == Fase.VITORIA,
                    "Sem reinicio unilateral");
            ResultadoAcao nova = svc.solicitarReinicio(c2.token());
            conferir(nova.aceita() && nova.estado().fase() == Fase.EM_ANDAMENTO,
                    "Reinicia apos dois votos");
            conferir(nova.estado().erros() == 0
                    && nova.estado().letrasTentadas().isEmpty(),
                    "Novo jogo zera erros e letras");

            char[] erradas = {'C', 'D', 'E', 'F', 'G', 'H'};
            for (int i = 0; i < erradas.length; i++) {
                String token = i % 2 == 0 ? c1.token() : c2.token();
                ResultadoAcao perda = svc.tentarLetra(token, erradas[i]);
                conferir(perda.aceita(), "Erro " + (i + 1) + " aceito");
                conferir(perda.estado().erros() == i + 1, "Contador correto");
            }
            EstadoPublico derrota = svc.consultarEstado(c1.token());
            conferir(derrota.fase() == Fase.DERROTA, "Derrota apos seis erros");
            conferir(derrota.mensagem().contains("BANANA"), "Revela segredo so apos derrota");

            svc.solicitarReinicio(c1.token());
            svc.solicitarReinicio(c2.token());

            // Dois palpites do MESMO jogador disputam o mesmo turno.
            CountDownLatch largada = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<ResultadoAcao> f1 = pool.submit(() -> {
                    largada.await();
                    return svc.tentarLetra(c1.token(), 'A');
                });
                Future<ResultadoAcao> f2 = pool.submit(() -> {
                    largada.await();
                    return svc.tentarLetra(c1.token(), 'B');
                });
                largada.countDown();
                ResultadoAcao a = f1.get(5, TimeUnit.SECONDS);
                ResultadoAcao b = f2.get(5, TimeUnit.SECONDS);
                conferir(a.aceita() != b.aceita(), "So uma jogada simultanea aceita");
                EstadoPublico estado = svc.consultarEstado(c1.token());
                conferir(estado.letrasTentadas().trim().length() == 1,
                        "Nenhuma letra concorrente extra");
                conferir(estado.jogadorDaVez() == 2, "Turno unico apos concorrencia");
                conferir(svc.consultarEstado(c2.token()).versao() == estado.versao(),
                        "Dois clientes observam mesma versao");
            } finally {
                pool.shutdownNow();
            }

            svc.sair(c2.token());
            EstadoPublico interrompido = svc.consultarEstado(c1.token());
            conferir(interrompido.fase() == Fase.INTERROMPIDA,
                    "Desconexao interrompe a partida");
            conferir(interrompido.jogadoresConectados() == 1,
                    "Conexao removida corretamente");
            Conexao novoJogador = svc.conectar("Outro");
            conferir(novoJogador.aceita() && novoJogador.numeroJogador() == 2,
                    "Vaga liberada para novo jogador");
            conferir(svc.consultarEstado(c1.token()).fase() == Fase.EM_ANDAMENTO,
                    "Nova dupla inicia partida nova");
            svc.sair(c1.token());
            svc.sair(novoJogador.token());
            conferir(!svc.conectar(" ").aceita(), "Nome vazio rejeitado");

            System.out.println("OK: partida, turnos, vitoria, derrota, reinicio e concorrencia");
        } finally {
            UnicastRemoteObject.unexportObject(impl, true);
            UnicastRemoteObject.unexportObject(registro, true);
        }
    }

    private static void testarExpiracaoEQuedaDeServidor() throws Exception {
        AtomicLong agora = new AtomicLong(1000);
        ForcaServiceImpl impl = new ForcaServiceImpl(0, () -> "BANANA",
                agora::get, 2000L);
        ForcaRemote stub = (ForcaRemote) UnicastRemoteObject.toStub(impl);
        try {
            Conexao c1 = stub.conectar("Um");
            Conexao c2 = stub.conectar("Dois");
            agora.addAndGet(1500);
            stub.consultarEstado(c1.token()); // C1 envia heartbeat
            agora.addAndGet(1000);
            EstadoPublico estado = stub.consultarEstado(c1.token());
            conferir(estado.fase() == Fase.INTERROMPIDA
                    && estado.jogadoresConectados() == 1, "Timeout detectado");
            boolean expirada = false;
            try {
                stub.consultarEstado(c2.token());
            } catch (IllegalArgumentException ex) {
                expirada = true;
            }
            conferir(expirada, "Sessao antiga rejeitada");
        } finally {
            UnicastRemoteObject.unexportObject(impl, true);
        }
        boolean servidorCaiu = false;
        try {
            stub.conectar("Tres");
        } catch (RemoteException ex) {
            servidorCaiu = true;
        }
        conferir(servidorCaiu, "Falha de rede apos queda do servidor");
        System.out.println("OK: timeout de jogador e indisponibilidade do servidor");
    }

    private static void conferir(boolean condicao, String descricao) {
        if (!condicao) throw new AssertionError("FALHOU: " + descricao);
        verificacoes++;
        System.out.println("  [OK] " + descricao);
    }
}
