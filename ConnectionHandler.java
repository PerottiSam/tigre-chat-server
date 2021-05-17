import java.io.*;
import java.net.Socket;
import java.util.StringTokenizer;
import java.util.Vector;

public class ConnectionHandler extends Thread{
    Socket mioSocket;
    private Server server;
    private DataInputStream leggi;
    private DataOutputStream invia;
    private String io;
    private String interlcutore;
    //private final Boolean lock; //Lock per sincronizzare i thread quando eseguono operazioni sul buffer condiviso (non e' super necessario ma lo uso per sicurezza)

    public ConnectionHandler(Socket mioSocket, Server server){
        this.mioSocket = mioSocket;
        this.server = server;
        io = null;
        interlcutore = null;
        //this.lock = lock;
        try{
            leggi = new DataInputStream(mioSocket.getInputStream());
            invia = new DataOutputStream(mioSocket.getOutputStream());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public String getIo() {
        return io;
    }

    public String getInterlcutore() {
        return interlcutore;
    }

    @Override
    public void run() {
        inAscoltoDaMioClient();
    }

    public void inAscoltoDaMioClient(){
        String pacchetto;
        StringTokenizer st;
        Vector<ConnectionHandler> listaSocket;

        try {
            while (!(pacchetto = leggi.readLine()).equals("EXIT")){
                if (pacchetto.startsWith("LOGN")){
                    st = new StringTokenizer(pacchetto, ";");
                    st.nextToken();
                    String user = st.nextToken();
                    String psw = st.nextToken();

                    boolean login = checkLogin(user, psw);
                    send(login + ""); //Dico se ha effettuato l'accesso o meo

                    if (login){
                        io = user;

                        //Invio gli allegati che sono arrivati quando ero offline
                        inviaAllegatiRimastiSulServer();
                        /* **************************************** */
                        //Invio tutti i messsaggi che mi sono arrivati quando ero offline
                        inviaMessaggiRimastiSulServer();
                        /* ***************************************** */

                        //Invio il mio status a tutti coloro che mi hanno come contatto selezionato (interlocutore)

                        listaSocket = server.getListaSocket();
                        for (int i = 0; i < listaSocket.size(); i++) {
                            ConnectionHandler ch = listaSocket.get(i);
                            if (ch != null){
                                try{
                                    String suoInterlocutore = ch.getInterlcutore();
                                    if (suoInterlocutore != null) {
                                        if (suoInterlocutore.equals(io)){
                                            ch.send("STATUS;" + getIo() + ";true");
                                        }
                                    }
                                }catch (Exception exxx){}
                            }
                        }

                    }
                }else if (pacchetto.startsWith("SIGN")){
                    st = new StringTokenizer(pacchetto, ";");
                    st.nextToken();
                    String user = st.nextToken();
                    String psw = st.nextToken();
                    boolean signUp = checkSingup(user, psw);
                    send(signUp + "");
                }else if (pacchetto.startsWith("MSSG") || pacchetto.startsWith("ALGT")){
                    st = new StringTokenizer(pacchetto, ";");
                    st.nextToken(); //MSSG || ALGT
                    st.nextToken(); //IO
                    String destinatario = st.nextToken();   //DESTINATARIO
                    String msg = st.nextToken();    //Messaggio || nomeFile
                    boolean inviato = false;


                    listaSocket = server.getListaSocket();
                    for (ConnectionHandler connectionHandler : listaSocket) {
                        if (connectionHandler!=null){
                            try{
                                if (connectionHandler.getIo().equals(destinatario)){
                                    connectionHandler.send(pacchetto);
                                    inviato = true;
                                    break;
                                }
                            }catch (Exception e){}
                        }
                    }

                    //Se non e' ancora stato inviato
                    if (!inviato){
                        scriviSuFileMessaggiInviatiQuandoNonEraOnlineIlDestinatario(pacchetto, destinatario);
                    }
                }else if(pacchetto.startsWith("ADCT")){
                    //Se esiste salva sul file dei contatti
                    System.out.println("Arrivato pacchetto ADCT: " + pacchetto);
                    st = new StringTokenizer(pacchetto, ";");
                    st.nextToken();
                    String userContatto = st.nextToken();
                    boolean esiste = userPresente(userContatto);
                    send(pacchetto + ";" + esiste);

                }else if (pacchetto.startsWith("FILE")){
                    st = new StringTokenizer(pacchetto, ";");
                    st.nextToken();
                    st.nextToken();
                    String destinatario = st.nextToken();
                    String nomeFile = st.nextToken();
                    int length = (int) Long.parseLong(st.nextToken());

                    boolean inviato = false;
                    synchronized (server.getLock()){
                        listaSocket = server.getListaSocket();
                        DataOutputStream socketDest;
                        for (ConnectionHandler connectionHandler : listaSocket) {
                            if (connectionHandler!=null){
                                try{
                                    if (connectionHandler.getIo().equals(destinatario)){

                                        connectionHandler.send(pacchetto);
                                        socketDest = connectionHandler.invia;

                                        byte[] bytes = new byte[1024];
                                        int count;
                                        int qtArrivata = 0;
                                        do{
                                            count = leggi.read(bytes);
                                            socketDest.write(bytes, 0, count);
                                            qtArrivata += count;
                                        }while (qtArrivata < length);
                                        socketDest = null;
                                        inviato = true;
                                        break;
                                    }
                                }catch (Exception ex){}
                            }
                        }
                    }

                    if(!inviato){
                        String path = "DATABASE/" + destinatario + "/" ;
                        OutputStream out = new FileOutputStream(path + nomeFile);
                        byte[] bytes = new byte[1024];
                        int count;
                        int qtArrivata = 0;
                        do{
                            count = leggi.read(bytes);
                            out.write(bytes, 0, count);
                            qtArrivata += count;
                        }while (qtArrivata < length);
                        out.close();
                        scriviSuFileAllegatoNonInviato(destinatario, nomeFile);
                    }
                }else if(pacchetto.startsWith("INTRL")){
                    System.out.println("Pacchetto interlocutore arrivato: " + pacchetto);
                    st = new StringTokenizer(pacchetto, ";");
                    st.nextToken();
                    interlcutore = st.nextToken();

                    boolean pacchettoInviato = false;

                    listaSocket = server.getListaSocket();
                    for (int i = 0; i < listaSocket.size(); i++) {
                        ConnectionHandler cH = listaSocket.get(i);
                        if (cH != null) {
                            String suoIo = cH.getIo();
                            if (suoIo != null && suoIo.equals(interlcutore)) {
                                send("STATUS;" + interlcutore + ";true");
                                System.out.println("Ho inviato " + interlcutore + " true");
                                pacchettoInviato = true;
                                break;
                            }
                        }
                    }


                    if(!pacchettoInviato){
                        send("STATUS;" + interlcutore + ";false");
                        System.out.println("Ho inviato " + interlcutore +" false");
                    }

                }
            }



            /* ***************************  PROCEDIMENTO CHIUSURA *************************  */
            if(io!=null){
                //Invio il mio status a tutti coloro che mi hanno come contatto selezionato (interlocutore)
                listaSocket = server.getListaSocket();
                for (int i = 0; i < listaSocket.size(); i++) {
                    ConnectionHandler ch = listaSocket.get(i);
                    if (ch != null){
                        try{
                            String suoInterlocutore = ch.getInterlcutore();
                            if (suoInterlocutore != null) {
                                if (suoInterlocutore.equals(io)){
                                    ch.send("STATUS;" + getIo() + ";false");
                                }
                            }
                        }catch (Exception exxx){}
                    }
                }
                send("CHIUDI-THREAD");

            }

            synchronized (server.getLock()){
                listaSocket = server.getListaSocket();
                for (int i = 0; i < listaSocket.size(); i++) {
                    ConnectionHandler connectionHandler = listaSocket.get(i);
                    if(connectionHandler!=null){
                        if (connectionHandler.equals(this)){
                            listaSocket.set(i, null);
                        }
                    }
                }
            }

            
            mioSocket.close();
            io = null;
            System.out.println("Client disconnesso");
            /* ******************************************************  */
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public synchronized void send(String msg){
        try {
            invia.writeBytes(msg + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void scriviSuFileAllegatoNonInviato(String destinatario, String nomeFile){
        try{
            FileWriter fw = new FileWriter("DATABASE/" + destinatario + "/Allegati.csv", true);
            PrintWriter scrivi = new PrintWriter(fw);
            scrivi.println("FILE;" + io + ";" + destinatario + ";" + nomeFile);
            fw.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void inviaMessaggiRimastiSulServer(){
        try{
            FileReader fr = new FileReader("DATABASE/" + io + "/messaggi.csv");
            BufferedReader leggi = new BufferedReader(fr);
            String pacchettoMessaggio;

            pacchettoMessaggio = leggi.readLine();
            while (pacchettoMessaggio!=null){
                send(pacchettoMessaggio);
                pacchettoMessaggio = leggi.readLine();
            }
            fr.close();
            new File("DATABASE/" + io + "/messaggi.csv").delete();
        }catch (FileNotFoundException e){}
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void inviaAllegatiRimastiSulServer(){
        try{
            FileReader fr = new FileReader("DATABASE/" + io + "/Allegati.csv");
            BufferedReader leggi = new BufferedReader(fr);
            String pacchettoFile;
            StringTokenizer st;

            pacchettoFile = leggi.readLine();
            while (pacchettoFile!=null){
                send(pacchettoFile);
                st = new StringTokenizer(pacchettoFile, ";");

                /* ***INVIO IL FILE *** */
                st.nextToken();
                st.nextToken();
                st.nextToken();
                String nomeFile = st.nextToken();

                File allegato = new File("DATABASE/" + io + "/" + nomeFile);
                byte[] bytes = new byte[1024];
                InputStream fin = new FileInputStream(allegato);
                int count;
                while ((count = fin.read(bytes)) > 0) {
                    invia.write(bytes, 0, count);
                }
                fin.close();
                /* ********************* */
                allegato.delete();

                pacchettoFile = leggi.readLine();
            }
            fr.close();
            //Adesso elimino il file degli allegati
            new File("DATABASE/" + io + "/Allegati.csv").delete();
        }catch (FileNotFoundException e){}
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void scriviSuFileMessaggiInviatiQuandoNonEraOnlineIlDestinatario(String pacchetto, String destinatario){
        try{
            FileWriter fw = new FileWriter("DATABASE/" + destinatario + "/messaggi.csv", true);
            PrintWriter scrivi = new PrintWriter(fw);

            scrivi.println(pacchetto);
            fw.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public boolean checkLogin(String user, String psw){
        try{
            FileReader fr = new FileReader("DATABASE/Credentials.csv");
            BufferedReader leggi = new BufferedReader(fr);
            String s;
            StringTokenizer st;

            s = leggi.readLine();
            while (s!=null){
                st = new StringTokenizer(s, ";");
                String username = st.nextToken();
                String password = st.nextToken();

                if (username.equals(user) && password.equals(psw)){
                    return !userOnline(user);   //Se e' gia online restituisco false, altrimenti true
                }
                s = leggi.readLine();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }

    public boolean checkSingup(String user, String psw){
        if (userPresente(user)){
            return false;   //Non puo' registrarsi con questo username, e' gia presente
        }

        aggiungiUtente(user, psw);
        new File("DATABASE/" + user).mkdir();
        return true;
    }

    public boolean userPresente(String user){
        try{
            FileReader fr = new FileReader("DATABASE/Credentials.csv");
            BufferedReader leggi = new BufferedReader(fr);
            String s;
            StringTokenizer st;

            s = leggi.readLine();
            while (s!=null){
                st = new StringTokenizer(s, ";");
                if (st.nextToken().equals(user))
                    return true;
                s = leggi.readLine();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }

    public void aggiungiUtente(String username, String psw){
        try{
            FileWriter fw = new FileWriter("DATABASE/Credentials.csv", true);
            PrintWriter scrivi = new PrintWriter(fw);

            scrivi.println(username + ";" + psw);
            fw.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public boolean userOnline(String user){
        Vector<ConnectionHandler> listaSocket = server.getListaSocket();
        for(ConnectionHandler connectionHandler : listaSocket){
            if (connectionHandler != null){
                String userlogged = connectionHandler.getIo();
                if(userlogged!=null){
                    if (userlogged.equals(user)){
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
