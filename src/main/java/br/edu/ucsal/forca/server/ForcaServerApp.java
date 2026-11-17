package br.edu.ucsal.forca.server;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/** Inicializacao do servidor e publicacao do servico no Registry. */
public final class ForcaServerApp {
    private ForcaServerApp() { }

    public static void main(String[] args) throws Exception {
        int portaRegistry = args.length > 0 ? Integer.parseInt(args[0]) : 1099;
        String hostAnunciado = args.length > 1 ? args[1] : "";
        int portaObjeto = args.length > 2 ? Integer.parseInt(args[2]) : 1100;

        validarPorta(portaRegistry);
        validarPorta(portaObjeto);
        if (portaRegistry == portaObjeto) {
            throw new IllegalArgumentException("Registry e objeto remoto devem usar portas diferentes.");
        }
        if (!hostAnunciado.isBlank()) {
            System.setProperty("java.rmi.server.hostname", hostAnunciado);
        }

        Registry registry = LocateRegistry.createRegistry(portaRegistry);
        registry.rebind("ForcaService", new ForcaServiceImpl(portaObjeto));

        System.out.println("Servidor da Forca iniciado.");
        System.out.printf("RMI Registry: porta %d | objeto remoto: porta %d%n",
                portaRegistry, portaObjeto);
        System.out.println("Servico registrado: ForcaService");
        System.out.println("Use Ctrl+C para encerrar o servidor.");
    }

    private static void validarPorta(int porta) {
        if (porta < 1 || porta > 65535) {
            throw new IllegalArgumentException("Porta invalida: " + porta);
        }
    }
}
