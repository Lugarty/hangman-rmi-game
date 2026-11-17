package br.edu.ucsal.forca.client;

import br.edu.ucsal.forca.shared.Conexao;
import br.edu.ucsal.forca.shared.EstadoPublico;
import br.edu.ucsal.forca.shared.ForcaRemote;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/** Utilitario de teste para iniciar clientes independentes sem abrir janelas. */
public final class ForcaConnectionProbe {
    private ForcaConnectionProbe() { }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 1099;
        String nome = args.length > 2 ? args[2] : "Teste";
        long esperaMs = args.length > 3 ? Long.parseLong(args[3]) : 2000;

        Registry registro = LocateRegistry.getRegistry(host, porta);
        ForcaRemote servico = (ForcaRemote) registro.lookup("ForcaService");
        Conexao resposta = servico.conectar(nome);
        System.out.println("CONEXAO: " + resposta.mensagem());

        if (!resposta.aceita()) {
            return;
        }

        try {
            EstadoPublico antes = servico.consultarEstado(resposta.token());
            System.out.printf("ANTES: %s | %d jogador(es)%n",
                    antes.fase(), antes.jogadoresConectados());
            Thread.sleep(esperaMs);
            EstadoPublico depois = servico.consultarEstado(resposta.token());
            System.out.printf("DEPOIS: %s | %d jogador(es)%n",
                    depois.fase(), depois.jogadoresConectados());
        } finally {
            servico.sair(resposta.token());
            System.out.println("SAIDA: desconectado.");
        }
    }
}
