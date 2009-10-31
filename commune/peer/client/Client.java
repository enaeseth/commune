package commune.peer.client;

import commune.peer.Reactor;
import commune.peer.ChannelListener;
import commune.protocol.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.Future;

public class Client {
    private Reactor reactor;
    private File storage;
    private Map<InetSocketAddress, ServerConnection> connections;
    
    public Client(Reactor reactor, File storageDirectory) {
        this.reactor = reactor;
        storage = storageDirectory;
        connections = new HashMap<InetSocketAddress, ServerConnection>();
    }
    
    public Future<File> request(String host, int port, String path)
        throws IOException
    {
        InetAddress hostAddress = InetAddress.getByName(host);
        InetSocketAddress address = new InetSocketAddress(hostAddress, port);
        
        ServerConnection con = connections.get(address);
        if (con == null || !((SocketChannel) con.getChannel()).isConnected()) {
            SocketChannel channel = SocketChannel.open();
            channel.connect(address);
            channel.configureBlocking(false);
            
            con = new ServerConnection(reactor, channel, storage);
        }
        
        return con.request(path);
    }
}
