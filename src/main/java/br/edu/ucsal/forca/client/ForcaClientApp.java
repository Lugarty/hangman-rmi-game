package br.edu.ucsal.forca.client;

import br.edu.ucsal.forca.shared.Conexao;
import br.edu.ucsal.forca.shared.EstadoPublico;
import br.edu.ucsal.forca.shared.Fase;
import br.edu.ucsal.forca.shared.ForcaRemote;
import br.edu.ucsal.forca.shared.ResultadoAcao;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Adaptador Swing/RMI. A rede executa fora da EDT; o servidor valida o jogo.
 */
public final class ForcaClientApp {
    private record Sessao(ForcaRemote servico, String token, int numero,
                          AtomicLong ultimaVersao) { }

    private ForcaClientApp() { }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 1099;
        String nome = args.length > 2 ? args[2] : "Jogador";
        abrir(nome, host, porta);
    }

    /** Abre o cliente grafico com os dados de conexao inicial ja preenchidos. */
    public static void abrir(String nome, String host, int porta) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // LAF nativo e opcional.
        }
        SwingUtilities.invokeLater(() -> abrirJanela(nome, host, porta));
    }

    private static void abrirJanela(String nomeInicial, String hostInicial, int portaInicial) {
        ForcaWindow janela = new ForcaWindow(nomeInicial, hostInicial, portaInicial);
        AtomicReference<Sessao> atual = new AtomicReference<>();
        AtomicBoolean conectando = new AtomicBoolean();
        AtomicBoolean enviandoJogada = new AtomicBoolean();
        AtomicBoolean reiniciando = new AtomicBoolean();

        ScheduledExecutorService atualizacoes = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "forca-consulta-remota");
            t.setDaemon(true);
            return t;
        });

        janela.setConnectionHandler((host, porta) -> {
            if (atual.get() != null) {
                janela.showFeedback("Voce ja esta conectado.");
                return;
            }
            if (!conectando.compareAndSet(false, true)) return;

            String nome = janela.getNomeJogador();
            janela.showFeedback("Conectando...");
            CompletableFuture.runAsync(() -> {
                try {
                    Registry registry = LocateRegistry.getRegistry(host, porta);
                    ForcaRemote servico = (ForcaRemote) registry.lookup("ForcaService");
                    Conexao conexao = servico.conectar(nome);
                    if (!conexao.aceita()) {
                        janela.showFeedback(conexao.mensagem());
                        return;
                    }

                    Sessao sessao = new Sessao(servico, conexao.token(),
                            conexao.numeroJogador(), new AtomicLong(-1));
                    if (!atual.compareAndSet(null, sessao)) {
                        servico.sair(conexao.token());
                        return;
                    }
                    janela.setConnected(true);
                    janela.setPlayerNumber(sessao.numero());
                    janela.showFeedback(conexao.mensagem());
                    aplicarEstado(janela, atual, sessao,
                            servico.consultarEstado(sessao.token()));
                } catch (Exception ex) {
                    janela.showFeedback("Erro ao conectar: " + resumirErro(ex));
                } finally {
                    conectando.set(false);
                }
            });
        });

        janela.setLetterHandler(letra -> {
            Sessao sessao = atual.get();
            if (sessao == null || !enviandoJogada.compareAndSet(false, true)) return;
            CompletableFuture.runAsync(() -> {
                try {
                    ResultadoAcao resultado = sessao.servico().tentarLetra(sessao.token(), letra);
                    aplicarEstado(janela, atual, sessao, resultado.estado());
                    if (atual.get() == sessao) janela.showFeedback(resultado.mensagem());
                } catch (Exception ex) {
                    perderConexao(janela, atual, sessao, ex);
                } finally {
                    enviandoJogada.set(false);
                }
            });
        });

        janela.setRestartHandler(() -> {
            Sessao sessao = atual.get();
            if (sessao == null || !reiniciando.compareAndSet(false, true)) return;
            CompletableFuture.runAsync(() -> {
                try {
                    ResultadoAcao resultado = sessao.servico().solicitarReinicio(sessao.token());
                    aplicarEstado(janela, atual, sessao, resultado.estado());
                    if (atual.get() == sessao) janela.showFeedback(resultado.mensagem());
                } catch (Exception ex) {
                    perderConexao(janela, atual, sessao, ex);
                } finally {
                    reiniciando.set(false);
                }
            });
        });

        janela.setLeaveHandler(() -> {
            Sessao sessao = atual.getAndSet(null);
            limparTela(janela, "Voce saiu da sala.");
            if (sessao != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        sessao.servico().sair(sessao.token());
                    } catch (Exception ignored) {
                        // A expiração de sessão cobre quedas de rede.
                    }
                });
            }
        });

        atualizacoes.scheduleWithFixedDelay(() -> {
            Sessao sessao = atual.get();
            if (sessao == null) return;
            try {
                EstadoPublico estado = sessao.servico().consultarEstado(sessao.token());
                aplicarEstado(janela, atual, sessao, estado);
            } catch (Exception ex) {
                perderConexao(janela, atual, sessao, ex);
            }
        }, 0, 800, TimeUnit.MILLISECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            atualizacoes.shutdownNow();
            Sessao sessao = atual.getAndSet(null);
            if (sessao != null) {
                try {
                    sessao.servico().sair(sessao.token());
                } catch (Exception ignored) {
                    // Falha de rede durante o encerramento.
                }
            }
        }, "forca-saida"));

        janela.setVisible(true);
    }

    private static void aplicarEstado(ForcaWindow janela, AtomicReference<Sessao> atual,
                                     Sessao sessao, EstadoPublico estado) {
        // A verificacao e a apresentacao precisam ocorrer juntas na EDT: caso
        // contrario respostas de threads diferentes podem inverter a ordem visual.
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> aplicarEstado(janela, atual, sessao, estado));
            return;
        }
        if (atual.get() != sessao) return;
        long anterior = sessao.ultimaVersao().getAndUpdate(
                v -> Math.max(v, estado.versao()));
        if (anterior > estado.versao()) return;

        boolean ativo = estado.fase() == Fase.EM_ANDAMENTO;
        boolean minhaVez = ativo && estado.jogadorDaVez() == sessao.numero();
        String turno = ativo ? "Jogador " + estado.jogadorDaVez() : "-";
        String fase = switch (estado.fase()) {
            case AGUARDANDO -> "Aguardando outro jogador...";
            case EM_ANDAMENTO -> minhaVez ? "Sua vez de jogar!" : "Vez do outro jogador.";
            case VITORIA -> "Vitoria dos dois jogadores!";
            case DERROTA -> "Fim de jogo. A palavra nao foi descoberta.";
            case INTERROMPIDA -> "Partida interrompida: outro jogador desconectou.";
        };
        janela.renderPublicState(estado.palavraMascarada(), estado.letrasTentadas(),
                estado.erros(), estado.limiteErros(), turno, fase, minhaVez);
        boolean terminou = estado.fase() == Fase.VITORIA || estado.fase() == Fase.DERROTA;
        janela.setRestartAvailable(terminou, estado.meuVotoReinicio());
        if (terminou || estado.fase() == Fase.INTERROMPIDA) {
            janela.showFeedback(estado.mensagem());
        }
    }

    private static void perderConexao(ForcaWindow janela, AtomicReference<Sessao> atual,
                                     Sessao sessao, Exception erro) {
        if (atual.compareAndSet(sessao, null)) {
            limparTela(janela, "Conexao perdida: " + resumirErro(erro));
        }
    }

    private static void limparTela(ForcaWindow janela, String mensagem) {
        janela.setConnected(false);
        janela.renderPublicState("-", "", 0, 6, "-", "Desconectado", false);
        janela.setRestartAvailable(false, false);
        janela.showFeedback(mensagem);
    }

    private static String resumirErro(Exception erro) {
        if (erro.getMessage() != null && !erro.getMessage().isBlank()) {
            return erro.getMessage();
        }
        return erro.getClass().getSimpleName();
    }
}
