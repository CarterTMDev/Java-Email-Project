import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.Random;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.text.SimpleDateFormat;

public class SMTP {
    private final String CRLF = "\r\n";
    private final String SERVER_KEY = "KCDNocKwIM2cypYgzaHCsCk";
    private String serverAddr;
    private String clientAddr;
    private String domain;
    private ArrayList<Domain> domains;
    private boolean postmaster = false;
    private String state = "connected";
    private String sender = null;
    private ArrayList<String> receivers = new ArrayList<String>();
    private ArrayList<String> data = new ArrayList<String>();
    private String username = null;
    private String password = null;
    private Encoder encoder = Base64.getEncoder();
    private Decoder decoder = Base64.getDecoder();
    private Login auth = new Login();
    
    public SMTP(String serverAddr, String clientAddr, String domain, ArrayList<Domain> domains) {
        this.serverAddr = serverAddr;
        this.clientAddr = clientAddr;
        this.domain = domain;
        this.domains = domains;
        /*
        for (int i = 0; i < domains.size(); i++) {
            if (clientAddr.equals(domains.get(i).address)) {
                postmaster = true;
                break;
            }
        }
        */
    }
    
    public String read(String cmd) {
        String[] currCmd = cmd.split(" ");
        String s = null;
        String code;
        if (state.equals("data")) {
            code = "DATA";
        } else if (state.equals("login")) {
            code = "AUTH";
        } else {
            code = currCmd[0].toUpperCase();
        }
        switch (code) {
            case "HELO":
                if (currCmd.length == 2) {
                    s = replyCodeMsg(250);
                    if (currCmd[1].equals(SERVER_KEY)) {
                        postmaster = true;
                    }
                    if (postmaster) {
                        state = "start";
                    } else {
                        state = "beforeLogin";
                    }
                    receivers.clear();
                    data.clear();
                    username = null;
                } else {
                    s = replyCodeMsg(501);
                }
                break;
            case "AUTH":
                if (state.equals("beforeLogin") || state.equals("login")) {
                    if (!state.equals("login")) {
                        s = "334 dXNlcm5hbWU6";
                        state = "login";
                    } else if (currCmd[0].equals("*")) {
                        s = replyCodeMsg(501);
                        username = null;
                        password = null;
                        state = "connected";
                    } else {
                        if (username == null) {
                            // Username
                            try {
                                username = new String(decoder.decode(currCmd[0]));
                                //System.out.println("Encoded: " + currCmd[0] + "\nDecoded: " + username);
                                if (!auth.userExists(username)) {
                                    s = "330 " + auth.newAccount(username);
                                    username = null;
                                    state = "disconnected";
                                } else {
                                    s = "334 cGFzc3dvcmQ6";
                                    // DEBUG
                                    //System.out.println("PASSWORD: " + auth.getPassword(username));
                                }
                            } catch (IllegalArgumentException e) {
                                // DEBUG
                                //System.out.println("Encoded: " + currCmd[0] + "\nERROR: Could not be decoded.");
                                username = null;
                                s = replyCodeMsg(501);
                            }
                        } else {
                            // Password
                            boolean success = false;
                            try {
                                password = new String(decoder.decode(currCmd[0]));
                                success = auth.login(username, password);
                            } catch (IllegalArgumentException e) {
                                //System.out.println("Encoded: " + currCmd[0] + "\nERROR: Could not be decoded.");
                            }
                            if (success) {
                                state = "start";
                                s = replyCodeMsg(235);
                                sender = username + "@" + domain;
                            } else {
                                s = "535 Invalid password";
                            }
                            password = null;
                        }
                    }
                } else {
                    s = replyCodeMsg(503);
                }
                break;
            case "MAIL":
                if (state.equals("start")) {
                    if (currCmd.length == 2) {
                        if (currCmd[1].length() > 7) {
                            if (currCmd[1].substring(0, 6).equalsIgnoreCase("FROM:<") && currCmd[1].endsWith(">")) {
                                String newSender = currCmd[1].substring(6, currCmd[1].length() - 1);
                                if (isValidEmailAddress(newSender) && (postmaster || sender.equals(newSender))) {
                                    sender = newSender;
                                    s = replyCodeMsg(250);
                                    state = "mail";
                                } else {
                                    s = replyCodeMsg(550);
                                }
                            } else {
                                s = replyCodeMsg(501);
                            }
                        } else {
                            s = replyCodeMsg(501);
                        }
                    } else {
                        s = replyCodeMsg(501);
                    }
                } else {
                    s = replyCodeMsg(503);
                }
                break;
            case "RCPT":
                if (state.equals("mail")) {
                    state = "rcpt";
                }
                if (state.equals("rcpt")) {
                    if (currCmd.length == 2) {
                        if (currCmd[1].length() > 5) {
                            if (currCmd[1].substring(0, 4).equalsIgnoreCase("TO:<") && currCmd[1].endsWith(">")) {
                                String rcpt = currCmd[1].substring(4, currCmd[1].length() - 1);
                                if (isValidEmailAddress(rcpt) && (!postmaster || (postmaster && isMyDomain(rcpt)))) {
                                    receivers.add(rcpt);
                                    s = replyCodeMsg(250);
                                } else {
                                    s = replyCodeMsg(550);
                                }
                            } else {
                                s = replyCodeMsg(501);
                            }
                        } else {
                            s = replyCodeMsg(501);
                        }
                    } else {
                        s = replyCodeMsg(501);
                    }
                } else {
                    s = replyCodeMsg(503);
                }
                break;
            case "DATA":
                if (state.equals("rcpt")) {
                    if (currCmd.length == 1 && receivers.size() > 0) {
                        state = "data";
                        s = replyCodeMsg(354);
                    } else {
                        s = replyCodeMsg(501);
                    }
                } else if (state.equals("data")) {
                    // System.out.println("Data mode");
                    String newData = cmd;
                    if (newData.length() > 0) {
                        // System.out.println("Len: " + newData.length());
                        if (newData.startsWith(".")) {
                            // System.out.println("Period");
                            if (newData.length() > 1) {
                                newData = newData.substring(1);
                            } else {
                                // end of data
                                // System.out.println("About to Email");
                                sendEmails();
                                s = replyCodeMsg(250);
                                state = "start";
                                receivers.clear();
                                data.clear();
                                break;
                            }
                        }
                    }
                    data.add(newData);
                    // System.out.println("Data added");
                } else {
                    s = replyCodeMsg(503);
                }
                break;
            case "HELP":
                if (currCmd.length == 2) {
                    s = help(currCmd[1]);
                } else if (currCmd.length == 1) {
                    s = help("HELP");
                } else {
                    s = replyCodeMsg(501);
                }
                break;
            case "QUIT":
                s = replyCodeMsg(221);
                state = "disconnected";
                break;
            default:
                s = replyCodeMsg(500);
        }
        if (s != null) {
            serverLog(code, s);
        }
        return s;
    }

    private String help(String code) {
        String re = "214 Syntax: ";
        switch (code) {
        case "HELO":
            re += "HELO Domain";
            break;
        case "MAIL":
            re += "MAIL FROM:<address>";
            break;
        case "RCPT":
            re += "RCPT TO:<address>";
            break;
        case "DATA":
            re += "DATA";
            break;
        case "HELP":
            re += "HELP Command";
            break;
        case "QUIT":
            re += "QUIT";
            break;
        default:
            re = replyCodeMsg(501);
        }
        return re;
    }

    public String replyCodeMsg(int code) {
        switch (code) {
            case 220:
                return "220 " + domain + " ready";
            case 221:
                return "221 OK Disconnecting";
            case 235:
                return "235 Login successful";
            case 250:
                return "250 OK";
            case 354:
                return "354 Start mail input; end with a . on its own line";
            case 500:
                return "500 Command unrecognized";
            case 501:
                return "501 Syntax error in parameters or arguments";
            case 503:
                return "503 Command not allowed in this state of the transaction";
            case 550:
                return "550 That mailbox cannot be reached";
            default:
                return null;
        }
    }

    private void sendEmails() {
        ArrayList<Domain> remoteDom = new ArrayList<Domain>();
        // Send all emails within my domain
        for (int i = 0; i < receivers.size(); i++) {
            String receiver = receivers.get(i);
            if (isMyDomain(receiver)) {
                /*
                String emailFileName;
                int count = 0;
                File email;
                String dirName = "db/" + receiver.substring(0, receiver.indexOf("@"));
                File dir = new File(dirName);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                do {
                    count++;
                    emailFileName = dirName + "/" + String.format("%03d", count) + ".email";
                    email = new File(emailFileName);
                } while (email.exists());
                // System.out.println("Email !exists");
                // Write the email file
                try (PrintWriter fileOut = new PrintWriter(email)) {
                    SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd yyyy HH:mm:ss Z");
                    fileOut.println("Date:  " + sdf.format(new Date()));
                    fileOut.println("From:  <" + sender + ">");
                    fileOut.println("To:  <" + receiver + ">");
                    for (int j = 0; j < data.size(); j++) {
                        fileOut.println(data.get(j));
                    }
                    // System.out.println("Email try");
                } catch (FileNotFoundException e) {
                    System.err.println("Couldn't create file");
                    System.exit(1);
                }
                // Add email file name to unread
                try (FileWriter fw = new FileWriter(dirName + "/" + "unread", true);
                        PrintWriter fileOut = new PrintWriter(fw);) {
                    fileOut.println(String.format("%03d", count));
                    // System.out.println("Email try");
                } catch (FileNotFoundException e) {
                    System.err.println("Couldn't create file");
                    System.exit(1);
                } catch (IOException e) {
                    System.err.println("Couldn't write to file");
                    System.exit(1);
                }
                */
                if (sendEmailLocal(sender, receiver, data)) {
                    // System.out.println("Email created");
                    receivers.remove(i--);
                }
            } else {
                for (int j = 0; j < domains.size(); j++) {
                    Domain dom = domains.get(j);
                    if (dom.name.equals(getDomain(receiver))) {
                        if (!remoteDom.contains(dom)) {
                            remoteDom.add(dom);
                        }
                        break;
                    }
                }
            }
        }
        // Send emails to the remote domains
        if (remoteDom.size() > 0) {
            for (int i = 0; i < remoteDom.size(); i++) {
                Domain d = remoteDom.get(i);
                boolean success = false;
                try (Socket socket = new Socket(d.address, d.port);
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));) {
                    // Initiate the connection
                    int state = 0;
                    String fromServer;
                    int rcptInd = -1;
                    boolean done = false;
                    while (!done && (fromServer = in.readLine()) != null) {
                        switch (state) {
                        case 0:
                            if (fromServer.startsWith("220")) {
                                out.println("HELO " + SERVER_KEY);
                                state++;
                            } else {
                                done = true;
                            }
                            break;
                        case 1:
                            if (fromServer.startsWith("250")) {
                                out.println("MAIL FROM:<" + sender + ">");
                                state++;
                            } else {
                                done = true;
                            }
                            break;
                        case 2:
                            /*
                            if !250 && rcptInd == -1
                                - done
                            else...
                                if 250 && rcptInd != -1
                                - receivers.remove(rcptInd)
                                - rcptInd--
                            
                                rcptInd++
                                
                                if rcptInd == receivers.size()
                                - send DATA
                                - step++
                                else
                                - normal stuff
                            */
                            if (!fromServer.startsWith("250") && rcptInd == -1) {
                                done = true;
                            } else {
                                if (fromServer.startsWith("250") && rcptInd != -1) {
                                    receivers.remove(rcptInd);
                                    rcptInd--;
                                }
                                String receiver = "";
                                do {
                                    rcptInd++;
                                    if (rcptInd < receivers.size()) {
                                        receiver = receivers.get(rcptInd);
                                    } else {
                                        break;
                                    }
                                } while (!getDomain(receiver).equals(d.name));
                                if (rcptInd == receivers.size()) {
                                    out.println("DATA");
                                    state += 2;
                                } else {
                                    out.println("RCPT TO:<" + receiver + ">");
                                }
                            }
                            /*
                            if (fromServer.startsWith("250")) {
                                rcptInd++;
                                if (rcptInd < receivers.size()) {
                                    String receiver = receivers.get(rcptInd);
                                    if (getDomain(receiver).equals(d.name)) {
                                        out.println("RCPT TO:<" + receiver + ">");
                                    }
                                }
                                if (rcptInd == receivers.size() - 1) {
                                    state++;
                                }
                            } else {
                                done = true;
                            }
                            */
                            break;
                        case 3:
                            /*
                            if (fromServer.startsWith("250")) {
                                out.println("DATA");
                                state++;
                            } else {
                                done = true;
                            }
                            */
                            break;
                        case 4:
                            if (fromServer.startsWith("354")) {
                                for (int j = 0; j < data.size(); j++) {
                                    String dLine = data.get(j);
                                    if (dLine.startsWith(".")) {
                                        out.println("." + dLine);
                                    } else {
                                        out.println(dLine);
                                    }

                                }
                                out.println(".");
                                state++;
                            } else {
                                done = true;
                            }
                            break;
                        case 5:
                            if (fromServer.startsWith("250")) {
                                success = true;
                                out.println("QUIT");
                            }
                            done = true;
                            break;
                        }
                    }
                } catch (UnknownHostException e) {
                    // Couldn't connect to d.address
                } catch (IOException e) {
                    //
                }
                /*
                if (success) {
                    // mail was sent to this domain
                    //remoteDom.remove(i--);
                }
                */
            }
            if (receivers.size() > 0) {
                String re = "550 The following recipients could not be reached: ";
                for (int i = 0; i < receivers.size(); i++) {
                    re += receivers.get(i);
                    if (i < receivers.size() - 1) {
                        re += ", ";
                    }
                }
                // Put an email in the sender's mailbox
                sendEmailLocal("", sender, re);
            }
            /*
            if (remoteDom.size() > 0) {
                String re = "550 The following domains could not be reached: ";
                for (int i = 0; i < remoteDom.size(); i++) {
                    re += remoteDom.get(i).name;
                    if (i < remoteDom.size() - 1) {
                        re += ", ";
                    }
                }
                // Put an email in the sender's mailbox
                sendEmailLocal("", sender, re);
            }
            */
        }
        //return replyCodeMsg(250);
    }
    
    private boolean sendEmailLocal(String sender, String receiver, ArrayList<String> message) {
        String emailFileName;
        int count = 0;
        File email;
        String dirName = "db/" + receiver.substring(0, receiver.indexOf("@"));
        File dir = new File(dirName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        do {
            count++;
            emailFileName = dirName + "/" + String.format("%03d", count) + ".email";
            email = new File(emailFileName);
        } while (email.exists());
        // System.out.println("Email !exists");
        // Write the email file
        try (PrintWriter fileOut = new PrintWriter(email)) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd yyyy HH:mm:ss Z");
            fileOut.println("Date:  " + sdf.format(new Date()));
            fileOut.println("From:  <" + sender + ">");
            fileOut.println("To:  <" + receiver + ">");
            for (int j = 0; j < message.size(); j++) {
                fileOut.println(message.get(j));
            }
            // System.out.println("Email try");
        } catch (FileNotFoundException e) {
            System.err.println("Couldn't create file");
            System.exit(1);
        }
        // Add email file name to unread
        try (FileWriter fw = new FileWriter(dirName + "/" + "unread", true);
                PrintWriter fileOut = new PrintWriter(fw);) {
            fileOut.println(String.format("%03d", count));
            // System.out.println("Email try");
        } catch (FileNotFoundException e) {
            System.err.println("Couldn't create file");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Couldn't write to file");
            System.exit(1);
        }
        return true;
    }

    private boolean sendEmailLocal(String sender, String receiver, String message) {
        ArrayList<String> m = new ArrayList<String>();
        m.add(message);
        return sendEmailLocal(sender, receiver, m);
    }

    private boolean isValidEmailAddress(String address) {
        if (address.length() >= 8) {
            if (!address.contains(" ") && address.contains("@") && address.indexOf("@") == address.lastIndexOf("@")) {
                String rcptDom = address.substring(address.indexOf("@") + 1);
                if (rcptDom.equals(domain)) {
                    return true;
                }
                for (int i = 0; i < domains.size(); i++) {
                    if (domains.get(i).name.equals(rcptDom)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public String getState() {
        return state;
    }

    private String encodeString(String s) {
        return encoder.withoutPadding().encodeToString(s.getBytes());
    }

    private void serverLog(String cmd, String reply) {
        if (cmd == null || reply == null) {
            return;
        }
        String logString = "";
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy'T'HH:mm:ss.SS");
        logString += sdf.format(new Date()) + " ";
        logString += clientAddr + " ";
        logString += serverAddr + " ";
        logString += "SMTP-" + cmd + " ";
        logString += reply;
        try (FileWriter file = new FileWriter(".server_log", true); PrintWriter fileOut = new PrintWriter(file);) {
            fileOut.println(logString);
        } catch (FileNotFoundException e) {
            System.err.println("Couldn't create .server_log file");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("IO Exception for .server_log");
            System.exit(1);
        }
        System.out.println(logString);
    }

    private String getDomain(String emailAddr) {
        int ind = emailAddr.indexOf("@");
        if (ind != -1) {
            return emailAddr.substring(ind + 1);
        }
        return null;
    }

    private boolean isMyDomain(String emailAddr) {
        return getDomain(emailAddr).equals(domain);
    }
}
