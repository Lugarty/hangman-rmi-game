package br.edu.ucsal.forca.client;

import br.edu.ucsal.forca.server.ForcaServerApp;
import java.rmi.registry.Registry;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Tela inicial que encaminha para o servidor RMI ou para o cliente do jogo. */
public final class ForcaLauncherApp {
    private static Registry registryHospedado;

    private ForcaLauncherApp() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ForcaLauncherApp::escolherModo);
    }

    private static void escolherModo() {
        Object[] opcoes = {"Hospedar partida", "Entrar em partida"};
        int escolha = JOptionPane.showOptionDialog(
                null,
                "Escolha como deseja jogar.",
                "Jogo da Forca",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                opcoes,
                opcoes[0]);

        if (escolha == 0) {
            hospedarPartida();
        } else if (escolha == 1) {
            entrarEmPartida();
        }
    }

    private static void hospedarPartida() {
        String ipHost = JOptionPane.showInputDialog(
                null,
                "Informe o IP deste computador (host):",
                "Hospedar partida",
                JOptionPane.QUESTION_MESSAGE);
        if (ipHost == null || ipHost.isBlank()) {
            return;
        }

        try {
            ServerActivityLog registroServidor = new ServerActivityLog();
            registryHospedado = ForcaServerApp.iniciarServidor(
                    1099, ipHost.strip(), 1100, registroServidor);
            registroServidor.accept("IP do host: " + ipHost.strip());
            JOptionPane.showMessageDialog(
                    null,
                    "Servidor iniciado. Passe este IP para o outro jogador:\n"
                            + ipHost.strip() + "\n\nMantenha esta janela aberta.",
                    "Partida hospedada",
                    JOptionPane.INFORMATION_MESSAGE);
            ForcaClientApp.abrirComoHost("Jogador", "127.0.0.1", 1099, registroServidor);
        } catch (Exception erro) {
            String detalhe = erro.getMessage() == null
                    ? erro.getClass().getSimpleName() : erro.getMessage();
            JOptionPane.showMessageDialog(
                    null,
                    "Nao foi possivel abrir o servidor. Confira se outra instancia ja esta usando "
                            + "as portas 1099 ou 1100.\n\n" + detalhe,
                    "Erro ao hospedar",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void entrarEmPartida() {
        String host = JOptionPane.showInputDialog(
                null,
                "Informe o IP do host:",
                "Entrar em partida",
                JOptionPane.QUESTION_MESSAGE);
        if (host == null || host.isBlank()) {
            return;
        }
        ForcaClientApp.abrir("Jogador", host.strip(), 1099);
    }
}
