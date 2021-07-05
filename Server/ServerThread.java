import java.io.*;
import java.net.*;
import java.nio.Buffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class ServerThread extends Thread {
    private String type;
    private int port;
    private Socket socket = null;
    private final String CRLF = "\r\n";
    private String clientAddr;
    private String serverAddr;
    private ArrayList<Domain> domains;
    private String domain;

    public ServerThread(String type, int port) {
        super("ServerThread");
        this.type = type;
        this.port = port;
    }

    public ServerThread(String type, int port, String domain, ArrayList<Domain> domains) {
        super("ServerThread");
        this.type = type;
        this.port = port;
        this.domain = domain;
        this.domains = domains;
    }

    public ServerThread(String type, Socket socket, String serverAddr) {
        super(type + "ServerThread");
        this.type = type;
        this.socket = socket;
        clientAddr = socket.getInetAddress().getHostAddress();
        this.serverAddr = serverAddr;
    }

    public ServerThread(String type, Socket socket, String serverAddr, String domain, ArrayList<Domain> domains) {
        super(type + "ServerThread");
        this.type = type;
        this.socket = socket;
        clientAddr = socket.getInetAddress().getHostAddress();
        this.serverAddr = serverAddr;
        this.domain = domain;
        this.domains = domains;
    }

    public void run() {
        if (socket == null) {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                serverAddr = InetAddress.getLocalHost().getHostAddress();
                // DEBUG
                System.out.println(type + " server started on port " + port);
                while (true) {
                    if (type.equals("SMTP")) {
                        new ServerThread(type, serverSocket.accept(), serverAddr, domain, domains).start();
                    } else {
                        new ServerThread(type, serverSocket.accept(), serverAddr).start();
                    }
                    System.out.println(type + " thread started on port " + port);
                }
            } catch (IOException e) {
                System.err.println("Could not listen on port " + port);
                System.exit(1);
            }
        } else {
            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));) {
                String inputLine;
                switch (type) {
                    case "HTTP":
                        HTTP http = new HTTP(serverAddr, clientAddr, domain);
                        // read client data
                        //System.out.println("HTTP waiting...");
                        while ((inputLine = in.readLine()) != null) {
                            //System.out.println("HTTP read");
                            // DEBUG
                            //System.out.println(inputLine);
                            ArrayList<String> packet = http.read(inputLine);
                            if (packet.size() > 0) {
                                //System.out.println("HTTP write");
                                //System.out.println("packet size = " + packet.size());
                                for (int i = 0; i < packet.size(); i++) {
                                    //System.out.println("i = " + i);
                                    out.println(packet.get(i));
                                }
                            }
                            if (!http.reading && !http.keepAlive) {
                                break;
                            }
                        }
                        break;
                    case "SMTP":
                        SMTP smtp = new SMTP(serverAddr, clientAddr, domain, domains);
                        //System.out.println("SMTP waiting...");
                        // send client a "Connected" message
                        out.println(smtp.replyCodeMsg(220));
                        // read client data
                        while ((inputLine = in.readLine()) != null) {
                            // DEBUG
                            //System.out.println(inputLine);
                            String response = smtp.read(inputLine);
                            if (response != null) {
                                //System.out.println("SMTP write");
                                out.println(response);
                            } else {
                                //System.out.println("SMTP NULL");
                            }
                            if (smtp.getState().equals("disconnected")) {
                                break;
                            }
                        }
                        break;
                }
                socket.close();
                System.out.println(type + " thread closed");
            } catch (IOException e) {
                System.err.println("Could not listen on port " + port);
                System.exit(1);
            }
        }
    }
}
