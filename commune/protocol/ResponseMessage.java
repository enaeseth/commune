package commune.protocol;

import java.nio.ByteBuffer;

public class ResponseMessage extends Message {
    public static final short CODE = 0x11;
    
    private int id;
    private short statusCode;
    private String statusDescription;
    private long fileLength;
    private String contentType;
    private byte[] digest;
    
    public ResponseMessage(int id, short statusCode, String statusDescription)
    {
        this(id, statusCode, statusDescription, 0L, "", null);
    }
    
    public ResponseMessage(int id, short statusCode, String statusDescription,
        long fileLength, String contentType, byte[] digest)
    {
        super(CODE);
        this.id = id;
        this.statusCode = statusCode;
        this.statusDescription = statusDescription;
        this.fileLength = fileLength;
        this.contentType = contentType;
        if (this.contentType == null)
            this.contentType = "application/octet-stream";
        this.digest = digest;
    }
    
    /**
     * Returns the client's identifier for this request.
     * @return client's identifier for this request
     */
    public int getID() {
        return id;
    }
    
    /**
     * Returns the status code.
     * @return status code
     */
    public short getStatusCode() {
        return statusCode;
    }
    
    /**
     * Returns the human-readable description of the status.
     * @return human-readable description of the status
     */
    public String getStatusDescription() {
        return statusDescription;
    }
    
    /**
     * Returns the length of the requested file.
     * @return length of the requested file
     */
    public long getFileLength() {
        return fileLength;
    }
    
    /**
     * Returns the content (MIME) type of the requested file.
     * @return content (MIME) type of the requested file
     */
    public String getContentType() {
        return contentType;
    }
    
    /**
     * Returns the message digest (hash) of the requested file.
     * @return message digest (hash) of the requested file
     */
    public byte[] getDigest() {
        return digest;
    }
    
    public ByteBuffer getBytes() {
        return formatMessage(getID(), getStatusCode(),
            getStatusDescription(), getFileLength(), getContentType(),
            (digest != null ? digest.length : 0),
            ByteBuffer.wrap(getDigest()));
    }
    
    static {
        Message.addParser(CODE, new MessageParser() {
            public Message parse(ByteBuffer buf, int length)
                throws InvalidMessageException
            {
                int clientID = buf.getInt();
                short statusCode = buf.getShort();
                String statusDescription = readString(buf);
                long fileLength = buf.getLong();
                String contentType = readString(buf);
                
                byte[] digest = null;
                if (buf.position() < buf.limit()) {
                    int digestLength = buf.getInt();
                    if (digestLength > 0) {
                        digest = new byte[digestLength];
                        buf.get(digest);
                    }
                }
                
                return new ResponseMessage(clientID, statusCode,
                    statusDescription, fileLength, contentType, digest);
            }
        });
    }
}
