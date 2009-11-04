package commune.net;

import java.io.IOException;
import java.nio.channels.SelectableChannel;

public interface TimeoutTask {
    public static final TimeoutTask CLOSE = new TimeoutTask() {
        public void timedOut(SelectableChannel channel) throws IOException {
            channel.close();
        }
    };
    
    public void timedOut(SelectableChannel channel) throws IOException;
}
