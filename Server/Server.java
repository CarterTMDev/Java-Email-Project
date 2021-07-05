import java.io.*;
import java.util.ArrayList;

public class Server {
    public static void main(String[] args) {
        int httpPort = -1;
        int smtpPort = -1;
        String domain = null;
        ArrayList<Domain> domains = new ArrayList<Domain>();
        // Get port for HTTP from server.conf
        try (BufferedReader r = new BufferedReader(new FileReader(args[0]))) {
            String input;
            String name = null;
            String ip = null;
            int port;
            while ((input = r.readLine()) != null) {
                if (input.startsWith("[")) {
                    if (domain == null) {
                        domain = input.substring(1, input.length() - 1);
                    } else {
                        name = input.substring(1, input.length() - 1);
                    }
                } else if (input.contains("=")) {
                    int ind = input.indexOf("=");
                    String data = input.substring(ind + 1, input.length());
                    switch (input.substring(0, ind)) {
                        case "HTTP_PORT":
                            httpPort = Integer.parseInt(data);
                            break;
                        case "SMTP_PORT":
                            smtpPort = Integer.parseInt(data);
                            break;
                        case "IP":
                            ip = data;
                            break;
                        case "PORT":
                            port = Integer.parseInt(data);
                            domains.add(new Domain(name, ip, port));
                            break;
                    }
                }
                /*
                }else if (input.startsWith("HTTP_PORT")) {
                    int ind = input.indexOf("=") + 1;
                    httpPort = Integer.parseInt(input.substring(ind, input.length()));
                } else if (input.startsWith("SMTP_PORT")) {
                    int ind = input.indexOf("=") + 1;
                    smtpPort = Integer.parseInt(input.substring(ind, input.length()));
                }
                */
            }
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
            System.exit(-1);
        } catch (IOException e) {
            System.out.println("An error occurred.");
            System.exit(-1);
        }
        // Start both servers
        if (httpPort == -1 || smtpPort == -1) {
            System.out.println("Couldn't get an SMTP port and an HTTP port from the .conf file. Please restart the program with a different .conf file.");
            System.exit(1);
        }
        // Start the servers
        ServerThread httpServer = new ServerThread("HTTP", httpPort);
        httpServer.start();
        ServerThread smtpServer = new ServerThread("SMTP", smtpPort, domain, domains);
        smtpServer.start();
        while (true) {
        }
    }
}