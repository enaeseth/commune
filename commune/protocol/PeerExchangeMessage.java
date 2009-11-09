package commune.protocol;

import commune.peer.Peer;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.BufferOverflowException;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;


public class PeerExchangeMessage extends Message {
    public static final short CODE = 0x20;
    
    private List<Peer> peers;
    private boolean response;
    
    public PeerExchangeMessage() {
        this(new ArrayList<Peer>(0));
    }
    
    public PeerExchangeMessage(List<Peer> peers) {
        this(peers, false);
    }
    
    public PeerExchangeMessage(List<Peer> peers, boolean response) {
        super(CODE);
        this.peers = peers;
        this.response = response;
    }
    
    /**
     * Returns the list of peers contained in this message.
     * @return list of peers contained in this message
     */
    public List<Peer> getPeers() {
        return Collections.unmodifiableList(peers);
    }
    
    /**
     * Returns true if this is a response to another exchange message;
     * false if otherwise.
     * @return true if this is a response to another exchange message;
     *         false if otherwise
     */
    public boolean isResponse() {
        return response;
    }
    
    public ByteBuffer getBytes() {
        ByteBuffer peerBuffer = ByteBuffer.allocate(8192);
        
        long now = System.currentTimeMillis();
        int count = 0;
        int lastPos = 0;
        for (Peer peer : getPeers()) {
            try {
                InetSocketAddress address = peer.getAddress();
                String host = address.getAddress().getHostName();
                writeString(peerBuffer, host);
                peerBuffer.putInt(address.getPort());
                writeString(peerBuffer, peer.getUserAgent());
                peerBuffer.putLong(now - peer.getLastContact());
                lastPos = peerBuffer.position();
                count++;
            } catch (UnknownHostException e) {
                // ignore this peer
            } catch (BufferOverflowException e) {
                // set the buffer's limit to the end of the last fully-written
                // peer
                peerBuffer.limit(lastPos);
                break;
            }
        }
        
        peerBuffer.flip();
        return formatMessage(isResponse(), count, peerBuffer);
    }
    
    static {
        Message.addParser(CODE, new MessageParser() {
            public Message parse(ByteBuffer buf, int length)
                throws InvalidMessageException
            {
                long now = System.currentTimeMillis();
                
                boolean response = (buf.get() != (byte) 0);
                int count = buf.getInt();
                List<Peer> peers = new ArrayList<Peer>(count);
                
                for (int i = 0; i < count; i++) {
                    String hostname = readString(buf);
                    int port = buf.getInt();
                    String userAgent = readString(buf);
                    long age = buf.getLong();
                    
                    peers.add(new Peer(hostname, port, userAgent, now - age));
                }
                
                return new PeerExchangeMessage(peers, response);
            }
        });
    }
}
