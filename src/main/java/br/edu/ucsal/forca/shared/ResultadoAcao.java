package br.edu.ucsal.forca.shared;

import java.io.Serial;
import java.io.Serializable;

/** Resultado de uma tentativa de letra ou pedido de nova partida. */
public record ResultadoAcao(boolean aceita, String mensagem, EstadoPublico estado)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
