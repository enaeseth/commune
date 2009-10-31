package commune.protocol;

import java.nio.ByteBuffer;

public class HelloMessage extends Message {
    public static final short CODE = 0x01;
    
    private String agent;
    private boolean acknowledgement;
    
    public HelloMessage(String agent, boolean acknowledgement) {
        super(CODE);
        this.agent = agent;
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
     * Returns true if this message was an acknowledgement of a previous hello,
     * false if otherwise.
     * @return true if this message was an acknowledgement of a previous hello,
     *         false if otherwise
     */
    public boolean isAcknowledgement() {
        return acknowledgement;
    }
    
    public ByteBuffer getBytes() {
        return formatMessage(isAcknowledgement(), getUserAgent());
    }
    
    static {
        Message.addParser(CODE, new MessageParser() {
            public Message parse(ByteBuffer buf, int length)
                throws InvalidMessageException
            {
                boolean acknowledgement = (buf.get() != (byte) 0);
                String agent = readString(buf);
                
                return new HelloMessage(agent, acknowledgement);
            }
        });
    }
    
    public static void main(String... args) {
        System.out.println(new HelloMessage("Reference/0.2", false).getBytes());
    }
}
