package commune.peer.server;

import java.util.List;
import java.util.LinkedList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Serves requested resources asynchronously over pipe channels.
 *
 * A ResourceServer can serve files/resources from any number of
 * {@link Source}s.
 */
public class ResourceServer {
    private List<Source> sources;
    private ReadWriteLock sourceLock;
    private Lock sourceReadLock;
    private Lock sourceWriteLock;
    
    /**
     * Creates a new resource server.
     */
    public ResourceServer() {
        sources = new LinkedList<Source>();
        sourceLock = new ReentrantReadWriteLock();
        sourceReadLock = sourceLock.readLock();
        sourceWriteLock = sourceLock.writeLock();
    }
    
    /**
     * Adds a new source to the server.
     * @param source the source to add
     */
    public void addSource(Source source) {
        sourceWriteLock.lock();
        try {
            sources.add(source);
        } finally {
            sourceWriteLock.unlock();
        }
    }
    
    /**
     * Gets the resource available at the given path, if any.
     * @param path the path of the desired resource
     * @return the available resource, or null if no resource is available at
     *         the given path
     */
    public AvailableResource getResource(String path) {
        sourceReadLock.lock();
        try {
            AvailableResource resource;
            for (Source source : sources) {
                resource = source.getResource(path);
                if (resource != null)
                    return resource;
            }
            return null;
        } finally {
            sourceReadLock.unlock();
        }
    }
}
