package commune.protocol;

import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.MatchResult;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

public class Response extends Message {
    private String protocol;
    private int statusCode;
    private String statusText;
    
    private static final Pattern RESPONSE_LINE_PATTERN =
        Pattern.compile("(\\w+/\\d+.\\d+) ([0-9]+) (.+)");
    
    public Response(int statusCode, String statusText) {
        this(Message.NATIVE_PROTOCOL, statusCode, statusText);
    }
    
    public Response(String protocol, int statusCode, String statusText) {
        this.protocol = protocol;
        this.statusCode = statusCode;
        this.statusText = statusText;
    }
    
    /**
     * Returns the numeric status code of the response.
     * @return numeric status code of the response
     */
    public int getStatusCode() {
        return statusCode;
    }
    
    /**
     * Returns the textual description of the response status.
     * @return textual description of the response status
     */
    public String getStatusText() {
        return statusText;
    }
    
    /**
     * Returns the protocol that the server responded with.
     * @return protocol that the server responded with
     */
    public String getProtocol() {
        return protocol;
    }
    
    public boolean equals(Object o) {
        return (o instanceof Response)
            ? equals((Response) o)
            : false;
    }
    
    public boolean equals(Response o) {
        return (getStatusCode() == o.getStatusCode() &&
            getProtocol().equals(o.getProtocol()) &&
            super.equals(o));
    }
    
    /**
     * Returns this response as a string suitable for sending over the network.
     */
    public String toString() {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer, true);
        
        writer.printf("%s %d %s\r\n", getProtocol(), getStatusCode(),
            getStatusText());
        writeHeaders(writer);
        return buffer.toString();
    }
    
    /**
     * Parses a response.
     */
    public static Response parse(byte[] response)
        throws InvalidResponseException, UnsupportedEncodingException
    {
        return parse(response, 0, response.length);
    }
    
    /**
     * Parses a response.
     */
    public static Response parse(byte[] response, int offset, int length)
        throws InvalidResponseException, UnsupportedEncodingException
    {
        return parse(new String(response, offset, length, "UTF-8"));
    }
    
    /**
     * Parses a response string.
     */
    public static Response parse(String responseString)
        throws InvalidResponseException
    {
        Scanner scanner = new Scanner(responseString);
        
        if (scanner.findInLine(RESPONSE_LINE_PATTERN) == null) {
            throw new InvalidResponseException("Invalid response line: " +
                scanner.nextLine());
        }
        
        MatchResult match = scanner.match();
        Response response = new Response(match.group(1),
            Integer.parseInt(match.group(2)), match.group(3));
        
        scanner.nextLine();
        try {
            readHeaders(scanner, response);
        } catch (InvalidMessageException e) {
            throw new InvalidResponseException(e.getMessage(), e.getCause());
        }
        
        return response;
    }
    
    public static void main(String... args) throws InvalidResponseException {
        Response r = new Response(200, "OK");
        r.addHeader("Server", "Commons Reference/0.1");
        r.addHeader("Connection", "close");
        r.addHeader("Content-Hash", "algorithm=\"SHA-1\"; digest=\"...\"");
        r.addHeader("Content-Type", "text/plain");
        
        System.out.print(Response.parse(r.toString()).toString());
    }
}
