package commune.peer;

import commune.protocol.Message;

/**
 * A message source: an object that provides a sequence of messages to a
 * broker.
 */
public interface MessageSource {
    /**
     * Gets the next message from the source. If no more messages are
     * available, returns null.
     */
    public Message next();
}
