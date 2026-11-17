package br.edu.ucsal.forca.shared;

import java.io.Serial;
import java.io.Serializable;

public record Conexao(boolean aceita, int numeroJogador, String token, String mensagem)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
