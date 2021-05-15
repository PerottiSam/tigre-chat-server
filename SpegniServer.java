import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Vector;

public class SpegniServer extends Thread{
    Server server;

    public SpegniServer(Server server){
        this.server = server;
    }

    @Override
    public void run() {
        try{
            String comando;
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            Vector<ConnectionHandler> lista;


            while (!(comando = in.readLine()).equalsIgnoreCase("quit")){
                if(comando.equalsIgnoreCase("online")){
                    lista = server.getListaSocket();
                    for (int i = 0; i < lista.size(); i++) {
                        if (lista.get(i) != null){
                            System.out.println("User: " + lista.get(i).getIo());
                        }
                    }
                }else if (comando.equalsIgnoreCase("ip")){
                    lista = server.getListaSocket();
                    for (int i = 0; i < lista.size(); i++) {
                        if (lista.get(i) != null){
                            System.out.println("Socket: " + lista.get(i).mioSocket);
                        }
                    }
                }
            }

            //PROCEDURA CHIUSURA DI TUTTI I THREAD ANCHE DEI RISPETTIVI THREAD DI LETTURA DEI CLIENT TRAMITE L'INVIO DEL MESSAGGIO CHIUDI-THREAD
            try{
                server.questoServer.close();
            }catch (Exception exxx){}

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
