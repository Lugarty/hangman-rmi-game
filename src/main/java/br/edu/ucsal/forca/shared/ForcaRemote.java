package br.edu.ucsal.forca.shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

/** Contrato do servico RMI. O servidor e a unica fonte de verdade. */
public interface ForcaRemote extends Remote {
    Conexao conectar(String nome) throws RemoteException;
    EstadoPublico consultarEstado(String token) throws RemoteException;
    ResultadoAcao tentarLetra(String token, char letra) throws RemoteException;
    ResultadoAcao solicitarReinicio(String token) throws RemoteException;
    void sair(String token) throws RemoteException;
}
