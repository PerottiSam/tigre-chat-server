import java.io.BufferedReader;
import java.io.InputStreamReader;

public class SpegniServer extends Thread{
    Server server;

    public SpegniServer(Server server){
        this.server = server;
    }

    @Override
    public void run() {
        try{
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

            while (!in.readLine().equalsIgnoreCase("quit")){ }

            //PROCEDURA CHIUSURA DI TUTTI I THREAD ANCHE DEI RISPETTIVI THREAD DI LETTURA DEI CLIENT TRAMITE L'INVIO DEL MESSAGGIO CHIUDI-THREAD
            server.questoServer.close();
            for (int i = 0; i < server.listaSocket.size(); i++) {
                try{
                    server.listaSocket.get(i).send("CHIUDI-THREAD");
                    server.listaSocket.get(i).mioSocket.close();
                }catch (Exception ex){}
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
