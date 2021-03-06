package commune.peer;

import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;
import java.util.regex.*;

/**
 * A Commune peer.
 */
public class Peer implements Comparable<Peer> {
    private static final Pattern ATTRIBUTE_SUFFIX =
        Pattern.compile("\\s+\\((.+)\\)$");
    private static final Pattern ATTRIBUTE_SEPARATOR =
        Pattern.compile(";\\s+");
    
    private long id;
    private String hostname;
    private int port;
    private String userAgent;
    private long lastContact;
    private Set<String> attributes;
    
    public Peer(long id, String hostname, int port, String userAgent) {
        this(id, hostname, port, userAgent, System.currentTimeMillis());
    }
    
    public Peer(long id, String hostname, int port, String userAgent,
        long lastContact)
    {
        this.id = id;
        this.hostname = hostname.toLowerCase();
        this.port = port;
        this.userAgent = userAgent;
        this.lastContact = lastContact;
        
        attributes = new HashSet<String>();
        if (userAgent != null)
            parseUserAgent();
    }
    
    public static Peer fromAddress(InetSocketAddress address) {
        return fromAddress(address.getAddress(), address.getPort(), 0, null);
    }
    
    public static Peer fromAddress(InetAddress address, int port, long id,
        String userAgent)
    {
        if (address == null)
            return null;
        String hostname = address.getCanonicalHostName();
        
        return new Peer(id, hostname, port, userAgent);
    }
    
    private Set<String> parseUserAgent() {
        Matcher matcher = ATTRIBUTE_SUFFIX.matcher(userAgent);
        if (matcher.find()) {
            String attributeString = matcher.group(1);
            for (String attribute : ATTRIBUTE_SEPARATOR.split(attributeString))
                attributes.add(attribute);
        }
        
        return attributes;
    }
    
    /**
     * Returns the globally unique ID of this peer.
     * @return globally unique ID of this peer
     */
    public long getID() {
        return id;
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
    
    public List<InetSocketAddress> getAddresses() {
        List<InetSocketAddress> saddrs = new LinkedList<InetSocketAddress>();
        try {
            InetAddress[] addresses = InetAddress.getAllByName(hostname);
            
            for (InetAddress possible : addresses) {
                saddrs.add(new InetSocketAddress(possible, getPort()));
            }
        } catch (UnknownHostException e) {
            // ignore
        }
        
        return saddrs;
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
     * Updates the last-contact time to be the current time.
     */
    public void touch() {
        lastContact = System.currentTimeMillis();
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
        long otherID = other.getID();
        if (id != 0 && otherID != 0)
            return id == otherID;
        return sameAddress(other);
    }
    
    public boolean sameAddress(Peer other) {
        return hostname.equals(other.getHost()) && port == other.getPort();
    }
    
    public int compareTo(Peer other) {
        return (int) (other.getLastContact() - lastContact);
    }
    
    public int hashCode() {
        try {
            return getAddress().hashCode();
        } catch (UnknownHostException e) {
            return super.hashCode();
        }
    }
    
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("<");
        if (id != 0)
            builder.append(String.format("%016x; ", id));
        builder.append(String.format("%s:%d", hostname, port));
        if (userAgent != null)
            builder.append(String.format("; %s", userAgent));
        builder.append(">");
        return builder.toString();
    }
}
