package br.edu.ucsal.forca.client;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/** Guarda as mensagens recentes do servidor e as encaminha para a tela do host. */
public final class ServerActivityLog implements Consumer<String> {
    private static final int LIMITE_EVENTOS = 200;
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Deque<String> eventos = new ArrayDeque<>();
    private Consumer<String> ouvinte;

    @Override
    public synchronized void accept(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) return;

        String evento = "[" + LocalTime.now().format(FORMATO_HORA) + "] " + mensagem;
        if (eventos.size() == LIMITE_EVENTOS) eventos.removeFirst();
        eventos.addLast(evento);
        if (ouvinte != null) ouvinte.accept(evento);
    }

    /** Liga o registro à janela e mostra os eventos ocorridos antes da abertura dela. */
    public synchronized void setOuvinte(Consumer<String> ouvinte) {
        this.ouvinte = ouvinte;
        if (ouvinte != null) {
            for (String evento : eventos) ouvinte.accept(evento);
        }
    }
}
