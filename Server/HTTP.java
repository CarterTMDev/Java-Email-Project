import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.text.SimpleDateFormat;

public class HTTP {
    private String serverAddr;
    private String clientAddr;
    private String domain;
    private String code = "";
    public boolean reading = false;
    private ArrayList<String[]> headers = new ArrayList<String[]>();
    private ArrayList<String> packets = new ArrayList<String>();
    private String username = null;
    private Encoder encoder = Base64.getEncoder();
    private Decoder decoder = Base64.getDecoder();
    private Login auth = new Login();
    private boolean loginMode = false;
    private boolean loggedIn = false;
    public boolean keepAlive = false;

    public HTTP(String serverAddr, String clientAddr, String domain) {
        this.serverAddr = serverAddr;
        this.clientAddr = clientAddr;
        this.domain = domain;
    }

    public ArrayList<String> read(String cmd) {
        /*
            - if start line
                - code = cmd.substring
                - headers.add(parseHeader(cmd))
                - reading = true
            - else if cmd.length > 0
                - parseHeader
            - else
                - reading = false
                - processRequest
                - return packets
        */
        if (cmd.equals("AUTH")) {
            loginMode = true;
            keepAlive = true;
            username = null;
            loggedIn = false;
        }
        if (loginMode) {
            code = "AUTH";
            packets.clear();
            headers.clear();
            if (cmd.equals("AUTH")) {
                packets.add("334 dXNlcm5hbWU6");
            } else if (cmd.equals("*")) {
                packets.add(replyCodeMsg(501));
                loginMode = false;
                username = null;
                loggedIn = false;
            } else if (username == null) {
                try {
                    username = new String(decoder.decode(cmd));
                    if (!auth.userExists(username)) {
                        packets.add("330 " + auth.newAccount(username));
                        username = null;
                        loginMode = false;
                    } else {
                        packets.add("334 cGFzc3dvcmQ6");
                        // DEBUG
                        //System.out.println("PASSWORD: " + auth.getPassword(username));
                    }
                } catch (IllegalArgumentException e) {
                    // DEBUG
                    //System.out.println("Encoded: " + cmd + "\nERROR: Could not be decoded.");
                    username = null;
                    packets.add(replyCodeMsg(501));
                }
            } else {
                // Password
                boolean success = false;
                String password = null;
                try {
                    password = new String(decoder.decode(cmd));
                    success = auth.login(username, password);
                } catch (IllegalArgumentException e) {
                    //System.out.println("Encoded: " + cmd + "\nERROR: Could not be decoded.");
                }
                if (success) {
                    packets.add(replyCodeMsg(235));
                    loginMode = false;
                    loggedIn = true;
                } else {
                    packets.add("535 Invalid password");
                    //username = null;
                }
            }
        } else {
            if (cmd.endsWith("HTTP/1.1")) {
                code = cmd.substring(0, cmd.indexOf(" "));
                // DEBUG
                //System.out.println("// DEBUG: CODE: " + code);
            }
            if (cmd.length() > 0) {
                String[] newHdr = parseHeader(cmd);
                // DEBUG
                /*
                if (newHdr == null) {
                    System.out.println("    DEBUG: newHdr == null");
                } else {
                    System.out.println("    DEBUG: newHdr = " + arrayToString(newHdr));
                }
                */
                headers.add(newHdr);
                reading = true;
            } else {
                reading = false;
                keepAlive = false;
                switch (code) {
                case "GET":
                    if (!loggedIn) {
                        packets.add(replyCodeMsg(401));
                        packets.add("");
                        break;
                    } else {
                        String hdrVal = getHeaderValue("GET");
                        if(hdrVal == null){
                            //System.out.println("GET header is null");
                            packets.add(replyCodeMsg(400));
                            packets.add("");
                            break;
                        }
                        String[] dirSplit = hdrVal.substring(1).split("/");
                        // DEBUG
                        //System.out.println("    DEBUG: dirSplit = " + arrayToString(dirSplit));
                        if (dirSplit.length < 2 || !dirSplit[1].equals(username)) {
                            packets.add(replyCodeMsg(403));
                            packets.add("");
                            break;
                        }
                    }
                    if (getHeaderValue("GET").endsWith("/unread")) {
                        keepAlive = true;
                        String inputLine;
                        File emailFile = new File(getHeaderValue("GET").substring(1));
                        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd yyyy HH:mm:ss Z");
                        try (BufferedReader r = new BufferedReader(new FileReader(emailFile))) {
                            packets.add(replyCodeMsg(200));
                            packets.add("Server: " + domain);
                            packets.add("Last-Modified: " + sdf.format(new Date(emailFile.lastModified())));
                            packets.add("Content-Type: text/plain");
                            packets.add("");
                            // System.out.println("Headers done");
                            while ((inputLine = r.readLine()) != null) {
                                packets.add(inputLine);
                            }
                            packets.add(".");
                            // System.out.println("Content done");
                        } catch (FileNotFoundException e) {
                            // System.out.println("404 here");
                            packets.add(replyCodeMsg(404));
                            packets.add("");
                        } catch (IOException e) {

                        }
                    } else {
                        // Fill the packets with email content
                        String[] dirBreak = getHeaderValue("GET").substring(1).split("/");
                        String dirName = dirBreak[0] + "/" + dirBreak[1];
                        File dir = new File(dirName);
                        if (!dir.exists()) {
                            //System.out.println("dir " + dirName + " doesn't exist");
                            packets.add(replyCodeMsg(404));
                            packets.add("");
                            break;
                        }
                        int count = 0;
                        try {
                            count = Integer.parseInt(getHeaderValue("Count"));
                            if (count <= 0) {
                                //System.out.println("Count <= 0");
                                packets.add(replyCodeMsg(400));
                                packets.add("");
                                break;
                            }
                        } catch (NumberFormatException e) {
                            //System.out.println("Count not an integer");
                            packets.add(replyCodeMsg(400));
                            packets.add("");
                            break;
                        }
                        ArrayList<String> unreadList = new ArrayList<String>();
                        try (BufferedReader r = new BufferedReader(new FileReader(dirName + "/unread"))) {
                            String inputLine;
                            while ((inputLine = r.readLine()) != null) {
                                unreadList.add(inputLine + ".email");
                            }
                            // System.out.println("Content done");
                        } catch (FileNotFoundException e) {

                        } catch (IOException e) {

                        }
                        ArrayList<String> reqEmails = new ArrayList<String>();
                        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd yyyy HH:mm:ss Z");
                        for (int i = 0; i < count; i++) {
                            // System.out.println("HTTP i = " + i);
                            String inputLine;
                            String emailFileName;
                            if (count == 1 && count != unreadList.size()) {
                                emailFileName = dirBreak[2];
                            } else {
                                emailFileName = unreadList.get(i);
                            }
                            File emailFile = new File(dirName + "/" + emailFileName);
                            try (BufferedReader r = new BufferedReader(new FileReader(emailFile))) {
                                packets.add(replyCodeMsg(200));
                                packets.add("Server: " + domain);
                                packets.add("Last-Modified: " + sdf.format(new Date(emailFile.lastModified())));
                                // count = Math.min(count, dir.list().length);
                                packets.add("Count: " + Integer.toString(count));
                                packets.add("Content-Type: text/plain");
                                packets.add("Message: " + Integer.toString(i + 1));
                                packets.add("");
                                // System.out.println("Headers done");
                                while ((inputLine = r.readLine()) != null) {
                                    packets.add(inputLine);
                                }
                                reqEmails.add(emailFileName);
                                // System.out.println("Content done");
                            } catch (FileNotFoundException e) {
                                // System.out.println("404 here");
                                packets.add(replyCodeMsg(404));
                                packets.add("");
                            } catch (IOException e) {

                            }
                        }
                        for (int i = 0; i < reqEmails.size(); i++) {
                            unreadList.remove(reqEmails.get(i));
                        }
                        try (PrintWriter fileOut = new PrintWriter(dirBreak[0] + "/" + dirBreak[1] + "/unread")) {
                            if (unreadList.size() > 0) {
                                for (int i = 0; i < unreadList.size(); i++) {
                                    fileOut.println(unreadList.get(i).substring(0, unreadList.get(i).indexOf(".email")));
                                }
                            } else {
                                fileOut.print("");
                            }
                        } catch (FileNotFoundException e) {
                            System.err.println("Couldn't create file");
                            System.exit(1);
                        }
                    }
                    break;
                default:
                    packets.add(replyCodeMsg(400));
                    packets.add("");
                }
            }
        }
        ArrayList<String> retPackets = new ArrayList<String>();
        if (packets.size() > 0) {
            serverLog(code, packets.get(0));
            code = null;
            headers.clear();
            for (String s : packets) {
                retPackets.add(s);
            }
            packets.clear();
        }
        return retPackets;
    }

    public String replyCodeMsg(int code) {
        String re = "HTTP/1.1 ";
        switch (code) {
            case 200:
                re += "200 OK";
                break;
            // SMTP
            case 235:
                return "235 Login successful";
            case 400:
                re += "400 Bad Request";
                break;
            case 401:
                re += "401 Unauthorized";
                break;
            case 403:
                re += "403 Forbidden";
                break;
            case 404:
                re += "404 Not Found";
                break;
            // SMTP
            case 501:
                return "501 Syntax error in parameters or arguments";
            default:
                return null;
        }
        return re;
    }

    private String[] parseHeader(String s) {
        if (s.contains(" ")) {
            String field = s.substring(0, s.indexOf(" "));
            if (field.endsWith(":")) {
                field = field.substring(0, field.length() - 1);
            }
            String value = s.substring(s.indexOf(" ") + 1);
            if (value.endsWith(" HTTP/1.1")) {
                value = value.substring(0, value.length() - 9);
            }
            String[] aStrings = new String[2];
            aStrings[0] = field;
            aStrings[1] = value;
            // DEBUG
            /*
            if (aStrings[0] == null) {
                System.out.println("    DEBUG: field == null");
            } else {
                System.out.println("    DEBUG: field = " + aStrings[0]+"|");
            }
            if (aStrings[1] == null) {
                System.out.println("    DEBUG: value == null");
            } else {
                System.out.println("    DEBUG: value = " + aStrings[1] + "|");
            }
            */
            return aStrings;
        } else {
            // DEBUG
            //System.out.println("    DEBUG: header == null");
            return null;
        }
    }

    private String getHeaderValue(String header) {
        for (int i = 0; i < headers.size(); i++) {
            // DEBUG
            if (headers.get(i) == null) {
                //System.out.println("    DEBUG: header == null");
            } else {
                String hdr = headers.get(i)[0];
                if (hdr != null && hdr.equals(header)) {
                    return headers.get(i)[1];
                }
            }

            
        }
        return null;
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
        logString += "HTTP-" + cmd + " ";
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

    private String arrayToString(String[] s) {
        String re = "";
        for (int i = 0; i < s.length; i++) {
            re += s[i];
            if (i < s.length - 1) {
                re += ", ";
            }
        }
        return re;
    }
}
