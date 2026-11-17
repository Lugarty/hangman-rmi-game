package br.edu.ucsal.forca.server;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.function.Consumer;

/** Inicializacao do servidor e publicacao do servico no Registry. */
public final class ForcaServerApp {
    private ForcaServerApp() { }

    public static void main(String[] args) throws Exception {
        int portaRegistry = args.length > 0 ? Integer.parseInt(args[0]) : 1099;
        String hostAnunciado = args.length > 1 ? args[1] : "";
        int portaObjeto = args.length > 2 ? Integer.parseInt(args[2]) : 1100;

        iniciarServidor(portaRegistry, hostAnunciado, portaObjeto);
        System.out.println("Use Ctrl+C para encerrar o servidor.");
    }

    /** Inicia o Registry RMI e publica o servico para servidores de linha de comando e GUI. */
    public static Registry iniciarServidor(
            int portaRegistry, String hostAnunciado, int portaObjeto) throws Exception {
        return iniciarServidor(portaRegistry, hostAnunciado, portaObjeto, mensagem -> { });
    }

    /** Inicia o servidor e encaminha os eventos para o registro visual opcional do host. */
    public static Registry iniciarServidor(int portaRegistry, String hostAnunciado,
                                            int portaObjeto, Consumer<String> registroEventos)
            throws Exception {
        validarPorta(portaRegistry);
        validarPorta(portaObjeto);
        if (registroEventos == null) {
            throw new IllegalArgumentException("O registro de eventos nao pode ser nulo.");
        }
        if (portaRegistry == portaObjeto) {
            throw new IllegalArgumentException("Registry e objeto remoto devem usar portas diferentes.");
        }
        if (hostAnunciado != null && !hostAnunciado.isBlank()) {
            System.setProperty("java.rmi.server.hostname", hostAnunciado);
        }

        Registry registry = LocateRegistry.createRegistry(portaRegistry);
        registry.rebind("ForcaService", new ForcaServiceImpl(portaObjeto, registroEventos));

        registrarEvento("Servidor da Forca iniciado.", registroEventos);
        registrarEvento(String.format("RMI Registry: porta %d | objeto remoto: porta %d",
                portaRegistry, portaObjeto), registroEventos);
        registrarEvento("Servico registrado: ForcaService", registroEventos);
        return registry;
    }

    private static void registrarEvento(String mensagem, Consumer<String> registroEventos) {
        System.out.println(mensagem);
        registroEventos.accept(mensagem);
    }

    private static void validarPorta(int porta) {
        if (porta < 1 || porta > 65535) {
            throw new IllegalArgumentException("Porta invalida: " + porta);
        }
    }
}
