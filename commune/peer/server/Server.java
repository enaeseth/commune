package commune.peer.server;

import commune.net.*;
import commune.source.ResourceManager;
import commune.source.AvailableResource;
import commune.protocol.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.nio.*;
import java.nio.channels.*;

public class Server {
    private Reactor reactor;
    private ResourceManager manager;
    private ServerSocketChannel serverChannel;
    
    public static final int DEFAULT_PORT = 2666;
    
    public Server(Reactor reactor, ResourceManager manager) {
        this.reactor = reactor;
        this.manager = manager;
        
        serverChannel = null;
    }
    
    public void listen(int port) throws IOException {
        serverChannel = ServerSocketChannel.open();
        
        ServerSocket socket = serverChannel.socket();
        socket.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        System.out.printf("[server] listening on port %d%n", port);
        
        reactor.listen(serverChannel, Operation.ACCEPT, new AcceptListener());
    }
    
    public AvailableResource getResource(String path) {
        return manager.getResource(path);
    }
    
    private class AcceptListener implements Listener {
        public void ready(SelectableChannel channel) throws IOException {
            SocketChannel client = serverChannel.accept();
            client.configureBlocking(false);
            System.out.printf("[server] got connection from %s%n",
                client.socket().getRemoteSocketAddress());
            
            new ClientConnection(Server.this, reactor, client);
        }
    }
    
    public static void main(String... args) throws IOException {
        Reactor reactor = new Reactor();
        Server server = new Server(reactor, null);
        server.listen(DEFAULT_PORT);
        reactor.run();
    }
}
