package commune.peer;

import java.util.Arrays;

/**
 * Represents a resource available from a peer.
 */
public class Resource {
    private String path;
    private long length;
    private String contentType;
    private byte[] digest;
    
    public Resource(String path, long length, String contentType,
        byte[] digest)
    {
        this.path = path;
        this.length = length;
        this.contentType = contentType;
        this.digest = digest;
    }
    
    /**
     * Returns the server path of the resource.
     * @return server path of the resource
     */
    public String getPath() {
        return path;
    }
    
    /**
     * Returns the length of the resource (in bytes).
     * @return length of the resource (in bytes)
     */
    public long getLength() {
        return length;
    }
    
    /**
     * Returns the MIME content type of the resource.
     * @return MIME content type of the resource
     */
    public String getContentType() {
        return contentType;
    }
    
    /**
     * Returns the message digest (hash) of the resource.
     * @return message digest (hash) of the resource
     */
    public byte[] getDigest() {
        return digest;
    }
    
    public boolean equals(Object other) {
        return (other instanceof Resource)
            ? equals((Resource) other)
            : false;
    }
    
    public boolean equals(Resource other) {
        if (length != other.getLength())
            return false;
        
        byte[] otherDigest = other.getDigest();
        if (digest == null || otherDigest == null)
            return false;
        if (digest.length != otherDigest.length)
            return false;
        return Arrays.equals(digest, otherDigest);
    }
}
