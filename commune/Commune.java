package commune;

import commune.net.Reactor;
import commune.peer.Connection;
import commune.peer.Peer;
import commune.peer.Servent;
import commune.peer.Resource;
import commune.source.ResourceManager;
import commune.source.*;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

public class Commune {
    private Servent servent = null;
    
    public static void main(String... args) {
        try {
            new Commune().run(args);
        } catch (UnknownHostException e) {
            System.err.printf("error: unknown host: %s%n", e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.printf("fatal I/O error: %s%n", e.getMessage());
            System.exit(1);
        }
    }
    
    private static InetSocketAddress parseAddress(String address) {
        String[] parts = address.split(":");
        int port = Servent.DEFAULT_PORT;
        if (parts.length > 1)
            port = Integer.parseInt(parts[1]);
        return new InetSocketAddress(parts[0], port);
    }
    
    public void run(String... args) throws IOException {
        Reactor reactor = new Reactor();
        Source source = new DirectorySource("/", new File("Content"));
        File storage = new File("Downloads");
        int port = Servent.DEFAULT_PORT;
        int maxConnections = 3;
        
        List<InetSocketAddress> peerAddresses =
            new LinkedList<InetSocketAddress>();
        
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-h") || args[i].equals("--help")) {
                System.err.println("usage: commune [-p port] [-l limit] " +
                    "[host[:port]] [host[:port]] [...]");
                return;
            }
            
            if (args[i].equals("-p")) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-l")) {
                port = Integer.parseInt(args[++i]);
            } else {
                peerAddresses.add(parseAddress(args[i]));
            }
        }
        
        servent = new Servent(reactor, source, storage, maxConnections);
        servent.listen(port);
        Thread reactorThread = reactor.start();
        
        for (InetSocketAddress peer : peerAddresses) {
            servent.getConnection(peer);
        }
        
        System.out.println("Welcome to Commune.");
        System.out.println("Type \"help\" for a list of commands.");
        
        Scanner in = new Scanner(System.in);
        while (true) {
            try {
                System.out.print("commune> ");
                String command = in.nextLine().trim();
            
                if ("help".equals(command)) {
                    showHelp();
                } else if ("exit".equals(command) || "quit".equals(command)) {
                    break;
                } else if (command.startsWith("connect ")) {
                    connectTo(command.substring("connect ".length()));
                    pause();
                } else if ("connections".equals(command)) {
                    showConnections();
                } else if ("discover".equals(command)) {
                    discoverPeers();
                    pause();
                } else if (command.startsWith("find ")) {
                    findFile(command.substring("find ".length()));
                } else if (command.startsWith("get ")) {
                    String[] parts = command.substring("get ".length()).
                        split(" ");
                    if (parts.length < 2) {
                        requestFile(parts[0]);
                    } else {
                        requestFile(parts[0], parts[1]);
                    }
                    pause();
                } else if ("peers".equals(command)) {
                    showKnownPeers();
                } else if ("whoami".equals(command)) {
                    System.out.printf("Peer ID: %016x%n",
                        servent.getLocalID());
                } else if (command.length() > 0) {
                    System.err.println("Unknown command. " +
                        "Type \"help\" for help.");
                }
            } catch (IOException e) {
                System.err.printf("I/O error: %s%n", e.getMessage());
            }
        }
        
        System.out.println("Shutting down.");
        reactorThread.interrupt();
    }
    
    private void pause() {
        try {
            Thread.sleep(800L);
        } catch (InterruptedException e) {
            // ignore
        }
    }
    
    private static void showHelp() {
        System.out.println("Available commands:");
        System.out.println("  connect host[:port]      Open a new connection");
        System.out.println("  connections              List open connections");
        System.out.println("  discover                 Discover new peers");
        System.out.println("  exit                     Quit the program");
        System.out.println("  find path                Find copies of the " +
            "file on connected peers");
        System.out.println("  get [host[:port]] path   Request a file");
        System.out.println("  peers                    Show all known peers");
        System.out.println("  whoami                   Show local peer ID");
    }
    
    private void connectTo(String host) throws IOException {
        servent.getConnection(parseAddress(host));
    }
    
    private void showConnections() throws IOException {
        List<Connection> connections = servent.getConnections();
        
        if (connections.size() <= 0) {
            System.out.println("No connections are open.");
        } else {
            System.out.printf("%d connection(s) are open:%n",
                connections.size());
            long now = System.currentTimeMillis();
            for (Connection connection : connections) {
                System.out.printf("  %s", connection.describeAddress());
                String agent = connection.getPeer().getUserAgent();
                if (agent != null)
                    System.out.printf(", using %s", agent);
                System.out.printf(", %d seconds%n",
                    (now - connection.getLastContact()) / 1000);
            }
        }
    }
    
    private void discoverPeers() throws IOException {
        for (Connection connection : servent.getConnections()) {
            if (connection.getPeer().exchangesPeers()) {
                connection.exchangePeers(servent.getKnownPeers(connection));
            }
        }
    }
    
    private String cleanPath(String path) {
        if (!path.startsWith("/"))
            path = "/" + path;
        return path;
    }
    
    private void findFile(String path) {
        path = cleanPath(path);
        showSearchResults(path, servent.find(path));
    }
    
    private void requestFile(String path) throws IOException {
        path = cleanPath(path);
        
        Map<Peer, Resource> resources = servent.find(path);
        if (resources.size() == 0) {
            showSearchResults(path, resources);
            return;
        }
        
        Resource blessed = null;
        for (Resource resource : resources.values()) {
            if (blessed == null) {
                blessed = resource;
            } else if (!blessed.equals(resource)) {
                // Multiple different resources exist for that name; show a
                // list of them.
                showSearchResults(path, resources);
                return;
            }
        }
        
        // All the copies of this resource on the network represent the same
        // file. Download from a random peer.
        ArrayList<Peer> peers = new ArrayList<Peer>(resources.keySet());
        Peer chosenOne = peers.get(new Random().nextInt(peers.size()));
        
        Connection con = servent.getConnection(chosenOne);
        if (con != null) {
            requestFile(con, path);
        } else {
            System.err.print("Failed to open connection");
            if (peers.size() > 1)
                System.err.print("; please try again");
            System.err.println(".");
        }
    }
    
    private void requestFile(String host, String path) throws IOException {
        path = cleanPath(path);
        
        InetSocketAddress address = parseAddress(host);
        Connection con = servent.getConnection(address);
        if (con != null) {
            requestFile(con, path);
        } else {
            System.err.println("Failed to open connection.");
        }
    }
    
    private void requestFile(Connection con, String path) throws IOException {
        try {
            // Start the request
            Future<File> download = con.request(path);
            // Block waiting for the download to finish
            File downloadedFile = download.get();
            // Share the good news
            System.out.printf("Downloaded //%s%s to %s.%n",
                con.describeAddress(), path, downloadedFile);
        } catch (ExecutionException e) {
            System.err.printf("Failed to download //%s%s%n",
                con.describeAddress(), path);
            e.getCause().printStackTrace();
        } catch (Exception e) {
            System.err.printf("Failed to download //%s%s%n",
                con.describeAddress(), path);
            e.printStackTrace();
        }
    }
    
    private void showSearchResults(String path, Map<Peer, Resource> resources)
    {
        if (resources.size() == 0) {
            System.out.printf("File \"%s\" was not found on any peers.%n",
                path);
            return;
        }
        
        System.out.printf("Found %d result(s) for file \"%s\".%n",
            resources.size(), path);
        for (Map.Entry<Peer, Resource> e : resources.entrySet()) {
            Peer peer = e.getKey();
            Resource resource = e.getValue();
            
            System.out.printf("  peer %16x (%s:%d):%n", peer.getID(),
                peer.getHost(), peer.getPort());
            System.out.printf("    %s, %s", describeSize(resource.getLength()),
                resource.getContentType());
            byte[] digest = resource.getDigest();
            if (digest != null)
                System.out.printf(", %s", toHexString(digest));
            System.out.println();
        }
    }
    
    private String describeSize(long bytes) {
        String[] suffixes = new String[] {"B", "KB", "MB", "GB", "PB"};
        
        double size = (double) bytes;
        int i;
        for (i = 0; i < suffixes.length; i++) {
            if (size < 1000)
                break;
            size /= 1000;
        }
        
        return (i == 0)
            ? String.format("%d %s", bytes, suffixes[i])
            : String.format("%.02f %s", size, suffixes[i]);
    }
    
    private String toHexString(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        
        return builder.toString();
    }
    
    private void showKnownPeers() throws IOException {
        List<Peer> peers = servent.getKnownPeers();
        int count = peers.size();
        
        if (count <= 0) {
            System.out.println("No peers have been discovered.");
        } else {
            System.out.printf("%d peer(s) have been discovered:%n", count);
            long now = System.currentTimeMillis();
            
            for (Peer peer : peers) {
                System.out.printf("  %016x; %s:%d; %d seconds%n",
                    peer.getID(), peer.getHost(), peer.getPort(),
                    (now - peer.getLastContact()) / 1000);
            }
        }
    }
}
