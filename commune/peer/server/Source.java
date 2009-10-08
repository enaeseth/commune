package commune.peer.server;

/**
 * An interface for objects that can provide a resource to serve given a path.
 */
public interface Source {
    /**
     * Gets the resource available at the given path.
     * @param path the path to the resource being requested
     * @return the requested resource (if it exists), otherwise null
     */
    public AvailableResource getResource(String path);
}
