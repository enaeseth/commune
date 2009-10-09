package commune;

import commune.peer.Reactor;
import commune.peer.client.Client;
import commune.peer.server.Server;
import commune.peer.source.ResourceManager;
import commune.peer.source.DirectorySource;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

/**
 * The main class: creates a reactor, a client, and a server, and goes wild.
 */
public class Servent implements Runnable {
    private ResourceManager manager;
    private Reactor reactor;
    private Server server;
    private Client client;
    private int serverPort;
    
    public Servent(int serverPort, File contentDir, File downloadDir)
        throws IOException
    {
        if (!contentDir.isDirectory())
            contentDir.mkdirs();
        if (!downloadDir.isDirectory())
            downloadDir.mkdirs();
        
        manager = new ResourceManager();
        manager.addSource(new DirectorySource("/", contentDir));
        
        reactor = new Reactor();
        server = new Server(reactor, manager);
        this.serverPort = serverPort;
        client = new Client(reactor, downloadDir);
    }
    
    public void run() {
        // Create and start the reactor thread
        final Reactor reactor = this.reactor;
        final Thread mainThread = Thread.currentThread();
        final Thread reactorThread = new Thread(new Runnable() {
            public void run() {
                try {
                    reactor.run();
                } catch (IOException e) {
                    System.err.println(e);
                    System.exit(1);
                }
            }
        }, "Reactor");
        
        try {
            server.listen(serverPort);
            System.out.printf("Listening on port %d.%n", serverPort);
        } catch (IOException e) {
            System.err.printf("failed to listen on port %d: %s%n",
                serverPort, e);
            System.exit(1);
        }
        
        reactorThread.start();
        
        Scanner in = new Scanner(System.in);
        int choice = 1;
        int port;
        String fname;
        String host;
        
        try {
            while (choice == 1) {
                System.out.print("Press 1 to request a file, 2 to quit:  ");
                choice = Integer.parseInt(in.nextLine().trim());
                if (choice == 1) {
                    System.out.print("Enter path to remote file:    ");
                    fname = in.nextLine();
                    System.out.print("Name or IP address of host?    ");
                    host = in.nextLine();
                    System.out.print("Port of host?  ");
                    port = Integer.parseInt(in.nextLine().trim());
                    
                    if (fname.charAt(0) != '/')
                        fname = "/" + fname;
                    
                    download(host, port, fname);
                } else if (choice == 2) {
                    System.out.println("Bye!");
                }
            }
        } catch (NumberFormatException e) {
            System.out.println("Error:  non-numerical option entered.");
        }
        
        reactorThread.interrupt();
    }
    
    private void download(String host, int port, String path) {
        try {
            // Start the request
            Future<File> download = client.request(host, port, path);
            // Block waiting for the download to finish
            File downloadedFile = download.get();
            // Share the good news
            System.out.printf("Downloaded //%s:%d%s to %s.%n",
                host, port, path, downloadedFile);
        } catch (ExecutionException e) {
            System.err.printf("Failed to download //%s:%d%s%n",
                host, port, path);
            e.getCause().printStackTrace();
        } catch (Exception e) {
            System.err.printf("Failed to download //%s:%d%s%n",
                host, port, path);
            e.printStackTrace();
        }
    }
    
    public static void main(String... args) {
        int port = Server.DEFAULT_PORT;
        
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("usage: commune [port]");
                return;
            }
        }
        
        try {
            Servent servent = new Servent(port, new File("Content"),
                new File("Downloads"));
            servent.run();
        } catch (IOException e) {
            System.err.println(e);
        }
    }
}
