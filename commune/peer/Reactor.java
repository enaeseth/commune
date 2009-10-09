package commune.peer;

import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.Iterator;

public class Reactor {
    private Selector selector;
    
    public Reactor() throws IOException {
        selector = Selector.open();
    }
    
    public void register(SelectableChannel channel, int operations,
        ChannelListener listener) throws ClosedChannelException
    {
        SelectionKey key = channel.keyFor(selector);
        if (key == null) {
            channel.register(selector, operations, listener);
            return;
        }
        
        key.interestOps(operations);
        key.attach(listener);
    }
    
    public boolean cancel(SelectableChannel channel) {
        SelectionKey key = channel.keyFor(selector);
        if (key == null)
            return false;
        key.cancel();
        return true;
    }
    
    public void run() throws IOException {
        while (true) {
            selector.select();
            
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                
                try {
                    handleKey(key);
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                    key.cancel();
                    key.channel().close();
                }
            }
        }
    }
    
    private void handleKey(SelectionKey key) throws IOException {
        ChannelListener listener = (ChannelListener) key.attachment();
        listener.ready(key.channel(), key.readyOps());
    }
}
