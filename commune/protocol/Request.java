package commune.protocol;

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
public class Request extends Message {
    private String method;
    private String resource;
    private String protocol;
    
    private static final Pattern REQUEST_LINE_PATTERN =
        Pattern.compile("([A-Z]+) ([^ ]+) (\\w+/\\d+.\\d+)");
    
    public Request(String method) {
        this(method, "-");
    }
    
    public Request(String method, String resource) {
        this(method, resource, Message.NATIVE_PROTOCOL);
    }
    
    public Request(String method, String resource, String protocol) {
        super();
        this.method = method;
        this.resource = resource;
        this.protocol = protocol;
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
    
    public boolean equals(Object o) {
        return (o instanceof Request)
            ? equals((Request) o)
            : false;
    }
    
    public boolean equals(Request o) {
        return (getMethod().equals(o.getMethod()) &&
            getResource().equals(o.getResource()) &&
            getProtocol().equals(o.getProtocol()) &&
            super.equals(o));
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
        writeHeaders(writer);
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
        try {
            readHeaders(scanner, request);
        } catch (InvalidMessageException e) {
            throw new InvalidRequestException(e.getMessage(), e.getCause());
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
