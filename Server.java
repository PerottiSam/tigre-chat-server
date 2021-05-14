import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;

public class Server {
    private final int port;
    Vector<ConnectionHandler> listaSocket;
    ServerSocket questoServer;

    public Server(int port) {
        this.port = port;
        listaSocket = new Vector<>(20, 5);
    }

    public void inizializzaServer(){
        try {
            System.out.println("Inizializzo il server...");
            questoServer = new ServerSocket(port);
            System.out.println("Server pronto in ascolto sulla porta " + port);
            new SpegniServer(this).start();
            while (true){
                Socket socketClient = questoServer.accept();
                ConnectionHandler connectionHandler = new ConnectionHandler(socketClient, this);
                connectionHandler.start();
                System.out.println("Connesso con un cliet");

                /*******AGGIUNGO QUESTO CONNECTIONHANDLER ALLA LISTA*************/
                boolean aggiunto = false;
                for (int i = 0; i < listaSocket.size(); i++) {
                    if (listaSocket.get(i) == null){
                        listaSocket.set(i, connectionHandler);
                        //Rimpiazzato
                        aggiunto = true;
                        break;
                    }
                }

                //Nel caso non l'abbia rimpiazzato
                if(!aggiunto){
                    listaSocket.add(connectionHandler);
                    //Aggiunto
                }
                /***************************************************************/
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Vector<ConnectionHandler> getListaSocket() {
        return listaSocket;
    }
}
