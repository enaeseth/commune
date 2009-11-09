package commune.protocol;

import java.nio.ByteBuffer;

public class PayloadMessage extends Message {
    public static final short CODE = 0x12;
    public static final int OVERHEAD = 12;
    
    private int requestID;
    private long offset;
    private byte[] body;
    
    public PayloadMessage(int requestID, long offset, byte[] body) {
        super(CODE);
        this.requestID = requestID;
        this.offset = offset;
        this.body = body;
    }
    
    /**
     * Returns the ID of the request that generated this payload message.
     * @return ID of the request that generated this payload message
     */
    public int getRequestID() {
        return requestID;
    }
    
    /**
     * Returns the offset within the file of the payload's body.
     * @return offset within the file of the payload's body
     */
    public long getOffset() {
        return offset;
    }
    
    /**
     * Returns the body of the payload.
     * @return body of the payload
     */
    public byte[] getBody() {
        return body;
    }
    
    public ByteBuffer getBytes() {
        return formatMessage(getRequestID(), getOffset(),
            ByteBuffer.wrap(body));
    }
    
    static {
        Message.addParser(CODE, new MessageParser() {
            public Message parse(ByteBuffer buf, int length)
                throws InvalidMessageException
            {
                int transferID = buf.getInt();
                long offset = buf.getLong();
                
                int dataLength = length - HEADER_LENGTH -
                    PayloadMessage.OVERHEAD;
                byte[] body = new byte[dataLength];
                buf.get(body);
                
                return new PayloadMessage(transferID, offset, body);
            }
        });
    }
    
    public String toString() {
        StringBuilder builder = new StringBuilder();
        
        builder.append(String.format("[payload id=%d, offset=%d: {",
            getRequestID(), getOffset()));
        
        boolean first = true;
        for (byte b : getBody()) {
            if (first)
                first = false;
            else
                builder.append(", ");
            builder.append(String.format("%02X", b));
        }
        builder.append("}]");
        
        return builder.toString();
    }
}
