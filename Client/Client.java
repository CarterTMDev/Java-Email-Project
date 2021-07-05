import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;

public class Client {
    private final static String CRLF = "\r\n";
    private static Encoder encoder = Base64.getEncoder();
    private static Decoder decoder = Base64.getDecoder();

    public static void main(String[] args) {
        int port = -1;
        String address = null;
        // Get port for HTTP from server.conf
        try (BufferedReader r = new BufferedReader(new FileReader(args[0]))) {
            String input;
            while ((input = r.readLine()) != null) {
                if (input.contains("SERVER_IP")) {
                    int ind = input.indexOf("=") + 1;
                    address = input.substring(ind, input.length());
                } else if (input.contains("SERVER_PORT")) {
                    int ind = input.indexOf("=") + 1;
                    port = Integer.parseInt(input.substring(ind, input.length()));
                }
                if (port != -1 && address != null) {
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            //System.out.println("An error occurred.");
            //e.printStackTrace();
            //System.exit(1);
        } catch (IOException e) {
            //System.out.println("An error occurred.");
            //System.exit(1);
        }
        if (port == -1 || address == null) {
            System.out.println(
                    "Couldn't get a port and address from the .conf file. Please restart the program with a different .conf file.");
            System.exit(1);
        }
        BufferedReader keeb = new BufferedReader(new InputStreamReader(System.in));
        boolean quit = false;
        System.out.println("Welcome! Type -q when you want to quit the program.");
        // Get the mode
        String mode = null;
        while (mode == null) {
            System.out.print("\nType \"SMTP\" to send emails, or \"HTTP\" to receive email: ");
            try {
                mode = keeb.readLine().toUpperCase();
                inputQuit(mode);
                if (!mode.equals("HTTP") && !mode.equals("SMTP")) {
                    System.out.println("Invalid input. Please try again.");
                    mode = null;
                }
            } catch (IOException e) {
                System.err.println("Couldn't get keyboard input");
                System.exit(1);
            }
        }
        while (!quit) {
            // Connect to the server
            boolean done;
            String fromServer;
            switch (mode) {
                case "HTTP":
                    // Get mail
                    String username = "";
                    int count = 0;
                    int emailInd = 0;
                    boolean readingHeaders = false;
                    done = false;
                    PrintWriter fileOut = null;
                    ArrayList<String> emailIds = new ArrayList<String>();
                    System.out.print("Connecting to the server... ");
                    String fromUser;
                    try (Socket kkSocket = new Socket(address, port);
                            PrintWriter out = new PrintWriter(kkSocket.getOutputStream(), true);
                            BufferedReader in = new BufferedReader(
                                    new InputStreamReader(kkSocket.getInputStream()));) {
                        System.out.println("Connected.");
                        int httpState = 0;
                        done = false;
                        out.println("AUTH");
                        httpState++;
                        while (!done && (fromServer = in.readLine()) != null) {
                            switch (httpState) {
                            case 0:
                                out.println("AUTH");
                                httpState++;
                                break;
                            case 1:
                                if (fromServer.equals("334 dXNlcm5hbWU6")) {
                                    System.out.print("Enter your username (the word BEFORE the @ in your email address), or type * to cancel: ");
                                    fromUser = keeb.readLine();
                                    inputQuit(fromUser);
                                    if (fromUser.equals("*")) {
                                        out.println(fromUser);
                                    } else {
                                        username = fromUser;
                                        out.println(encodeString(fromUser));
                                    }
                                    httpState++;
                                } else {
                                    done = true;
                                }
                                break;
                            case 2:
                                if (fromServer.startsWith("330")) {
                                    // Server issues a new password
                                    // Disconnect
                                    String pass = new String(decoder.decode(fromServer.substring(4)));
                                    System.out.println("Your password: " + pass);
                                    done = true;
                                } else if (fromServer.startsWith("501")) {
                                    // User cancelled login
                                    // Disconnect
                                    done = true;
                                    username = null;
                                } else if (fromServer.equals("334 cGFzc3dvcmQ6")) {
                                    // Valid username
                                    // Enter password
                                    System.out.print("Enter your password (or type * to cancel): ");
                                    fromUser = keeb.readLine();
                                    inputQuit(fromUser);
                                    if (fromUser.equals("*")) {
                                        out.println(fromUser);
                                    } else {
                                        out.println(encodeString(fromUser));
                                    }
                                    httpState++;
                                } else {
                                    done = true;
                                }
                                break;
                            case 3:
                                if (fromServer.startsWith("535")) {
                                    // Invalid password
                                    System.out.print("Incorrect password. Try again.\nEnter your password (or type * to cancel): ");
                                    fromUser = keeb.readLine();
                                    inputQuit(fromUser);
                                    if (fromUser.equals("*")) {
                                        out.println(fromUser);
                                    } else {
                                        out.println(encodeString(fromUser));
                                    }
                                } else if (fromServer.startsWith("501")) {
                                    // User cancelled login
                                    // Disconnect
                                    done = true;
                                } else if (fromServer.startsWith("235")) {
                                    // Successful login
                                    out.println("GET /db/" + username + "/unread HTTP/1.1");
                                    out.println("Host: " + address);
                                    out.println("");
                                    httpState++;
                                } else {
                                    done = true;
                                }
                                break;
                            case 4:
                                if (fromServer.startsWith("HTTP/1.1 200 OK")) {
                                    httpState++;
                                } else {
                                    done = true;
                                }
                                break;
                            case 5:
                                if (fromServer.length() == 0) {
                                    // Headers done
                                    httpState++;
                                }
                                break;
                            case 6:
                                if (fromServer.equals(".")) {
                                    System.out.println("Unread emails: " + emailIds.size());
                                    if (emailIds.size() > 0) {
                                        for (int i = 0; i < emailIds.size(); i++) {
                                            System.out.println("ID: " + emailIds.get(i));
                                        }
                                        System.out.println("Which emails do you want?");
                                        boolean valid = false;
                                        do {
                                            System.out.println("Enter an email ID or \"all\" for all of them: ");
                                            fromUser = keeb.readLine();
                                            inputQuit(fromUser);
                                            if (fromUser.equalsIgnoreCase("all") || emailIds.contains(fromUser)) {
                                                valid = true;
                                            }
                                        } while (!valid);
                                        if (fromUser.equalsIgnoreCase("all")) {
                                            out.println("GET /db/" + username + "/ HTTP/1.1");
                                            count = emailIds.size();
                                            emailInd = 0;
                                        } else {
                                            out.println("GET /db/" + username + "/" + fromUser + ".email HTTP/1.1");
                                            emailInd = emailIds.indexOf(fromUser);
                                            count = 1;
                                        }
                                        out.println("Host: " + address);
                                        out.println("Count: " + count);
                                        out.println("");
                                        File dir = new File(username);
                                        if (!dir.exists()) {
                                            dir.mkdir();
                                        }
                                        httpState++;
                                    } else {
                                        done = true;
                                    }
                                } else {
                                    emailIds.add(fromServer);
                                }
                                break;
                            case 7:
                                if (fromServer.startsWith("HTTP/1.1")) {
                                    // New email
                                    if (fileOut != null) {
                                        fileOut.close();
                                        System.out.println("---- END ----\n");
                                    }
                                    if (fromServer.equals("HTTP/1.1 200 OK")) {
                                        String emailFileName;
                                        File email;
                                        emailFileName = username + "/" + emailIds.get(emailInd) + ".txt";
                                        email = new File(emailFileName);
                                        try {
                                            fileOut = new PrintWriter(email);
                                        } catch (FileNotFoundException e) {
                                            System.err.println("Couldn't create file");
                                            System.exit(1);
                                        }
                                        System.out.println("---- ID: " + emailIds.get(emailInd) + " ----");
                                    } else {
                                        System.out.println("XXXXXX Email ID " + emailIds.get(emailInd) + " could not be downloaded. XXXXXX\n");
                                    }
                                    emailInd++;
                                    readingHeaders = true;
                                } else if (fromServer.length() == 0 && readingHeaders) {
                                    readingHeaders = false;
                                } else if (!readingHeaders) {
                                    // Write email
                                    System.out.println(fromServer);
                                    fileOut.println(fromServer);
                                }
                                break;
                            }
                        }
                    } catch (UnknownHostException e) {
                        System.err.println("Couldn't connect to " + address);
                        System.exit(1);
                    } catch (IOException e) {
                        System.err.println("Couldn't get I/O for the connection to " + address);
                        System.exit(1);
                    }
                    if (fileOut != null) {
                        fileOut.close();
                        System.out.println("---- END ----\n");
                    }
                    if (count > 0) {
                        System.out.println("Download complete.");
                    }
                    System.out.println("Disconnected from the server.");
                    try{
                        do{
                            System.out.print("Would you like to connect again? (y/n) ");
                            fromUser = keeb.readLine();
                            inputQuit(fromUser);
                        } while (!fromUser.equalsIgnoreCase("y") && !fromUser.equalsIgnoreCase("n"));
                        if (fromUser.equalsIgnoreCase("n")) {
                            quit = true;
                        }
                    } catch (IOException e) {
                        System.out.println("IO EXCEPTION");
                        System.exit(1);
                    }
                    break;
                case "SMTP":
                    System.out.print("Press Enter to connect to the server, or type -q to quit: ");
                    try {
                        String ans = keeb.readLine();
                        inputQuit(ans);
                    } catch (IOException e) {
                        System.err.println("Couldn't get keyboard input");
                        System.exit(1);
                    }
                    System.out.print("Connecting to the server... ");
                    try (Socket kkSocket = new Socket(address, port);
                            PrintWriter out = new PrintWriter(kkSocket.getOutputStream(), true);
                            BufferedReader in = new BufferedReader(new InputStreamReader(kkSocket.getInputStream()));) {
                        System.out.println("Connected.");
                        String ip = InetAddress.getLocalHost().getHostAddress();
                        String domain = null;
                        String emailAddress = null;
                        boolean validRcpt = false;
                        int smtpState = 0;
                        done = false;
                        while (!done && (fromServer = in.readLine()) != null) {
                            switch (smtpState) {
                                case 0:
                                    if (fromServer.startsWith("220")) {
                                        domain = fromServer.split(" ")[1];
                                        //out.println("HELO " + ip);
                                        System.out.println("Type your HELO command and press Enter:");
                                        String helo = keeb.readLine();
                                        inputQuit(helo);
                                        out.println(helo);
                                        smtpState++;
                                    } else {
                                        done = true;
                                    }
                                    break;
                                case 1:
                                    if (fromServer.startsWith("250")) {
                                        out.println("AUTH");
                                        smtpState++;
                                    } else if (fromServer.startsWith("501")) {
                                        System.out.println(
                                                "HELO command not accepted. Type your HELO command and press Enter:");
                                        String helo = keeb.readLine();
                                        inputQuit(helo);
                                        out.println(helo);
                                    } else {
                                        done = true;
                                    }
                                    break;
                                case 2:
                                    if (fromServer.equals("334 dXNlcm5hbWU6")) {
                                        System.out.print("Enter your username (the word BEFORE the @ in your email address), or type * to cancel: ");
                                        fromUser = keeb.readLine();
                                        inputQuit(fromUser);
                                        if (fromUser.equals("*")) {
                                            out.println(fromUser);
                                        } else {
                                            emailAddress = fromUser + "@" + domain;
                                            out.println(encodeString(fromUser));
                                        }
                                        smtpState++;
                                    } else {
                                        done = true;
                                    }
                                    break;
                                case 3:
                                    if (fromServer.startsWith("330")) {
                                        // Server issues a new password
                                        // Disconnect
                                        String pass = new String(decoder.decode(fromServer.substring(4)));
                                        System.out.println("Your password: " + pass);
                                        done = true;
                                    } else if (fromServer.startsWith("501")) {
                                        // User cancelled login
                                        // Disconnect
                                        done = true;
                                    } else if (fromServer.equals("334 cGFzc3dvcmQ6")) {
                                        // Valid username
                                        // Enter password
                                        System.out.print("Enter your password (or type * to cancel): ");
                                        fromUser = keeb.readLine();
                                        inputQuit(fromUser);
                                        if (fromUser.equals("*")) {
                                            out.println(fromUser);
                                        } else {
                                            out.println(encodeString(fromUser));
                                        }
                                        smtpState++;
                                    } else {
                                        done = true;
                                    }
                                    break;
                                case 4:
                                    if (fromServer.startsWith("535")){
                                        // Invalid password
                                        System.out.print("Incorrect password. Try again.\nEnter your password (or type * to cancel): ");
                                        fromUser = keeb.readLine();
                                        inputQuit(fromUser);
                                        if (fromUser.equals("*")) {
                                            out.println(fromUser);
                                        } else {
                                            out.println(encodeString(fromUser));
                                        }
                                    } else if (fromServer.startsWith("501")) {
                                        // User cancelled login
                                        // Disconnect
                                        done = true;
                                    } else if (fromServer.startsWith("235")) {
                                        // Successful login
                                        out.println("MAIL FROM:<" + emailAddress + ">");
                                        smtpState++;
                                    } else {
                                        done = true;
                                    }
                                    break;
                                case 5:
                                    if (fromServer.startsWith("250")) {
                                        System.out.print("Enter a recipient's email address: ");
                                        fromUser = keeb.readLine();
                                        inputQuit(fromUser);
                                        out.println("RCPT TO:<" + fromUser + ">");
                                        smtpState++;
                                    } else {
                                        done = true;
                                    }
                                    break;
                                case 6:
                                    if (fromServer.startsWith("250")) {
                                        validRcpt = true;
                                        do {
                                            System.out.print("Enter another recipient? (y/n): ");
                                            fromUser = keeb.readLine();
                                            inputQuit(fromUser);
                                        } while (!fromUser.equalsIgnoreCase("y") && !fromUser.equalsIgnoreCase("n"));
                                        if (fromUser.equalsIgnoreCase("y")) {
                                            System.out.print("Enter a recipient's email address: ");
                                            fromUser = keeb.readLine();
                                            inputQuit(fromUser);
                                            out.println("RCPT TO:<" + fromUser + ">");
                                        } else {
                                            out.println("DATA");
                                            smtpState++;
                                        }
                                    } else if (fromServer.startsWith("501") || fromServer.startsWith("503")
                                            || fromServer.startsWith("550")) {
                                        String response;
                                        if (fromServer.startsWith("550")) {
                                            response = "That address could not be reached. ";
                                        } else {
                                            response = "That address is invalid. ";
                                        }
                                        do {
                                            System.out.print(response + "Try again? (y/n): ");
                                            fromUser = keeb.readLine();
                                            inputQuit(fromUser);
                                        } while (!fromUser.equalsIgnoreCase("y") && !fromUser.equalsIgnoreCase("n"));
                                        if (fromUser.equalsIgnoreCase("y")) {
                                            System.out.print("Enter a recipient's email address: ");
                                            fromUser = keeb.readLine();
                                            inputQuit(fromUser);
                                            out.println("RCPT TO:<" + fromUser + ">");
                                        } else {
                                            if (!validRcpt) {
                                                done = true;
                                            } else {
                                                out.println("DATA");
                                                smtpState++;
                                            }
                                        }
                                    } else {
                                        done = true;
                                    }
                                    break;
                                case 7:
                                    if (fromServer.startsWith("354")) {
                                        System.out.println(
                                                "Begin typing your email. Type a . on its own line to finish your email.");
                                        boolean dataEnd = false;
                                        while (!dataEnd) {
                                            fromUser = keeb.readLine();
                                            if (fromUser.length() > 0) {
                                                if (fromUser.startsWith(".")) {
                                                    if (fromUser.length() > 1) {
                                                        fromUser = "." + fromUser;
                                                    } else {
                                                        dataEnd = true;
                                                    }
                                                }
                                            }
                                            out.println(fromUser/* + CRLF */);
                                        }
                                        smtpState++;
                                    } else {
                                        done = true;
                                    }
                                    break;
                                case 8:
                                    if (fromServer.startsWith("250")) {
                                        System.out.print("Your email was sent. ");
                                    } else {
                                        System.out.print("Your email was NOT sent. ");
                                        /*
                                        System.out.println(
                                                "The recipients at the following domains could not be reached:");
                                        String[] unreach = fromServer.substring(fromServer.indexOf(":") + 2)
                                                .split(", ");
                                        for (int i = 0; i < unreach.length; i++) {
                                            System.out.println(unreach[i]);
                                        }
                                        */
                                    }
                                    do {
                                        System.out.print("Send another email? (y/n): ");
                                        fromUser = keeb.readLine();
                                        inputQuit(fromUser);
                                    } while (!fromUser.equalsIgnoreCase("y") && !fromUser.equalsIgnoreCase("n"));
                                    if (fromUser.equalsIgnoreCase("y")) {
                                        validRcpt = false;
                                        out.println("MAIL FROM:<" + emailAddress + ">");
                                        smtpState = 5;
                                    } else {
                                        done = true;
                                        out.println("QUIT");
                                    }
                                    break;
                            }
                        }
                    } catch (UnknownHostException e) {
                        System.err.println("Couldn't connect to " + address);
                        System.exit(1);
                    } catch (IOException e) {
                        System.err.println("Couldn't get I/O for the connection to " + address);
                        System.exit(1);
                    }
                    System.out.println("Disconnected from the server.");
                    break;
            }
        }
    }

    private static void inputQuit(String in) {
        if (in.equals("-q")) {
            System.out.println("Goodbye!");
            System.exit(0);
        }
    }

    private static String encodeString(String s) {
        return encoder.withoutPadding().encodeToString(s.getBytes());
    }
}
