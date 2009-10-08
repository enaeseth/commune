package commune.peer.source;

import java.io.InputStream;
import java.io.IOException;

/**
 * Gives information about a resource that is available to be served to
 * clients.
 */
public interface AvailableResource {
    public long getSize();
    public String getContentType();
    public InputStream openStream() throws IOException;
}
