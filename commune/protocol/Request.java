package commune.protocol;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Represents a request made of a Commune servent.
 */
public class Request {
    private String method;
    private String resource;
    private String protocol;
    private Map<String, List<String>> headers;
    
    public Request(String method) {
        this(method, "-");
    }
    
    public Request(String method, String resource) {
        this(method, resource, "Commune/0.1");
    }
    
    public Request(String method, String resource, String protocol) {
        this.method = method;
        this.resource = resource;
        this.protocol = protocol;
        headers = new HashMap<String, List<String>>();
    }
    
    /**
     * Returns the method that this request uses.
     * @return method that this request uses
     */
    public String getMethod() {
        return method;
    }
    
    /**
     * Returns the name of the resource that this request tries to act upon.
     * @return name of the resource that this request tries to act upon
     */
    public String getResource() {
        return resource;
    }
    
    /**
     * Returns the protocol identifier used by this request.
     * @return protocol identifier used by this request
     */
    public String getProtocol() {
        return protocol;
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
     * Serializes this request as a string.
     */
    public String toString() {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer, true);
        
        writer.printf("%s %s %s\r\n", getMethod(), getResource(),
            getProtocol());
        
        for (Map.Entry<String, List<String>> pair : headers.entrySet()) {
            String name = pair.getKey();
            
            for (String value : pair.getValue()) {
                writer.printf("%s: %s\r\n", name, value);
            }
        }
        
        writer.print("\r\n");
        return buffer.toString();
    }
    
    public static void main(String... args) {
        Request r = new Request("GET", "/some/file");
        r.addHeader("User-Agent", "Commune Reference/0.1");
        r.addHeader("Connection", "close");
        System.out.print(r);
    }
}
