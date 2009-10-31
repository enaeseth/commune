package commune.protocol;

import java.nio.ByteBuffer;

public class RequestMessage extends Message {
    public static final short CODE = 0x10;
    
    private int id;
    private String path;
    
    public RequestMessage(int id, String path) {
        super(CODE);
        this.id = id;
        this.path = path;
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
    
    public ByteBuffer getBytes() {
        return formatMessage(getID(), getPath());
    }
    
    static {
        Message.addParser(CODE, new MessageParser() {
            public Message parse(ByteBuffer buf, int length)
                throws InvalidMessageException
            {
                int id = buf.getInt();
                String path = readString(buf);
                
                return new RequestMessage(id, path);
            }
        });
    }
}
