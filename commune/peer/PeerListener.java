package commune.peer;

import java.util.List;

/**
 * Interface for notifying objects of peer connection and discovery events.
 */
public interface PeerListener {
    /**
     * Called when a new connection is established with another peer.
     */
    public void peerConnected(Peer peer, Connection connection,
        boolean isServer);
    
    /**
     * Called when a connection to another peer is broken.
     */
    public void peerDisconnected(Peer peer);
    
    /**
     * Called whenever a message is received from a peer.
     */
    public void peerResponded(Peer peer);
    
    /**
     * Called when new peers are discovered through peer exchange (PEX).
     */
    public void peersDiscovered(List<Peer> peers, Connection connection,
        boolean response);
    
    /**
     * Called when a connection made to a peer reported an ID that was not
     * expected.
     * 
     * This could happen when (e.g.), a peer is discovered through exchange,
     * but it has restarted (and hence generated a new ID for itself) in
     * between us hearing about it and us connecting to it.
     */
    public void unexpectedPeerID(long expectedID, Peer actual);
}
