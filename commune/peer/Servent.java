package commune.peer;

import commune.net.Reactor;
import commune.net.Listener;
import commune.net.Operation;
import commune.net.CloseListener;
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
    private long localID;
    private Reactor reactor;
    private Source source;
    private File storageFolder;
    private int connectionLimit;
    private Map<Peer, Connection> connections;
    private Map<Long, Peer> knownPeers;
    private Set<Long> deadPeers;
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
        
        entropy = new Random();
        localID = entropy.nextLong();
        
        connections = Collections.synchronizedMap(
            new HashMap<Peer, Connection>());
        knownPeers = Collections.synchronizedMap(
            new HashMap<Long, Peer>());
        deadPeers = Collections.synchronizedSet(new HashSet<Long>());
        updater = new PeerUpdater();
        reactor.addCloseListener(new Disconnecter());
        
        serverChannel = null;
        
        new KeepAliveThread().start();
    }
    
    /**
     * Returns the unique ID of the local peer.
     * @return unique ID of the local peer
     */
    public long getLocalID() {
        return localID;
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
        return getKnownPeers(null);
    }
    
    public List<Peer> getKnownPeers(Connection exclude) {
        List<Peer> peerList;
        synchronized (knownPeers) {
            peerList = new ArrayList<Peer>(knownPeers.size());
            for (Peer peer : knownPeers.values()) {
                try {
                    if (peer.getAddress().getAddress().isLoopbackAddress())
                        continue;
                    if (exclude != null && connections.get(peer) == exclude)
                        continue;
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
            if (peer != null) {
                knownPeers.remove(peer.getID());
                deadPeers.add(peer.getID());
            }
            throw (IOException) new IOException(
                String.format("Failed to connect to %s.", peer)).initCause(e);
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
        Peer peer = Peer.fromAddress(address);
        if (peer == null)
            throw new UnknownHostException(address.getHostName());
        return getConnection(peer);
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
            storageFolder, localID, localAddress.getPort());
        connections.put(peer, connection);
        connection.sendHello();
        return connection;
    }
    
    private class AcceptListener implements Listener {
        public void ready(SelectableChannel channel) throws IOException {
            SocketChannel client = serverChannel.accept();
            client.configureBlocking(false);
            
            Connection con = new Connection(reactor, client, source,
                updater, storageFolder, localID, localAddress.getPort());
            System.out.printf("got new connection from %s%n",
                con.describeAddress());
        }
    }
    
    private class PeerUpdater implements PeerListener {
        public void peerConnected(Peer peer, Connection connection,
            boolean isServer)
        {
            synchronized (connections) {
                InetSocketAddress newRemote =
                    (InetSocketAddress) connection.getRemoteAddress();
                for (Map.Entry<Peer, Connection> e : connections.entrySet()) {
                    Peer existingPeer = e.getKey();
                    Connection existingCon = e.getValue();
                    if (existingCon == connection) {
                        if (existingPeer.getID() != peer.getID()) {
                            connections.remove(existingPeer);
                        } else {
                            continue;
                        }
                    }
                    
                    if (newRemote.equals(existingCon.getRemoteAddress())) {
                        System.err.printf("duplicate connection to %s%n",
                            connection.describeAddress());
                        connection.close();
                        return;
                    }
                }
                
                connections.put(peer, connection);
            }
            
            knownPeers.put(peer.getID(), peer);
            
            if (isServer && peer.exchangesPeers()) {
                connection.exchangePeers(getKnownPeers(connection));
            }
        }
        
        public void peerDisconnected(Peer peer) {
            System.out.printf("connection to %s closed%n", peer);
            connections.remove(peer);
            knownPeers.remove(peer.getID());
            deadPeers.add(peer.getID());
            openConnections();
        }
        
        public void peerResponded(Peer peer) {
            Peer realPeer = knownPeers.get(peer.getID());
            if (realPeer != null)
                realPeer.touch();
        }
        
        public void peersDiscovered(List<Peer> peers, Connection connection,
            boolean response)
        {
            for (Peer peer : peers) {
                if (peer.getID() == localID) {
                    // don't add ourselves as a known peer
                    continue;
                } else if (deadPeers.contains(peer.getID())) {
                    // peer is known to be dead
                    continue;
                }
                
                if (knownPeers.get(peer.getID()) == null) {
                    System.out.printf("discovered peer %s%n", peer);
                    knownPeers.put(peer.getID(), peer);
                }
            }
            
            if (!response) {
                List<Peer> ourPeers = new LinkedList<Peer>(knownPeers.values());
                for (Peer theirPeer : peers) {
                    ourPeers.remove(theirPeer);
                }
                
                connection.exchangePeers(ourPeers, true);
            }
            openConnections();
        }
    }
    
    private class Disconnecter implements CloseListener {
        public void channelClosed(SelectableChannel channel, Object attachment)
        {
            try {
                Connection con = (Connection) attachment;
                updater.peerDisconnected(con.getPeer());
            } catch (ClassCastException e) {
                System.err.println(e);
            } catch (NullPointerException e) {
                System.err.println(e);
            }
        }
    }
    
    private class KeepAliveThread extends Thread {
        private Queue<Connection> queue;
        private int count;
        
        public KeepAliveThread() {
            super("KeepAlive");
            setDaemon(true);
            queue = new LinkedList<Connection>();
            count = 0;
        }
        
        private void fill() {
            synchronized (connections) {
                count = 0;
                for (Connection con : connections.values()) {
                    if (con.isConnected()) {
                        queue.offer(con);
                        count++;
                    } else {
                        updater.peerDisconnected(con.getPeer());
                    }
                }
            }
        }
        
        public void run() {
            while (!Thread.interrupted()) {
                Connection next = queue.poll();
                if (next == null) {
                    fill();
                    next = queue.poll();
                }
                
                if (next != null) {
                    if (!next.isConnected()) {
                        updater.peerDisconnected(next.getPeer());
                        continue;
                    } else {
                        next.exchangePeers(getKnownPeers(next));
                    }
                } else {
                    // no active connections
                    count = 1;
                }
                
                try {
                    Thread.sleep((10000 + 1000 * entropy.nextInt(15)) / count);
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
