import java.io.*;
import java.util.Base64;
import java.util.Random;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;

public class Login {
    private Encoder encoder = Base64.getEncoder();
    private Decoder decoder = Base64.getDecoder();
    private String saltingStr = "447S21";
    
    // DEBUG
    /*
    public String getPassword(String user) {
        String search = encodeString(user) + " ";
        // Search in .user_pass
        String dirName = "db";
        File dir = new File(dirName);
        if (!dir.exists()) {
            return null;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(dirName + "/.user_pass"))) {
            String input;
            while ((input = r.readLine()) != null) {
                if (input.startsWith(search)) {
                    String s = new String(decoder.decode(input.substring(search.length())));
                    return s.substring(saltingStr.length());
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Could not read user_pass");
            e.printStackTrace();
            System.exit(-1);
        } catch (IOException e) {
            System.out.println("Could not read user_pass");
            System.exit(-1);
        }
        return null;
    }
    */

    private String encodeString(String s) {
        return encoder.withoutPadding().encodeToString(s.getBytes());
    }

    public String newAccount(String user) {
        String[] alphabet = "abcdefghijklmnopqrstuvwxyz0123456789".split("");
        String s = "";
        Random rand = new Random();
        for (int i = 0; i < 6; i++) {
            s += alphabet[rand.nextInt(alphabet.length)];
        }
        String username = encodeString(user);
        String pass = encodeString(saltingStr + s);
        // save pass in .user_pass
        String dirName = "db";
        File dir = new File(dirName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try (FileWriter userpass = new FileWriter(dirName + "/.user_pass", true);
                PrintWriter fileOut = new PrintWriter(userpass);) {
            fileOut.println(username + " " + pass);
        } catch (FileNotFoundException e) {
            System.err.println("Couldn't create user_pass file");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("IO Exception for user_pass");
            System.exit(1);
        }
        // Create mailbox
        dirName += "/" + user;
        dir = new File(dirName);
        if (!dir.exists()) {
            dir.mkdirs();
            File un = new File(dirName + "/unread");
            if (!un.exists()) {
                try (FileWriter unread = new FileWriter(dirName + "/unread");
                        PrintWriter fileOut = new PrintWriter(unread);) {
                    fileOut.print("");
                } catch (FileNotFoundException e) {
                    System.err.println("Couldn't create unread file");
                    System.exit(1);
                } catch (IOException e) {
                    System.err.println("IO Exception for unread");
                    System.exit(1);
                }
            }
        }
        // String pass = encode(447S21 + s)
        // save pass in .user_pass
        // encode(s) to string
        return encodeString(s);
    }
    
    public boolean userExists(String user) {
        String search = encodeString(user) + " ";
        // Search in .user_pass
        String dirName = "db";
        File dir = new File(dirName);
        if (!dir.exists()) {
            return false;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(dirName + "/.user_pass"))) {
            String input;
            while ((input = r.readLine()) != null) {
                if (input.startsWith(search)) {
                    return true;
                }
            }
        } catch (FileNotFoundException e) {
            //return false;
        } catch (IOException e) {
            //System.out.println("Could not read user_pass");
            //System.exit(-1);
        }
        return false;
    }
    
    public boolean login(String user, String pass) {
        String search = encodeString(user) + " " + encodeString(saltingStr + pass);
        // Search in .user_pass
        String dirName = "db";
        File dir = new File(dirName);
        if (!dir.exists()) {
            return false;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(dirName + "/.user_pass"))) {
            String input;
            while ((input = r.readLine()) != null) {
                if (input.equals(search)) {
                    return true;
                }
            }
        } catch (FileNotFoundException e) {
            //System.out.println("Could not read user_pass");
            //e.printStackTrace();
            //System.exit(-1);
        } catch (IOException e) {
            //System.out.println("Could not read user_pass");
            //System.exit(-1);
        }
        return false;
    }
}
