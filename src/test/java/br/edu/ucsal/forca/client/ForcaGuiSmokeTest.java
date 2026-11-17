package br.edu.ucsal.forca.client;

import br.edu.ucsal.forca.client.ForcaClientApp;
import br.edu.ucsal.forca.client.ForcaWindow;
import br.edu.ucsal.forca.server.ForcaServiceImpl;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicReference;

public final class ForcaGuiSmokeTest {
    @SuppressWarnings("unchecked")
    static <T> T field(ForcaWindow w, String name) throws Exception {
        Field f = ForcaWindow.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(w);
    }
    static void ui(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }
    static void check(boolean b, String msg) { if (!b) throw new AssertionError(msg); System.out.println("[OK] "+msg); }
    static ForcaWindow[] windows() {
        return java.util.Arrays.stream(JFrame.getFrames()).filter(w -> w instanceof ForcaWindow)
                .map(w -> (ForcaWindow)w).toArray(ForcaWindow[]::new);
    }
    static void waitUntil(java.util.concurrent.Callable<Boolean> test, String msg) throws Exception {
        long until = System.currentTimeMillis() + 7000;
        while(System.currentTimeMillis() < until) {
            AtomicReference<Boolean> ok = new AtomicReference<>(false);
            SwingUtilities.invokeAndWait(() -> {
                try { ok.set(test.call()); } catch(Exception e) { throw new RuntimeException(e); }
            });
            if(ok.get()) { check(true,msg); return; }
            Thread.sleep(150);
        }
        throw new AssertionError("TIMEOUT: "+msg);
    }
    public static void main(String[] args) throws Exception {
        int port;
        try(ServerSocket s = new ServerSocket(0)) { port=s.getLocalPort(); }
        System.setProperty("java.rmi.server.hostname","127.0.0.1");
        Registry reg=LocateRegistry.createRegistry(port);
        ForcaServiceImpl impl=new ForcaServiceImpl(0);
        reg.rebind("ForcaService",impl);
        try {
            ForcaClientApp.main(new String[0]);
            ForcaClientApp.main(new String[0]);
            waitUntil(() -> windows().length>=2, "Duas janelas Swing criadas");
            ForcaWindow[] ws=windows();
            for(int i=0;i<2;i++){
                final int k=i;
                ui(() -> {
                    try{
                       ((JTextField)field(ws[k],"portField")).setText(""+port);
                       ((JTextField)field(ws[k],"nomeField")).setText(k==0?"Anisio":"Bernardo");
                       ((JButton)field(ws[k],"connectButton")).doClick();
                    }catch(Exception e){throw new RuntimeException(e);}
                });
                waitUntil(() -> ((JButton)field(ws[k],"leaveButton")).isEnabled(),"Cliente Swing "+(i+1)+" conectou via botao");
            }
            waitUntil(() -> ((JButton)((Map<?,?>)field(ws[0],"letterButtons")).get('A')).isEnabled(),
                     "Jogador 1 pode tentar letra");
            waitUntil(() -> !((JButton)((Map<?,?>)field(ws[1],"letterButtons")).get('A')).isEnabled(),
                     "Jogador 2 aguarda turno");
            ui(() -> {
                try { ((JButton)((Map<?,?>)field(ws[0],"letterButtons")).get('A')).doClick(); }
                catch(Exception e){throw new RuntimeException(e);}
            });
            waitUntil(() -> ((JButton)((Map<?,?>)field(ws[1],"letterButtons")).get('B')).isEnabled(),
                     "Jogada por clique chegou ao servidor e passou turno");
            waitUntil(() -> !((JButton)((Map<?,?>)field(ws[0],"letterButtons")).get('B')).isEnabled(),
                     "Primeiro cliente deixou de ter turno");
            System.out.println("GUI_SMOKE_SUCCESS");
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        } finally {
            try{UnicastRemoteObject.unexportObject(impl,true);}catch(Exception ignored){}
            try{UnicastRemoteObject.unexportObject(reg,true);}catch(Exception ignored){}
        }
        System.exit(0);
    }
}