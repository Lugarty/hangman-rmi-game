package br.edu.ucsal.forca.shared;

import java.io.Serial;
import java.io.Serializable;

/** Visao imutavel enviada ao cliente; a mensagem revela a palavra ao ocorrer derrota. */
public record EstadoPublico(
        int jogadoresConectados,
        Fase fase,
        String palavraMascarada,
        String letrasTentadas,
        int erros,
        int limiteErros,
        int jogadorDaVez,
        boolean meuVotoReinicio,
        String mensagem,
        long versao) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
