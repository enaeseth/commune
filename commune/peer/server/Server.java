package commune.peer.server;

import commune.peer.Reactor;
import commune.peer.ChannelListener;
import commune.peer.source.ResourceManager;
import commune.peer.source.AvailableResource;
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
        
        reactor.register(serverChannel, SelectionKey.OP_ACCEPT,
            new AcceptListener());
    }
    
    public AvailableResource getResource(String path) {
        return manager.getResource(path);
    }
    
    private class AcceptListener implements ChannelListener {
        public void ready(SelectableChannel channel, int operations)
            throws IOException
        {
            ServerSocketChannel server = (ServerSocketChannel) channel;
            SocketChannel client = server.accept();
            client.configureBlocking(false);
            System.out.printf("[server] got connection from %s%n",
                client.socket().getRemoteSocketAddress());
            
            new ClientConnection(Server.this, reactor, client);
        }
    }
}
