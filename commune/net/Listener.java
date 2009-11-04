package commune.net;

import java.io.IOException;
import java.nio.channels.SelectableChannel;

public interface Listener {
    public static final Listener IGNORE = new Listener() {
        public void ready(SelectableChannel channel) throws IOException {};
    };
    
    public void ready(SelectableChannel channel) throws IOException;
}
