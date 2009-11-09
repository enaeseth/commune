package commune.peer;

import commune.net.Reactor;
import commune.net.Listener;
import commune.net.Operation;
import commune.source.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.Future;

/**
 * A servent: a client and server rolled into one.
 */
public class Servent {
    private Reactor reactor;
    private Source source;
    private File storageFolder;
    private int connectionLimit;
    private Map<Peer, Connection> connections;
    private Set<Peer> knownPeers;
    private ServerSocketChannel serverChannel;
    private InetSocketAddress localAddress;
    private PeerListener updater;
    private Random entropy;
    
    public static final int DEFAULT_PORT = 2666;
    
    public Servent(Reactor reactor, Source source, File storageFolder,
        int connectionLimit)
    {
        this.reactor = reactor;
        this.source = source;
        this.storageFolder = storageFolder;
        this.connectionLimit = connectionLimit;
        
        updater = new PeerUpdater();
        connections = Collections.synchronizedMap(
            new HashMap<Peer, Connection>());
        knownPeers = Collections.synchronizedSet(new HashSet<Peer>());
        
        serverChannel = null;
        
        entropy = new Random();
        new KeepAliveThread().start();
    }
    
    /**
     * Starts listening for peer connections on the given port.
     */
    public void listen(int port) throws IOException {
        serverChannel = ServerSocketChannel.open();
        
        ServerSocket socket = serverChannel.socket();
        socket.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        System.out.printf("Listening for peer connections on port %d%n", port);
        
        localAddress =
            (InetSocketAddress) serverChannel.socket().getLocalSocketAddress();
        
        reactor.listen(serverChannel, Operation.ACCEPT, new AcceptListener());
    }
    
    public List<Connection> getConnections() {
        synchronized (connections) {
            return new ArrayList<Connection>(connections.values());
        }
    }
    
    public List<Peer> getKnownPeers() {
        List<Peer> peerList;
        synchronized (knownPeers) {
            peerList = new ArrayList<Peer>(knownPeers.size());
            for (Peer peer : knownPeers) {
                try {
                    if (!peer.getAddress().getAddress().isLoopbackAddress())
                        peerList.add(peer);
                } catch (UnknownHostException e) {
                    // ignore that peer
                }
            }
        }
        
        // sort so that "fresher" peers appear first
        Collections.sort(peerList);
        return peerList;
    }
    
    public List<Peer> getUnconnectedPeers() {
        List<Peer> unconnected = new LinkedList<Peer>();
        
        for (Peer peer : getKnownPeers()) {
            if (!connections.containsKey(peer))
                unconnected.add(peer);
        }
        return unconnected;
    }
    
    /**
     * Returns the soft connection limit for this servent.
     * @return soft connection limit for this servent
     */
    public int getConnectionLimit() {
        return connectionLimit;
    }
    
    public boolean isBelowLimit() {
        return (connections.size() < connectionLimit);
    }
    
    public Connection openConnection() throws IOException {
        Peer peer = null;
        try {
            List<Peer> available = getUnconnectedPeers();
            if (available.size() <= 0)
                return null;
            peer = available.get(0);
            return getConnection(peer);
        } catch (IOException e) {
            if (peer != null)
                knownPeers.remove(peer);
            throw new IOException(
                String.format("Failed to connect to %s.", peer), e);
        }
    }
    
    private void openConnections() {
        while (isBelowLimit()) {
            try {
                if (openConnection() == null)
                    break;
            } catch (IOException e) {
                System.err.println(e.getMessage());
                if (e.getCause() != null)
                    System.err.printf("    %s%n", e.getCause().getMessage());
            }
        }
    }
    
    public Connection getConnection(String host, int port) throws IOException {
        InetAddress hostAddress = InetAddress.getByName(host);
        InetSocketAddress address = new InetSocketAddress(hostAddress, port);
        return getConnection(address);
    }
    
    public Connection getConnection(InetSocketAddress address)
        throws IOException
    {
        return getConnection(Peer.fromAddress(address));
    }
    
    public Connection getConnection(Peer peer) throws IOException {
        Connection connection = connections.get(peer);
        if (connection != null) {
            if (connection.isConnected()) {
                return connection;
            } else {
                updater.peerDisconnected(peer);
            } 
        }
        
        SocketChannel channel = SocketChannel.open();
        channel.connect(peer.getAddress());
        channel.configureBlocking(false);
        connection = new Connection(reactor, channel, source, updater,
            storageFolder);
        connections.put(peer, connection);
        connection.sendHello();
        return connection;
    }
    
    private class AcceptListener implements Listener {
        public void ready(SelectableChannel channel) throws IOException {
            SocketChannel client = serverChannel.accept();
            client.configureBlocking(false);
            
            Connection con = new Connection(reactor, client, source,
                updater, storageFolder);
            System.out.printf("got new connection from %s%n",
                con.describeAddress());
        }
    }
    
    /**
     * Checks to see if the given 'peer' (found through discovery) is really
     * us.
     */
    private boolean isSelf(Peer peer) {
        try {
            List<InetSocketAddress> peerAddresses = peer.getAddresses();
            InetAddress myHost = localAddress.getAddress();
            NetworkInterface iface;
        
            if (myHost.isAnyLocalAddress()) {
                Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();
            
                while (interfaces.hasMoreElements()) {
                    iface = interfaces.nextElement();
                    if (isSelf(peerAddresses, iface))
                        return true;
                }
            } else {
                iface = NetworkInterface.getByInetAddress(myHost);
                if (iface != null)
                    return isSelf(peerAddresses, iface);
            }
        } catch (SocketException e) {
            // ignore
        }
        
        return false;
    }
    
    private boolean isSelf(List<InetSocketAddress> peerAddresses,
        NetworkInterface iface) throws SocketException
    {
        Enumeration<InetAddress> ifAddresses =
            iface.getInetAddresses();
        while (ifAddresses.hasMoreElements()) {
            InetSocketAddress full = new InetSocketAddress(
                ifAddresses.nextElement(), localAddress.getPort());
            for (InetSocketAddress possible : peerAddresses) {
                if (full.equals(possible))
                    return true;
            }
        }
        return false;
    }
    
    private class PeerUpdater implements PeerListener {
        public void peerConnected(Peer peer, Connection connection,
            boolean isServer)
        {
            Connection existing = connections.get(peer);
            if (existing != null && existing != connection) {
                System.err.printf("duplicate connection to %s%n",
                    connection.describeAddress());
                connection.close();
                return;
            }
            
            if (peer.getUserAgent() != null) {
                // replace any previous connection entry, as its peer probably
                // doesn't have the user agent populated
                synchronized (connections) {
                    connections.remove(peer);
                    connections.put(peer, connection);
                }
            } else {
                connections.put(peer, connection);
            }
            if (!isServer)
                knownPeers.add(peer);
            
            if (isServer && peer.exchangesPeers()) {
                connection.exchangePeers(getKnownPeers());
            }
        }
        
        public void peerDisconnected(Peer peer) {
            System.out.printf("connection to %s closed%n", peer);
            connections.remove(peer);
            knownPeers.remove(peer);
            openConnections();
        }
        
        public void peerResponded(Peer peer) {
            peer.touch();
        }
        
        public void peersDiscovered(List<Peer> peers, Connection connection,
            boolean response)
        {
            for (Peer peer : peers) {
                if (isSelf(peer)) {
                    // don't add ourselves as a known peer
                    continue;
                }
                
                if (knownPeers.add(peer)) {
                    System.out.printf("discovered peer %s%n", peer);
                }
            }
            
            if (!response) {
                List<Peer> ourPeers = new LinkedList<Peer>(knownPeers);
                for (Peer theirPeer : peers) {
                    ourPeers.remove(theirPeer);
                }
                
                connection.exchangePeers(ourPeers, true);
            }
            openConnections();
        }
    }
    
    private class KeepAliveThread extends Thread {
        public KeepAliveThread() {
            super("KeepAlive");
            setDaemon(true);
        }
        
        public void run() {
            while (!Thread.interrupted()) {
                Connection oldest = null;
                long lastContact = 0;

                for (Connection con : getConnections()) {
                    if (!con.isConnected()) {
                        updater.peerDisconnected(con.getPeer());
                        continue;
                    }
                    long c = con.getLastContact();
                    if (c > lastContact) {
                        lastContact = c;
                        oldest = con;
                    }
                }

                if (oldest != null) {
                    oldest.exchangePeers(getKnownPeers());
                }
                
                try {
                    Thread.sleep(10000 + 1000 * entropy.nextInt(20));
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
    
    public static void main(String... args) throws IOException {
        Reactor reactor = new Reactor();
        Servent servent = new Servent(reactor,
            new DirectorySource("/", new File("Content")),
            new File("Downloads"), 3);
        servent.listen(DEFAULT_PORT);
        reactor.run();
    }
}
