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
    Vector<String> vectorContatti;

    public ConnectionHandler(Socket mioSocket, Server server){
        this.mioSocket = mioSocket;
        this.server = server;
        io = "";
        interlcutore = null;
        vectorContatti = new Vector<>();
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

    public Vector<String> getVectorContatti() {
        return vectorContatti;
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

                        /* **************************************** */
                        //Invio tutti i messsaggi che mi sono arrivati quando ero offline
                        inviaMessaggiRimastiSulServer();
                        /* ***************************************** */
                        //Invio gli allegati che sono arrivati quando ero offline
                        inviaAllegatiRimastiSulServer();
                        //Carico il vector dei miei contatti
                        caricaVectorDiContatti();

                        //INVIO A TUTTI COLORO CHE MI HANNO TRA I CONTATTI IL MIO STATUS ONLINE
                        listaSocket = server.getListaSocket();
                        for (ConnectionHandler connectionHandler : listaSocket){
                            if (connectionHandler!=null){
                                Vector<String> vectorContattiConnectionHandler = connectionHandler.getVectorContatti();
                                if(!vectorContattiConnectionHandler.isEmpty()){
                                    if (vectorContattiConnectionHandler.contains(getIo())){ //Se la sua lista di contatti mi contiene gli mando il mio stato
                                        connectionHandler.send("STATUS;" + getIo() + ";true");
                                        break;
                                    }
                                }
                            }
                        }

                        //INVIO AL MIO CLIENT GLI STATUS DEI SUOI CONTATTI
                        boolean pacchettoStatusInviato = false;
                        if (!vectorContatti.isEmpty()){
                            for (int i = 0; i < vectorContatti.size(); i++) {
                                String destinatario = vectorContatti.get(i);
                                for (ConnectionHandler connectionHandler : listaSocket) {
                                    if (connectionHandler!=null){
                                        if (connectionHandler.getIo().equals(destinatario)){
                                            send("STATUS;" + destinatario + ";true");
                                            pacchettoStatusInviato = true;
                                            break;
                                        }
                                    }
                                }
                                //SE NON SONO PRESENTI ALL'INTERNO DELLA LISTASOCKET ALLORA SONO OFFLINE
                                if(!pacchettoStatusInviato){
                                    send("STATUS;" + destinatario + ";false");
                                }
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
                }else if (pacchetto.startsWith("MSSG")){
                    st = new StringTokenizer(pacchetto, ";");
                    st.nextToken(); //MSSG
                    st.nextToken(); //IO
                    String destinatario = st.nextToken();   //DESTINATARIO
                    String msg = st.nextToken();    //Messaggio
                    boolean inviato = false;

                    listaSocket = server.getListaSocket();
                    for (ConnectionHandler connectionHandler : listaSocket) {
                        if (connectionHandler!=null){
                            if (connectionHandler.getIo().equals(destinatario)){
                                connectionHandler.send(pacchetto);
                                inviato = true;
                                break;
                            }
                        }
                    }
                    //Se non e' ancora stato inviato
                    if (!inviato){
                        scriviSuFileMessaggiInviatiQuandoNonEraOnlineIlDestinatario(msg, destinatario, io);
                    }
                }else if(pacchetto.startsWith("ADCT")){
                    //Se esiste salva sul file dei contatti
                    st = new StringTokenizer(pacchetto, ";");
                    st.nextToken();
                    String userContatto = st.nextToken();
                    boolean esiste = userPresente(userContatto);
                    send(pacchetto + ";" + esiste);

                    if (esiste){
                        vectorContatti.add(userContatto);
                        aggiungiAllaListaContatti(userContatto);
                    }
                }else if (pacchetto.startsWith("FILE")){
                    st = new StringTokenizer(pacchetto, ";");
                    st.nextToken();
                    st.nextToken();
                    String destinatario = st.nextToken();
                    String nomeFile = st.nextToken();

                    boolean inviato = false;
                    listaSocket = server.getListaSocket();
                    DataOutputStream socketDest = null;
                    for (ConnectionHandler connectionHandler : listaSocket) {
                        if (connectionHandler!=null){
                            if (connectionHandler.getIo().equals(destinatario)){
                                connectionHandler.send(pacchetto);
                                socketDest = connectionHandler.invia;
                                inviato = true;
                                break;
                            }
                        }
                    }

                    if(inviato){
                        byte[] bytes = new byte[1024];
                        int count;
                        do{
                            count = leggi.read(bytes);
                            socketDest.write(bytes, 0, count);
                        }while (count == 1024);
                        socketDest = null;
                    }else{
                        String path = "DATABASE/" + destinatario + "/" ;
                        OutputStream out = new FileOutputStream(path + nomeFile);
                        byte[] bytes = new byte[1024];
                        int count;
                        do{
                            count = leggi.read(bytes);
                            out.write(bytes, 0, count);
                        }while (count == 1024);
                        out.close();
                        scriviSuFileAllegatoNonInviato(destinatario, nomeFile);
                    }

                }
            }

            /* ***************************  PROCEDIMENTO CHIUSURA *************************  */
            listaSocket = server.getListaSocket();
            for (ConnectionHandler connectionHandler : listaSocket){
                if (connectionHandler!=null){
                    Vector<String> vectorContattiConnectionHandler = connectionHandler.getVectorContatti();
                    if(!vectorContattiConnectionHandler.isEmpty()){
                        if (vectorContattiConnectionHandler.contains(getIo())){ //Se la sua lista di contatti mi contiene gli mando il mio stato
                            connectionHandler.send("STATUS;" + getIo() + ";false");
                            break;
                        }
                    }
                }
            }
            send("CHIUDI-THREAD");
            mioSocket.close();
            //Forse devo fare un ciclo per settare a null il mio connectionhandler nella listsocket
            listaSocket = server.getListaSocket();
            for (int i = 0; i < listaSocket.size(); i++) {
                ConnectionHandler connectionHandler = listaSocket.get(i);
                if(connectionHandler!=null){
                    String user = listaSocket.get(i).getIo();
                    if(user!=null){
                        if (user.equals(io)){
                            listaSocket.set(i, null);
                        }
                    }
                }
            }
            io = null;
            /* ******************************************************  */
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void send(String msg){
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

    public void caricaVectorDiContatti(){
        try{
            FileReader fr = new FileReader("DATABASE/" + io + "/Contatti.txt");
            BufferedReader leggi = new BufferedReader(fr);
            String s;

            s = leggi.readLine();
            while (s!=null){
                vectorContatti.add(s);
                s = leggi.readLine();
            }
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

    public void scriviSuFileMessaggiInviatiQuandoNonEraOnlineIlDestinatario(String msg, String destinatario, String io){
        try{
            FileWriter fw = new FileWriter("DATABASE/" + destinatario + "/messaggi.csv", true);
            PrintWriter scrivi = new PrintWriter(fw);

            scrivi.println("MSSG;" + io + ";" + destinatario + ";" + msg);
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
                if (connectionHandler.getIo().equals(user)){
                    return true;
                }
            }
        }
        return false;
    }

    public void aggiungiAllaListaContatti(String userContatto){
        try{
            FileWriter fw = new FileWriter("DATABASE/" + io + "/Contatti.csv", true);
            PrintWriter scrivi = new PrintWriter(fw);
            scrivi.println(userContatto + ";");
            fw.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
