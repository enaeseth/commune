package commune.protocol;

import java.nio.ByteBuffer;

public class PayloadMessage extends Message {
    private static final short CODE = 0x21;
    public static final int OVERHEAD = 12;
    
    private int transferID;
    private long offset;
    private byte[] body;
    
    public PayloadMessage(int transferID, long offset, byte[] body) {
        super(CODE);
        this.transferID = transferID;
        this.offset = offset;
        this.body = body;
    }
    
    /**
     * Returns the key that the client will use to identify payload packets.
     * @return key that the client will use to identify payload packets
     */
    public int getTransferID() {
        return transferID;
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
        return formatMessage(getTransferID(), getOffset(),
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
}
