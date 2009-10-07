package commune.protocol;

import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.io.PrintWriter;

/**
 * Base type for Commune messages that contain name-value header lines.
 */
public abstract class Message {
    private Map<String, List<String>> headers;
    
    /**
     * Initialize the message object.
     */
    protected Message() {
        headers = new HashMap<String, List<String>>();
    }
    
    /**
     * Adds a header to this request.
     */
    public void addHeader(String name, String value) {
        List<String> container = headers.get(name);
        if (container == null) {
            container = new LinkedList<String>();
            headers.put(name, container);
        }
        
        container.add(value);
    }
    
    /**
     * Gets all headers and their values.
     */
    public Map<String, List<String>> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }
    
    /**
     * Gets all values for the given header name.
     */
    public List<String> getHeader(String name) {
        return headers.get(name);
    }
    
    /**
     * Returns the first value for the header with the given name.
     * @return first value for the header with the given name
     */
    public String getFirstHeader(String name) {
        List<String> values = getHeader(name);
        return (values != null) ? values.get(0) : null;
    }
    
    public boolean equals(Object o) {
        return (o instanceof Message)
            ? equals((Message) o)
            : false;
    }
    
    public boolean equals(Message o) {
        return headers.equals(o.getHeaders());
    }
    
    protected void writeHeaders(PrintWriter writer) {
        for (Map.Entry<String, List<String>> pair : headers.entrySet()) {
            String name = pair.getKey();
            
            for (String value : pair.getValue()) {
                writer.printf("%s: %s\r\n", name, value);
            }
        }
        
        writer.print("\r\n");
    }
}
