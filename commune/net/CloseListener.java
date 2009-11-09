package commune.net;

import java.io.IOException;
import java.net.*;
import java.nio.channels.*;

/**
 * An interface for objects that want to know when channels are closed.
 */
public interface CloseListener {
    public void channelClosed(SelectableChannel channel, Object attachment);
}
