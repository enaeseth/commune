package commune.source;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.io.IOException;
import java.net.FileNameMap;
import java.net.URLConnection;

/**
 * Represents a concrete file that is available to be served to clients.
 */
public class AvailableFile implements AvailableResource {
    private File file;
    private static FileNameMap filenameMap = URLConnection.getFileNameMap();
    
    /**
     * Creates a new AvailableFile object from the given file.
     * @param file the file being made available
     * @throws IllegalArgumentException if the given file does not exist,
     *         if it is not a file, or cannot be read by this application
     */
    public AvailableFile(File file) {
        if (!file.exists()) {
            throw new IllegalArgumentException("The given file (" + file +
                ") does not exist.");
        } else if (!file.isFile()) {
            throw new IllegalArgumentException("The given path (" + file +
                ") is not a file.");
        } else if (!file.canRead()) {
            throw new IllegalArgumentException("The given file (" + file +
                ") may not be read by this application.");
        }
        
        this.file = file;
    }
    
    /**
     * Returns the file that this object describes.
     * @return file that this object describes
     */
    public File getFile() {
        return file;
    }
    
    /**
     * Returns the size of the file.
     * @return size of the file
     */
    public long getSize() {
        return file.length();
    }
    
    /**
     * Returns the content type of the file.
     * @return content type of the file
     */
    public String getContentType() {
        return filenameMap.getContentTypeFor(file.getName());
    }
    
    /**
     * Opens an output stream through which the file can be read.
     */
    public MappedByteBuffer read() throws IOException {
        FileInputStream stream = new FileInputStream(file);
        FileChannel channel = stream.getChannel();
        
        return channel.map(FileChannel.MapMode.READ_ONLY, 0L, getSize());
    }
}
