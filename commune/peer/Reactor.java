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
        register(channel, operations, listener, timeout, new Runnable() {
            public void run() {
                try {
                    channel.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        });
    }
    
    public void register(SelectableChannel channel, int operations,
        ChannelListener listener, int timeout, Runnable timeoutAction)
        throws ClosedChannelException
    {
        /*
        System.out.print("Registering ");
        if (channel instanceof ServerSocketChannel) {
            System.out.printf("server socket %s\t",
                ((ServerSocketChannel) channel).socket().getLocalSocketAddress());
        } else if (channel instanceof SocketChannel) {
            System.out.printf("socket to %s\t",
                ((SocketChannel) channel).socket().getRemoteSocketAddress());
        } else if (channel instanceof DatagramChannel) {
            System.out.printf("datagram socket %s\t",
                ((DatagramChannel) channel).socket().getLocalSocketAddress());
        } else {
            System.out.printf("%s\t", channel);
        }
        
        if ((operations & SelectionKey.OP_ACCEPT) != 0) {
            System.out.print("ACCEPT ");
        }
        if ((operations & SelectionKey.OP_READ) != 0) {
            System.out.print("READ ");
        }
        if ((operations & SelectionKey.OP_WRITE) != 0) {
            System.out.print("WRITE ");
        }
        System.out.println();
        */
        
        SelectionKey key = channel.keyFor(selector);
        ChannelState state;
        if (key == null) {
            if (timeout > 0) {
                state = new ChannelState(listener, timeoutAction, timeout,
                    TimeUnit.SECONDS);
            } else {
                state = new ChannelState(listener);
            }
            
            channel.register(selector, operations, state);
            return;
        }
        
        key.interestOps(operations);
        
        synchronized (this) {
            state = (ChannelState) key.attachment();
            if (timeout > 0) {
                state.resetTimeout(timeoutAction, timeout, TimeUnit.SECONDS);
            } else {
                state.cancelTimeout();
            }
            state.setListener(listener);
        }
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
                // System.out.println(key.channel());
                keys.remove();
                
                try {
                    handleKey(key);
                } catch (IOException e) {
                    if (!(e instanceof PortUnreachableException))
                        System.err.println(e);
                    key.cancel();
                    key.channel().close();
                }
            }
        }
        
        executor.shutdownNow();
    }
    
    private void handleKey(SelectionKey key) throws IOException {
        try {
            ChannelState state = (ChannelState) key.attachment();
            state.getListener().ready(key.channel(), key.readyOps());
        } catch (CancelledKeyException e) {
            System.err.printf("warning: %s%n", e);
        }
    }
    
    /**
     * Holds information associated with a channel.
     */
    private class ChannelState {
        private ChannelListener listener;
        private ScheduledFuture<?> timeoutTask;
        
        public ChannelState(ChannelListener listener) {
            this(listener, null, 0L, TimeUnit.SECONDS);
        }
        
        public ChannelState(ChannelListener listener, Runnable timeoutAction,
            long timeout, TimeUnit unit) 
        {
            this.listener = listener;
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
        
        public void resetTimeout(Runnable timeoutAction, long timeout,
            TimeUnit unit)
        {
            if (timeoutTask == null || timeoutTask.cancel(false)) {
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
