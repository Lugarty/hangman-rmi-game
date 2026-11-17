package br.edu.ucsal.forca.client;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Interface grafica: apenas apresenta estado publico e encaminha comandos. */
public final class ForcaWindow extends JFrame {
    private final JTextField nomeField;
    private final JTextField hostField;
    private final JTextField portField;
    private final JLabel jogadorLabel = new JLabel("Nao conectado");
    private final JLabel phaseLabel = new JLabel("Aguardando conexao");
    private final JLabel turnLabel = new JLabel("Turno: -");
    private final JLabel wordLabel = new JLabel("_ _ _ _ _");
    private final JLabel mistakesLabel = new JLabel("Erros: 0 / 6");
    private final JLabel lettersLabel = new JLabel("Letras tentadas: -");
    private final JLabel feedbackLabel = new JLabel("Informe nome, host e porta e clique em Conectar.");

    private final JButton connectButton = new JButton("Conectar");
    private final JButton leaveButton = new JButton("Sair da sala");
    private final JButton restartButton = new JButton("Nova partida");
    private final HangmanDrawing desenho = new HangmanDrawing();
    private final Map<Character, JButton> letterButtons = new LinkedHashMap<>();

    private BiConsumer<String, Integer> connectionHandler;
    private Consumer<Character> letterHandler;
    private Runnable leaveHandler;
    private Runnable restartHandler;

    public ForcaWindow() {
        this("Jogador", "127.0.0.1", 1099);
    }

    public ForcaWindow(String nomeInicial, String hostInicial, int portaInicial) {
        super("Jogo da Forca | Java RMI");
        nomeField = new JTextField(nomeInicial == null || nomeInicial.isBlank()
                ? "Jogador" : nomeInicial, 10);
        hostField = new JTextField(hostInicial == null || hostInicial.isBlank()
                ? "127.0.0.1" : hostInicial, 12);
        portField = new JTextField(Integer.toString(portaInicial), 5);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1050, 650);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        root.add(connectionPanel(), BorderLayout.NORTH);
        root.add(gamePanel(), BorderLayout.CENTER);

        feedbackLabel.setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));
        root.add(feedbackLabel, BorderLayout.SOUTH);
        setContentPane(root);

        setConnected(false);
        setRestartAvailable(false, false);
    }

    private JPanel connectionPanel() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        bar.setBorder(BorderFactory.createTitledBorder("Conexao com o servidor"));
        bar.add(new JLabel("Nome:"));
        bar.add(nomeField);
        bar.add(new JLabel("Host:"));
        bar.add(hostField);
        bar.add(new JLabel("Porta:"));
        bar.add(portField);

        connectButton.addActionListener(event -> {
            String host = hostField.getText().strip();
            if (host.isBlank()) {
                showFeedback("Informe o endereco do servidor.");
                return;
            }
            int porta;
            try {
                porta = Integer.parseInt(portField.getText().strip());
                if (porta < 1 || porta > 65535) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                showFeedback("Porta invalida: use um numero de 1 a 65535.");
                return;
            }
            if (connectionHandler != null) connectionHandler.accept(host, porta);
        });
        leaveButton.addActionListener(e -> {
            if (leaveHandler != null) leaveHandler.run();
        });
        bar.add(connectButton);
        bar.add(leaveButton);
        return bar;
    }

    private JPanel gamePanel() {
        JPanel main = new JPanel(new BorderLayout(14, 14));

        JPanel top = new JPanel(new GridLayout(4, 1, 4, 6));
        jogadorLabel.setFont(jogadorLabel.getFont().deriveFont(Font.BOLD, 15f));
        phaseLabel.setFont(phaseLabel.getFont().deriveFont(Font.BOLD, 16f));
        wordLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 31));
        wordLabel.setHorizontalAlignment(SwingConstants.CENTER);
        top.add(jogadorLabel);
        top.add(phaseLabel);
        top.add(wordLabel);
        top.add(turnLabel);
        main.add(top, BorderLayout.NORTH);

        JPanel middle = new JPanel(new BorderLayout(16, 8));
        desenho.setPreferredSize(new Dimension(255, 245));
        middle.add(desenho, BorderLayout.WEST);

        JPanel details = new JPanel(new GridLayout(3, 1, 6, 8));
        details.setBorder(BorderFactory.createTitledBorder("Partida cooperativa"));
        mistakesLabel.setFont(mistakesLabel.getFont().deriveFont(Font.BOLD, 17f));
        details.add(mistakesLabel);
        details.add(lettersLabel);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(restartButton);
        details.add(actions);
        middle.add(details, BorderLayout.CENTER);
        main.add(middle, BorderLayout.CENTER);

        JPanel keyboard = new JPanel(new GridLayout(2, 13, 5, 5));
        keyboard.setBorder(BorderFactory.createTitledBorder("Escolha uma letra"));
        for (char ch = 'A'; ch <= 'Z'; ch++) {
            char letra = ch;
            JButton button = new JButton(Character.toString(ch));
            button.setEnabled(false);
            button.setFocusable(false);
            button.addActionListener(e -> {
                if (letterHandler != null) letterHandler.accept(letra);
            });
            letterButtons.put(letra, button);
            keyboard.add(button);
        }
        main.add(keyboard, BorderLayout.SOUTH);
        restartButton.addActionListener(e -> {
            if (restartHandler != null) restartHandler.run();
        });
        return main;
    }

    public String getNomeJogador() {
        return nomeField.getText().strip();
    }

    public void setConnectionHandler(BiConsumer<String, Integer> handler) {
        connectionHandler = handler;
    }

    public void setLetterHandler(Consumer<Character> handler) {
        letterHandler = handler;
    }

    public void setLeaveHandler(Runnable handler) {
        leaveHandler = handler;
    }

    public void setRestartHandler(Runnable handler) {
        restartHandler = handler;
    }

    public void setConnected(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            connectButton.setEnabled(!connected);
            leaveButton.setEnabled(connected);
            nomeField.setEnabled(!connected);
            hostField.setEnabled(!connected);
            portField.setEnabled(!connected);
            if (!connected) jogadorLabel.setText("Nao conectado");
        });
    }

    public void setPlayerNumber(int numero) {
        SwingUtilities.invokeLater(() -> jogadorLabel.setText(
                "Voce e o Jogador " + numero + " | cooperem para descobrir a palavra"));
    }

    public void setRestartAvailable(boolean available, boolean voted) {
        naEdt(() -> {
            restartButton.setEnabled(available && !voted);
            restartButton.setText(voted ? "Aguardando outro jogador..." : "Nova partida");
        });
    }

    public void renderPublicState(String maskedWord, String attemptedLetters, int mistakes,
                                  int maxMistakes, String currentTurn, String phase, boolean mayGuess) {
        naEdt(() -> {
            String tried = attemptedLetters == null
                    ? "" : attemptedLetters.toUpperCase(Locale.ROOT);
            wordLabel.setText(maskedWord == null ? "-" : maskedWord);
            lettersLabel.setText("Letras tentadas: " + (tried.isBlank() ? "-" : tried));
            mistakesLabel.setText("Erros: " + mistakes + " / " + maxMistakes);
            turnLabel.setText("Turno: " + (currentTurn == null ? "-" : currentTurn));
            phaseLabel.setText(phase == null ? "-" : phase);
            desenho.setErros(mistakes);
            for (Map.Entry<Character, JButton> entry : letterButtons.entrySet()) {
                boolean alreadyTried = tried.indexOf(entry.getKey()) >= 0;
                entry.getValue().setEnabled(mayGuess && !alreadyTried);
            }
        });
    }

    public void showFeedback(String message) {
        naEdt(() -> feedbackLabel.setText(
                message == null ? "" : message));
    }

    private static void naEdt(Runnable acao) {
        if (SwingUtilities.isEventDispatchThread()) acao.run();
        else SwingUtilities.invokeLater(acao);
    }

    private static final class HangmanDrawing extends JPanel {
        private int erros;

        HangmanDrawing() {
            setBorder(BorderFactory.createTitledBorder("Forca - 6 erros"));
            setBackground(new Color(249, 249, 252));
        }

        void setErros(int n) {
            erros = Math.max(0, Math.min(6, n));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                double scale = Math.min((getWidth() - 25) / 240.0,
                        (getHeight() - 40) / 210.0);
                g.translate((getWidth() - 240 * scale) / 2.0,
                        (getHeight() - 210 * scale) / 2.0 + 5);
                g.scale(scale, scale);
                g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(65, 78, 103));

                // Estrutura da forca, sempre visivel.
                g.drawLine(25, 195, 143, 195);
                g.drawLine(60, 195, 60, 22);
                g.drawLine(60, 22, 157, 22);
                g.drawLine(157, 22, 157, 42);

                g.setColor(new Color(175, 64, 78));
                if (erros >= 1) g.draw(new Ellipse2D.Double(136, 43, 42, 42));
                if (erros >= 2) g.drawLine(157, 85, 157, 135);
                if (erros >= 3) g.drawLine(157, 97, 126, 122);
                if (erros >= 4) g.drawLine(157, 97, 188, 122);
                if (erros >= 5) g.drawLine(157, 135, 131, 170);
                if (erros >= 6) g.drawLine(157, 135, 183, 170);
            } finally {
                g.dispose();
            }
        }
    }
}
