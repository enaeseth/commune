package commune.source;

import java.io.File;

/**
 * A source for servable resources that looks in a directory.
 */
public class DirectorySource implements Source {
    private String prefix;
    private File directory;
    
    /**
     * Creates a new source for the given directory.
     * @param directory the directory to serve via this source
     * @throws IllegalArgumentException if the given "directory" does not exist
     *         or is not really a directory at all
     */
    public DirectorySource(String prefix, File directory) {
        if (!directory.exists()) {
            throw new IllegalArgumentException("The given path (" + directory +
                ") does not exist.");
        } else if (!directory.isDirectory()) {
            throw new IllegalArgumentException("The given path (" + directory +
                ") does not refer to a directory.");
        }
        
        this.prefix = prefix;
        this.directory = directory.getAbsoluteFile();
    }
    
    public AvailableResource getResource(String path) {
        if (!path.startsWith(prefix))
            return null;
        path = path.substring(prefix.length());
        
        File requested = new File(directory, path);
        
        if (!requested.exists() || !requested.isFile() || !requested.canRead())
            return null;
        
        return new AvailableFile(requested);
    }
}
