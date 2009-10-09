package commune.peer.source;

import java.nio.ByteBuffer;
import java.io.IOException;

/**
 * Gives information about a resource that is available to be served to
 * clients.
 */
public interface AvailableResource {
    public long getSize();
    public String getContentType();
    public ByteBuffer read() throws IOException;
}
