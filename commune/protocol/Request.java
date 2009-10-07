package commune.protocol;

import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.MatchResult;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Represents a request made of a Commune servent.
 */
public class Request {
    private String method;
    private String resource;
    private String protocol;
    private Map<String, List<String>> headers;
    
    private static final Pattern REQUEST_LINE_PATTERN =
        Pattern.compile("([A-Z]+) ([^ ]+) (\\w+/\\d+.\\d+)");
    
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
        return (o instanceof Request)
            ? equals((Request) o)
            : false;
    }
    
    public boolean equals(Request o) {
        return (getMethod().equals(o.getMethod()) &&
            getResource().equals(o.getResource()) &&
            getProtocol().equals(o.getProtocol()) &&
            headers.equals(o.getHeaders()));
    }
    
    /**
     * Serializes this request as a string.
     */
    public String toString() {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer, true);
        String encodedResource;
        
        try {
            URI resource = new URI(null, null, null, 0, getResource(), null,
                null);
            encodedResource = resource.getRawPath();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        
        writer.printf("%s %s %s\r\n", getMethod(), encodedResource,
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
    
    /**
     * Parses a request.
     */
    public static Request parse(byte[] request)
        throws InvalidRequestException, UnsupportedEncodingException
    {
        return parse(request, 0, request.length);
    }
    
    /**
     * Parses a request.
     */
    public static Request parse(byte[] request, int offset, int length)
        throws InvalidRequestException, UnsupportedEncodingException
    {
        return parse(new String(request, offset, length, "UTF-8"));
    }
    
    /**
     * Parses a request string.
     */
    public static Request parse(String requestString)
        throws InvalidRequestException
    {
        Scanner scanner = new Scanner(requestString);
        
        if (scanner.findInLine(REQUEST_LINE_PATTERN) == null) {
            throw new InvalidRequestException("Invalid request line: " +
                scanner.nextLine());
        }
        
        MatchResult match = scanner.match();
        Request request;
        try {
            URI uri = new URI(match.group(2));
            
            if (uri.getScheme() != null || uri.getAuthority() != null) {
                throw new InvalidRequestException("Must only give a path " +
                    "in the request URI.");
            }
            
            request = new Request(match.group(1), uri.getPath(),
                match.group(3));
        } catch (URISyntaxException e) {
            throw new InvalidRequestException("Invalid request URI: " +
                match.group(2), e);
        }
        
        scanner.nextLine();
        
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.length() == 0) {
                break;
            }
            
            String[] parts = line.split(": ", 2);
            if (parts.length != 2) {
                throw new InvalidRequestException("Invalid header line: " +
                    line);
            }
            
            request.addHeader(parts[0], parts[1]);
        }
        
        return request;
    }
    
    public static void main(String... args) throws Exception {
        Request r = new Request("GET", "/some/file");
        r.addHeader("User-Agent", "Commune Reference/0.1");
        r.addHeader("Connection", "close");
        System.out.println(r.equals(parse(r.toString())));
    }
}
