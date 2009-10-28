package commune.protocol;

import java.nio.ByteBuffer;

/**
 * An interface that parses the contents of a buffer and returns an instance
 * of a subclass of Message.
 */
interface MessageParser {
    /**
     * Parse the body of a message found in the given byte buffer.
     */
    public Message parse(ByteBuffer buffer, int length)
        throws InvalidMessageException;
}
