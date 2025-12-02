import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Replace PORT_NUMBER and CREDENTIALS_FILE
// javac src/*.java && java -cp src ChatServer PORT_NUMBER CREDENTIALS_FILE
// For example,
// javac src/*.java && java -cp src ChatServer 12345 sample_users.txt
public class ChatServer {
    private final int MAX_CONNECTIONS = 100;
    private final int port;
    private final Map<String, Connection> connections;
    private final Map<String, String> user2pass;

    private class Connection implements AutoCloseable {
        private Socket socket;
        private BufferedWriter bw;
        private BufferedReader br;

        public Connection(Socket socket) throws IOException {
            this.socket = socket;
            br = new BufferedReader(new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
        }

        public void sendln(String msg) throws IOException {
            bw.write(msg);
            bw.newLine();
            bw.flush();
        }

        public String readLine() throws IOException {
            return br.readLine();
        }

        @Override
        public void close() throws IOException{
            socket.close();
        }        
    }


    public static void main(String[] args) throws IOException{
        if (args.length != 2) {
            System.err.println("Usage: java -cp src ChatServer <port> <credentialsFile>");
            System.exit(1);
        }

        int port = 0;
        try {
            port = Integer.parseInt(args[0]);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("Port out of range");
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid port: " + args[0]);
            System.exit(1);
        }

        String filename = args[1];

        Map<String, String> creds = new HashMap<>();
        try {
            creds = loadCredentials(filename);
        } catch (IOException e) {
            System.err.println("Error with credentials file: " + filename);
            System.err.println(e.getMessage());
            System.exit(1);
        }

        ChatServer server = new ChatServer(port, creds);
        server.serve();
    }

    private static Map<String, String> loadCredentials(String filename) throws IOException {
        Map<String, String> creds = new HashMap<>();
        try (Scanner sc = new Scanner(new File(filename))) {
            while (sc.hasNext()) {
                String user = sc.next();
                if (!sc.hasNext()) {
                    throw new IOException("Credentials file has an odd number of tokens; missing password for user: " + user);
                }
                String pass = sc.next();
                creds.put(user, pass);
            }
        }
        return creds;
    }


    public ChatServer(int port, Map<String, String> user2pass) {
        this.port = port;
        connections = new ConcurrentHashMap<>();
        this.user2pass = user2pass;

    }

    public void serve() throws IOException {
        ExecutorService pool = Executors.newFixedThreadPool(MAX_CONNECTIONS);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                pool.submit(() -> {try {
                        connectClient(socket);
                    } catch (IOException e ) {
                        System.err.println("nope nope nope");
                    }
                });
            }
        }
        
    }

    public void sendToAll(String user, String message)  {
        System.out.println("[" + user + "]: " + message);
        for(Connection connection : connections.values()) {
            try {
                connection.sendln("[" + user + "]: " + message);
            } catch (IOException e) {
                System.err.println("Lol didn't work");
                System.err.println(e.getMessage());
            }
            
        }
    }

    public void connectClient(Socket socket) throws IOException {
        Connection connection = new Connection(socket);
        try (connection){
            connection.sendln("Please enter your username and password, separated by a space");
            String authString = connection.readLine();
            if(authString == null) return; // If we get null, the client has already disconnected

            String[] parts = authString.split(" ");
            if(parts.length != 2) {
                connection.sendln("Invalid username password format. Got: '" + authString + "'");
                connection.sendln("Closing connection, please try again with correct authentication");
                return;
            }
            String user = parts[0];
            String pass = parts[1];

            if(!user2pass.containsKey(user)) {
                connection.sendln("Unknown user: '" + user + "'");
                connection.sendln("Closing connection, please try again with correct authentication");
                return;
            }

            if(!pass.equals(user2pass.get(user))) {
                connection.sendln("Incorrect password for user");
                connection.sendln("Closing connection, please try again with correct authentication");
                return;
            }

            if (connections.putIfAbsent(user, connection) != null) {
                connection.sendln("You are already connected with a different client to this server. Please disconnect the other client before continuing");
                return;
            }

            try {
                connection.sendln("Welcome to the server!");
                connection.sendln("Please be kind and respectful to your fellow classmates!");

                String line;
                while((line = connection.readLine()) != null) {
                    System.out.println(line);
                    sendToAll(user, line);
                }
            } finally {
                connections.remove(user);
            }
            

        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

    }
}
