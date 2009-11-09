package commune.protocol;

import java.nio.ByteBuffer;

public class HelloMessage extends Message {
    public static final short CODE = 0x01;
    
    private String agent;
    private boolean acknowledgement;
    private long peerID;
    private int listeningPort;
    
    public HelloMessage(String agent, long peerID, int listeningPort,
        boolean acknowledgement)
    {
        super(CODE);
        this.agent = agent;
        this.peerID = peerID;
        this.listeningPort = listeningPort;
        this.acknowledgement = acknowledgement;
    }
    
    /**
     * Returns the user-agent name of the peer who sent the hello.
     * @return user-agent name of the peer who sent the hello
     */
    public String getUserAgent() {
        return agent;
    }
    
    /**
     * Returns the unique ID of this peer.
     * @return unique ID of this peer
     */
    public long getPeerID() {
        return peerID;
    }
    
    /**
     * Returns the port on which this peer is listening.
     * @return port on which this peer is listening
     */
    public int getListeningPort() {
        return listeningPort;
    }
    
    /**
     * Returns true if this message was an acknowledgement of a previous hello,
     * false if otherwise.
     * @return true if this message was an acknowledgement of a previous hello,
     *         false if otherwise
     */
    public boolean isAcknowledgement() {
        return acknowledgement;
    }
    
    public ByteBuffer getBytes() {
        return formatMessage(isAcknowledgement(), getPeerID(),
            getListeningPort(), getUserAgent());
    }
    
    static {
        Message.addParser(CODE, new MessageParser() {
            public Message parse(ByteBuffer buf, int length)
                throws InvalidMessageException
            {
                boolean acknowledgement = (buf.get() != (byte) 0);
                long peerID = buf.getLong();
                int listeningPort = buf.getInt();
                String agent = readString(buf);
                
                return new HelloMessage(agent, peerID, listeningPort,
                    acknowledgement);
            }
        });
    }
}
