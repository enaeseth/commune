package commune.peer;

import java.io.IOException;
import java.nio.channels.SelectableChannel;

/**
 * An interface that objects who want to be informed when certain calls on
 * a channel will not block.
 */
public interface ChannelListener {
    public void ready(SelectableChannel channel, int operations)
        throws IOException;
}
