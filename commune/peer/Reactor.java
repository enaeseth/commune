package commune.peer;

import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.*;

public class Reactor {
    private Selector selector;
    private ScheduledExecutorService executor;
    
    public Reactor() throws IOException {
        selector = Selector.open();
        executor = Executors.newScheduledThreadPool(1);
    }
    
    public void register(SelectableChannel channel, int operations,
        ChannelListener listener) throws ClosedChannelException
    {
        register(channel, operations, listener, 0);
    }
    
    public void register(final SelectableChannel channel, int operations,
        ChannelListener listener, int timeout) throws ClosedChannelException
    {
        SelectionKey key = channel.keyFor(selector);
        ChannelState state;
        if (key == null) {
            if (timeout > 0) {
                state = new ChannelState(listener, new Runnable() {
                    public void run() {
                        try {
                            channel.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }, timeout, TimeUnit.SECONDS);
            } else {
                state = new ChannelState(listener);
            }
            
            channel.register(selector, operations, state);
            return;
        }
        
        key.interestOps(operations);
        
        state = (ChannelState) key.attachment();
        if (timeout > 0) {
            state.resetTimeout(timeout, TimeUnit.SECONDS);
        } else {
            state.cancelTimeout();
        }
        state.setListener(listener);
    }
    
    public boolean cancel(SelectableChannel channel) {
        SelectionKey key = channel.keyFor(selector);
        if (key == null)
            return false;
        
        ChannelState state = (ChannelState) key.attachment();
        state.cancelTimeout();
        key.cancel();
        
        return true;
    }
    
    public void run() throws IOException {
        while (!Thread.interrupted()) {
            selector.select(250);
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
        
        executor.shutdownNow();
    }
    
    private void handleKey(SelectionKey key) throws IOException {
        ChannelState state = (ChannelState) key.attachment();
        state.getListener().ready(key.channel(), key.readyOps());
    }
    
    /**
     * Holds information associated with a channel.
     */
    private class ChannelState {
        private ChannelListener listener;
        private Runnable timeoutAction;
        private ScheduledFuture<?> timeoutTask;
        
        public ChannelState(ChannelListener listener) {
            this(listener, null, 0L, TimeUnit.SECONDS);
        }
        
        public ChannelState(ChannelListener listener, Runnable timeoutAction,
            long timeout, TimeUnit unit) 
        {
            this.listener = listener;
            this.timeoutAction = timeoutAction;
            if (timeoutAction != null)
                timeoutTask = executor.schedule(timeoutAction, timeout, unit);
        }
        
        /**
         * Returns the readiness listener.
         * @return readiness listener
         */
        public ChannelListener getListener() {
            return listener;
        }
        
        /**
         * Changes the readiness listener.
         */
        public void setListener(ChannelListener listener) {
            this.listener = listener;
        }
        
        public void resetTimeout(long timeout, TimeUnit unit) {
            if (timeoutAction == null) {
                throw new IllegalStateException("This channel never had a " +
                    "timeout.");
            }
            
            if (timeoutTask.cancel(false)) {
                timeoutTask = executor.schedule(timeoutAction, timeout, unit);
            }
        }
        
        public void cancelTimeout() {
            if (timeoutTask != null) {
                timeoutTask.cancel(false);
                timeoutTask = null;
            }
        }
    }
}
