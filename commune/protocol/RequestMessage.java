package commune.protocol;

import java.nio.ByteBuffer;

public class RequestMessage extends Message {
    public static final short CODE = 0x10;
    
    private int id;
    private String path;
    private boolean hypothetical;
    
    public RequestMessage(int id, String path) {
        this(id, path, false);
    }
    
    public RequestMessage(int id, String path, boolean hypothetical) {
        super(CODE);
        this.id = id;
        this.path = path;
        this.hypothetical = hypothetical;
    }
    
    /**
     * Returns the client's identifier for this request.
     * @return client's identifier for this request
     */
    public int getID() {
        return id;
    }
    
    /**
     * Returns the path of the resource that this message is requesting.
     * @return path of the resource that this message is requesting
     */
    public String getPath() {
        return path;
    }
    
    /**
     * Returns true if this request is hypothetical; false if otherwise.
     * @return true if this request is hypothetical; false if otherwise
     */
    public boolean isHypothetical() {
        return hypothetical;
    }
    
    public ByteBuffer getBytes() {
        return formatMessage(getID(), getPath(), isHypothetical());
    }
    
    static {
        Message.addParser(CODE, new MessageParser() {
            public Message parse(ByteBuffer buf, int length)
                throws InvalidMessageException
            {
                int id = buf.getInt();
                String path = readString(buf);
                // backwards-compatible, because why not?
                boolean hasHypotheticalField =
                    (length > HEADER_LENGTH + 4 + path.length() + 2);
                boolean hypothetical = (hasHypotheticalField)
                    ? (buf.get() != (byte) 0)
                    : false;
                
                return new RequestMessage(id, path, hypothetical);
            }
        });
    }
}
