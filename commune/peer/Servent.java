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
import java.util.concurrent.ExecutionException;

/**
 * A servent: a client and server rolled into one.
 * 
 * A Servent object manages all connections to other peers, manages the
 * server socket that listens for new peer connections, and tracks known and
 * known-dead peers.
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
    
    public static final int DEFAULT_PORT = 2375;
    
    /**
     * Creates a new servent.
     *
     * The servent will not immediately begin listening for connections;
     * use {@link listen} to begin listening on a port.
     * 
     * @param reactor the reactor to use to manage sockets
     * @param source the source that provides the resources available on this
     *        servent
     * @param storageFolder the folder to which resources downloaded from
     *        other peers will be saved
     * @param connectionLimit the soft limit on the number of connections to
     *        maintain. This limit only affects automatic connections to peers
     *        that have been learned about through peer discovery. Incoming
     *        connections will never be rejected for exceeding this limit,
     *        and manual requests for a connection will never fail for
     *        exceeding this limit.
     */
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
    
    /**
     * Returns a list of all open connections.
     */
    public List<Connection> getConnections() {
        synchronized (connections) {
            return new ArrayList<Connection>(connections.values());
        }
    }
    
    /**
     * Returns a list of all known peers.
     */
    public List<Peer> getKnownPeers() {
        return getKnownPeers(null);
    }
    
    /**
     * Returns a list of all known peers, except the peer that is on the other
     * end of the given connection.
     * @param exclude the connection whose remote peer will be excluded from
     *        the returned list. If this parameter is <code>null</code>, no
     *        peers will be excluded.
     */
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
    
    /**
     * Returns a list of peers to which there are currently no connections.
     */
    public List<Peer> getUnconnectedPeers() {
        List<Peer> unconnected = new LinkedList<Peer>();
        
        for (Peer peer : getKnownPeers()) {
            if (!connections.containsKey(peer) && !deadPeers.contains(peer.getID()))
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
    
    /**
     * Returns the true if the number of open connections is less than the
     * soft connection limit; false if otherwise.
     * @return true if the number of open connections is less than the
     * soft connection limit; false if otherwise
     */
    public boolean isBelowLimit() {
        return (connections.size() < connectionLimit);
    }
    
    /**
     * Opens a connection to any known, unconnected peer.
     * @return the newly-opened connection. If there are no known peers without
     *         connections, <code>null</code> will be returned.
     */
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
    
    /**
     * Gets a connection to the peer at the given host and port, opening it
     * if necessary.
     */
    public Connection getConnection(String host, int port) throws IOException {
        InetAddress hostAddress = InetAddress.getByName(host);
        InetSocketAddress address = new InetSocketAddress(hostAddress, port);
        return getConnection(address);
    }
    
    /**
     * Gets a connection to the peer at the given socket address, opening it
     * if necessary.
     */
    public Connection getConnection(InetSocketAddress address)
        throws IOException
    {
        Peer peer = Peer.fromAddress(address);
        if (peer == null)
            throw new UnknownHostException(address.getHostName());
        return getConnection(peer);
    }
    
    /**
     * Gets a connection to the given peer, opening it if necessary.
     */
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
            storageFolder, localID, localAddress.getPort(), peer.getID());
        connections.put(peer, connection);
        connection.sendHello();
        return connection;
    }
    
    /**
     * Asks all connected peers for information on a resource.
     * Returns a mapping between peers and the resources they returned.
     * Peers that did not have a copy of the resource or returned an error
     * are not included in the map.
     * 
     * This method blocks until all connected peers have been queried for
     * the resource.
     */
    public Map<Peer, Resource> find(String path) {
        Queue<Connection> queue = new LinkedList<Connection>();
        Map<Peer, Resource> found = new HashMap<Peer, Resource>();
        
        synchronized (connections) {
            for (Connection con : connections.values()) {
                if (con.isConnected())
                    queue.offer(con);
            }
        }
        
        Connection con;
        while ((con = queue.poll()) != null) {
            try {
                Resource resource = con.describe(path).get();
                found.put(con.getPeer(), resource);
            } catch (IOException e) {
                // ignore
            } catch (ExecutionException e) {
                // ignore
            } catch (InterruptedException e) {
                queue.offer(con);
            }
        }
        
        return found;
    }
    
    private class AcceptListener implements Listener {
        public void ready(SelectableChannel channel) throws IOException {
            SocketChannel client = serverChannel.accept();
            client.configureBlocking(false);
            
            Connection con = new Connection(reactor, client, source,
                updater, storageFolder, localID, localAddress.getPort(), 0L);
            // System.out.printf("got new connection from %s%n",
            //     con.describeAddress());
        }
    }
    
    /**
     * Returns a peer from the known-peers list that is "equivalent" to the
     * given peer.
     * 
     * A peer is considered equivalent to the given peer if the given peer
     * has a non-zero ID and it is equal to another peer's ID, or if the
     * peer's remote address is the same as another peer's address.
     */
    public Peer getEquivalentPeer(Peer peer) {
        long id = peer.getID();
        if (id != 0) {
            Peer equiv = knownPeers.get(id);
            if (equiv != null)
                return equiv;
        }
        
        synchronized (knownPeers) {
            for (Peer possible : knownPeers.values()) {
                if (possible.sameAddress(peer))
                    return possible;
            }
        }
        
        return null;
    }
    
    private class PeerUpdater implements PeerListener {
        public void unexpectedPeerID(long expectedID, Peer actual) {
            synchronized (knownPeers) {
                knownPeers.remove(expectedID);
                deadPeers.add(expectedID);
            }
        }
        
        public void peerConnected(Peer peer, Connection connection,
            boolean isServer)
        {
            boolean connectedToSelf = (peer.getID() == localID);
            
            synchronized (connections) {
                InetSocketAddress remote =
                    (InetSocketAddress) connection.getRemoteAddress();
                for (Map.Entry<Peer, Connection> e : connections.entrySet()) {
                    Peer existingPeer = e.getKey();
                    Connection existingCon = e.getValue();
                    if (existingCon == connection) {
                        if (connectedToSelf)
                            knownPeers.remove(existingPeer.getID());
                        connections.remove(existingPeer);
                    } else if (remote.equals(existingCon.getRemoteAddress())) {
                        System.err.printf("duplicate connection to %s%n",
                            connection.describeAddress());
                        connection.close();
                        return;
                    }
                }
                
                if (connectedToSelf && !isServer) {
                    // oops, we just connected to ourselves
                    knownPeers.remove(peer.getID());
                    connection.close();
                    return;
                }
                connections.put(peer, connection);
            }
            
            knownPeers.put(peer.getID(), peer);
            
            if (isServer && peer.exchangesPeers()) {
                connection.exchangePeers(getKnownPeers(connection));
            }
        }
        
        public void peerDisconnected(Peer peer) {
            System.out.print("connection to ");
            if (peer.getID() != 0)
                System.out.printf("%016x ", peer.getID());
            System.out.printf("(%s:%d) closed%n", peer.getHost(),
                peer.getPort());
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
                
                Peer existing = getEquivalentPeer(peer);
                if (existing == null && peer.getID() != localID) {
                    System.out.printf("discovered peer %016x (%s:%d)%n",
                        peer.getID(), peer.getHost(), peer.getPort());
                    knownPeers.put(peer.getID(), peer);
                } else if (existing.getID() != peer.getID()) {
                    synchronized (connections) {
                        Connection existingCon = connections.get(existing);
                        if (existingCon == null) {
                            System.out.printf("peer at %s:%d; " +
                                "%016x => %016x%n", peer.getHost(),
                                peer.getPort(), existing.getID(),
                                peer.getID());
                            knownPeers.remove(existing.getID());
                            if (peer.getID() != localID)
                                knownPeers.put(peer.getID(), peer);
                            deadPeers.add(existing.getID());
                        }
                    }
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
    
    /**
     * Periodically checks peer connections by sending a peer exchange
     * message if the remote peer supports PEX or a hello message if it does
     * not.
     */
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
                        if (next.getPeer().exchangesPeers())
                            next.exchangePeers(getKnownPeers(next));
                        else
                            next.sendHello();
                    }
                } else {
                    // no active connections
                    count = 1;
                }
                
                try {
                    Thread.sleep((5000 + 1000 * entropy.nextInt(10)) / count);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
