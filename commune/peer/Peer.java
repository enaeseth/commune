package commune.peer;

import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;
import java.util.regex.*;

/**
 * A Commune peer.
 */
public class Peer {
    private static final Pattern ATTRIBUTE_SUFFIX =
        Pattern.compile("\\s+\\((.+)\\)$");
    private static final Pattern ATTRIBUTE_SEPARATOR =
        Pattern.compile(";\\s+");
    
    private String hostname;
    private int port;
    private String userAgent;
    private long lastContact;
    private Set<String> attributes;
    
    public Peer(String hostname, int port, String userAgent) {
        this(hostname, port, userAgent, System.currentTimeMillis());
    }
    
    public Peer(String hostname, int port, String userAgent, long lastContact)
    {
        this.hostname = hostname;
        this.port = port;
        this.userAgent = userAgent;
        this.lastContact = lastContact;
        
        attributes = parseUserAgent();
    }
    
    private Set<String> parseUserAgent() {
        attributes = new HashSet<String>();
        
        Matcher matcher = ATTRIBUTE_SUFFIX.matcher(userAgent);
        if (matcher.find()) {
            String attributeString = matcher.group(1);
            for (String attribute : ATTRIBUTE_SEPARATOR.split(attributeString))
                attributes.add(attribute);
        }
        
        return attributes;
    }
    
    /**
     * Returns the host at which this peer resides.
     * @return host at which this peer resides
     */
    public String getHost() {
        return hostname;
    }
    
    /**
     * Returns the port at which this peer resides.
     * @return port at which this peer resides
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Returns a socket address that may be used to connect to the peer.
     * @return a socket address that may be used to connect to the peer.
     * @throws UnknownHostException if no such address can be determined
     */
    public InetSocketAddress getAddress() throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(hostname);
        InetAddress blessed = null;
        
        for (InetAddress address : addresses) {
            if (address instanceof Inet6Address) {
                blessed = address;
                break;
            }
        }
        
        if (blessed == null)
            blessed = addresses[new Random().nextInt(addresses.length)];
        
        return new InetSocketAddress(blessed, port);
    }
    
    /**
     * Returns the user agent string for this peer.
     * @return user agent string for this peer
     */
    public String getUserAgent() {
        return userAgent;
    }
    
    /**
     * Returns the system time (in milliseconds) that this peer was last heard
     * from.
     * @return system time (in milliseconds) that this peer was last heard
     *         from
     */
    public long getLastContact() {
        return lastContact;
    }
    
    /**
     * Returns the user agent's reported attribute set.
     * @return user agent's reported attribute set
     */
    public Set<String> getAttributes() {
        return Collections.unmodifiableSet(attributes);
    }
    
    /**
     * Returns true if the peer reported support for peer exchange; false if
     * otherwise.
     * @return true if the peer reported support for peer exchange; false if
     *              otherwise
     */
    public boolean exchangesPeers() {
        return attributes.contains("PEX");
    }
    
    public boolean equals(Object other) {
        return (other instanceof Peer) ? equals((Peer) other) : false;
    }
    
    public boolean equals(Peer other) {
        return hostname.equals(other.getHost()) && port == other.getPort();
    }
    
    public int hashCode() {
        try {
            return getAddress().hashCode();
        } catch (UnknownHostException e) {
            return super.hashCode();
        }
    }
    
    public String toString() {
        return String.format("<%s:%d; %s>", hostname, port, userAgent);
    }
}
