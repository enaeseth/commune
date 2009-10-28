package commune.protocol;

import java.nio.ByteBuffer;

public class AcknowledgementMessage extends Message {
    private static final short CODE = 0x22;
    
    private int transferID;
    private long offset;
    
    public AcknowledgementMessage(int transferID, long offset)
    {
        super(CODE);
        this.transferID = transferID;
        this.offset = offset;
    }
    
    /**
     * Returns the ID of the transfer that this message acknowledges.
     * @return ID of the transfer that this message acknowledges
     */
    public int getTransferID() {
        return transferID;
    }
    
    /**
     * Returns the offset of the payload being acknowledged.
     * @return offset of the payload being acknowledged
     */
    public long getOffset() {
        return offset;
    }
    
    public ByteBuffer getBytes() {
        return formatMessage(getTransferID(), getOffset());
    }
    
    static {
        Message.addParser(CODE, new MessageParser() {
            public Message parse(ByteBuffer buf, int length)
                throws InvalidMessageException
            {
                int transferID = buf.getInt();
                long offset = buf.getLong();
                
                return new AcknowledgementMessage(transferID, offset);
            }
        });
    }
}
