package commune.protocol;

import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.io.UnsupportedEncodingException;

/**
 * Base type for Commune messages.
 * Contains facilities for messages to be parsed and serialized.
 */
public abstract class Message {
    public static final int HEADER_LENGTH = 8;
    
    private static Map<Short, MessageParser> types;
    private short type;
    
    static {
        types = new HashMap<Short, MessageParser>();
    }
    
    /**
     * Initialize the message object.
     */
    protected Message(short type) {
        this.type = type;
    }
    
    /**
     * Returns the type code of this message.
     * @return type code of this message
     */
    public short getType() {
        return type;
    }
    
    public boolean equals(Object o) {
        return (o instanceof Message)
            ? equals((Message) o)
            : false;
    }
    
    public boolean equals(Message o) {
        return o.getType() == getType(); // XXX
    }
    
    /**
     * Parse the message found in the given byte buffer.
     * @return an instance of a subclass of Message representing the message
     * @throws InvalidMessageException if the message type is unknown or if
     *         the message format is invalid
     */
    public static Message parseMessage(ByteBuffer buf)
        throws InvalidMessageException
    {
        int length = buf.getInt();
        short typeCode = buf.getShort();
        short checksum = buf.getShort();
        
        MessageParser parser = types.get(typeCode);
        if (parser == null) {
            throw new InvalidMessageException(String.format("Unknown " +
                "message type code %d.", typeCode));
        }
        
        return parser.parse(buf, length);
    }
    
    public abstract ByteBuffer getBytes();
    
    protected void writeHeader(ByteBuffer destination, int length) {
        destination.putInt(length);
        destination.putShort(getType());
        destination.putShort((short) 0); // reserved space
    }
    
    protected ByteBuffer formatMessage(Object... parts) {
        int length = HEADER_LENGTH;
        LinkedList<Object> strings = null;
        
        // Calculate the length of the packet.
        for (Object part : parts) {
            if (part instanceof String) {
                byte[] bytes = encodeString((String) part);
                length += (2 + bytes.length);
                
                if (strings == null)
                    strings = new LinkedList<Object>();
                strings.add((Object) bytes);
            } else if (part instanceof Long) {
                length += 8;
            } else if (part instanceof Integer) {
                length += 4;
            } else if (part instanceof Short) {
                length += 2;
            } else if (part instanceof Byte || part instanceof Boolean) {
                length += 1;
            } else if (part instanceof ByteBuffer) {
                length += ((ByteBuffer) part).limit();
            } else {
                throw new IllegalArgumentException("Message part " + part +
                    " cannot be formatted.");
            }
        }
        
        // Create and fill the packet's buffer.
        ByteBuffer buf = ByteBuffer.allocate(length);
        writeHeader(buf, length);
        
        for (Object part : parts) {
            if (part instanceof String) {
                writeString(buf, (byte[]) strings.removeFirst());
            } else if (part instanceof Long) {
                buf.putLong((Long) part);
            } else if (part instanceof Integer) {
                buf.putInt((Integer) part);
            } else if (part instanceof Short) {
                buf.putShort((Short) part);
            } else if (part instanceof Byte) {
                buf.put((Byte) part);
            } else if (part instanceof Boolean) {
                buf.put((byte) (((Boolean) part).booleanValue() ? 1 : 0));
            } else if (part instanceof ByteBuffer) {
                buf.put((ByteBuffer) part);
            }
        }
        
        buf.flip();
        return buf;
    }
    
    static void addParser(short typeCode, MessageParser parser) {
        types.put(typeCode, parser);
    }
    
    static String readString(ByteBuffer source)
        throws InvalidMessageException
    {
        int length = source.getShort();
        byte[] data = new byte[length];
        
        try {
            source.get(data);
            return new String(data, "UTF-8");
        } catch (BufferUnderflowException e) {
            String message = String.format(
                "Buffer underflow while reading a %d-byte string.", length);
            throw new InvalidMessageException(message, e);
        } catch (UnsupportedEncodingException e) {
            // All JVM implementations must support UTF-8; this code should
            // be unreachable.
            throw new RuntimeException(e);
        }
    }
    
    static byte[] encodeString(String string) {
        try {
            byte[] bytes = string.getBytes("UTF-8");
            if (bytes.length > Short.MAX_VALUE)
                throw new RuntimeException("String is too long.");
            return bytes;
        } catch (UnsupportedEncodingException e) {
            // All JVM implementations must support UTF-8; this code should
            // be unreachable.
            throw new RuntimeException(e);
        }
    }
    
    static int writeString(ByteBuffer destination, String string) {
        return writeString(destination, encodeString(string));
    }
    
    static int writeString(ByteBuffer destination, byte[] bytes) {
        destination.putShort((short) bytes.length);
        destination.put(bytes);
        return bytes.length;
    }
}
