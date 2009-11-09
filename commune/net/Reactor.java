package commune.net;

import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * A reactor forms the heart of Commune's networking infrastructure.
 * 
 * Reactor provides a higher-level interface to Selector. Applications ask the
 * Reactor to "listen" for one or more events on a socket and to call a method
 * on an object when that event happens. The events are defined in the
 * Operation class.
 */
public class Reactor implements Runnable {
    private Selector selector;
    private ScheduledExecutorService timeoutService;
    private Thread thread;
    private transient boolean registering;
    private Set<CloseListener> closeListeners;
    
    /**
     * Creates a new reactor.
     */
    public Reactor() throws IOException {
        selector = Selector.open();
        timeoutService = Executors.newScheduledThreadPool(1);
        thread = null;
        registering = false;
        closeListeners =
            Collections.synchronizedSet(new HashSet<CloseListener>());
    }
    
    /**
     * Starts the reactor on a new dedicated thread.
     * @return the created thread
     */
    public Thread start() {
        thread = new Thread(this, "Reactor");
        thread.start();
        return thread;
    }
    
    public boolean addCloseListener(CloseListener listener) {
        return closeListeners.add(listener);
    }
    
    public boolean removeCloseListener(CloseListener listener) {
        return closeListeners.remove(listener);
    }
    
    /**
     * Runs the reactor. This method will not return until the reactor thread
     * is interrupted.
     */
    public void run() {
        while (!Thread.interrupted()) {
            try {
                while (registering) {
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                selector.select();
            } catch (IOException e) {
                System.err.println("=== error in select() ===");
                e.printStackTrace();
                return;
            }
            
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                
                State state = (State) key.attachment();
                try {
                    state.dispatch(key.readyOps());
                } catch (IOException e) {
                    if (isNotable(e))
                        System.err.println(e);
                    closed(key);
                    cancel(key.channel());
                    try {
                        key.channel().close();
                    } catch (IOException ignored) { /* ignore */ }
                }
            }
        }
    }
    
    private void closed(SelectableChannel channel, Object attachment) {
        synchronized (closeListeners) {
            for (CloseListener listener : closeListeners) {
                listener.channelClosed(channel, attachment);
            }
        }
    }
    
    private void closed(SelectionKey key) {
        closed(key.channel(), ((State) key.attachment()).getAttachment());
    }
    
    private boolean isNotable(IOException e) {
        return !(e instanceof PortUnreachableException ||
            e instanceof ClosedByInterruptException);
    }
    
    public boolean listen(SelectableChannel channel, Operation operation,
        Listener listener)
    {
        return listen(channel, EnumSet.of(operation), listener);
    }
    
    public boolean listen(SelectableChannel channel,
        EnumSet<Operation> operations, Listener listener)
    {
        SelectionKey key = channel.keyFor(selector);
        State state;
        int interestOps = 0;
        
        if (key != null) {
            interestOps = key.interestOps();
            state = (State) key.attachment();
        } else {
            state = new State(channel);
        }
        
        for (Operation op : operations) {
            state.setListener(op, listener);
            interestOps |= op.selectorOperation();
        }
        
        return setOperations(channel, key, interestOps, state);
    }
    
    public Object getAttachment(SelectableChannel channel) {
        SelectionKey key = channel.keyFor(selector);
        if (key != null) {
            State state = (State) key.attachment();
            return state.getAttachment();
        }
        return null;
    }
    
    public boolean attach(SelectableChannel channel, Object attachment) {
        SelectionKey key = channel.keyFor(selector);
        if (key != null) {
            State state = (State) key.attachment();
            state.setAttachment(attachment);
            return true;
        }
        return false;
    }
    
    private boolean setOperations(SelectableChannel channel, SelectionKey key,
        int interestOps, State state)
    {
        try {
            if (key != null) {
                key.interestOps(interestOps);
            } else {
                synchronized (this) {
                    registering = true;
                    try {
                        selector.wakeup();
                        channel.register(selector, interestOps, state);
                    } finally {
                        registering = false;
                    }
                }
            }
            return true;
        } catch (CancelledKeyException e) {
            e.printStackTrace();
            return false;
        } catch (ClosedChannelException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean timeout(SelectableChannel channel, Operation operation,
        int delay, TimeoutTask task)
    {
        return timeout(channel, EnumSet.of(operation), delay, task);
    }
    
    public boolean timeout(SelectableChannel channel,
        EnumSet<Operation> operations, int delay, TimeoutTask task)
    {
        SelectionKey key = channel.keyFor(selector);
        State state;
        int interestOps = 0;
        
        if (key != null) {
            interestOps = key.interestOps();
            state = (State) key.attachment();
        } else {
            state = new State(channel);
        }
        
        for (Operation op : operations) {
            state.setTimeout(op, task, delay);
            interestOps |= op.selectorOperation();
        }
        
        return setOperations(channel, key, interestOps, state);
    }
    
    public boolean remove(SelectableChannel channel, Operation operation) {
        return remove(channel, EnumSet.of(operation));
    }
    
    public boolean remove(SelectableChannel channel,
        EnumSet<Operation> operations)
    {
        SelectionKey key = channel.keyFor(selector);
        
        if (key == null)
            return false;
        
        State state = (State) key.attachment();
        int interestOps = key.interestOps();
        
        for (Operation op : operations) {
            interestOps &= ~(op.selectorOperation());
            state.removeListener(op);
            state.clearTimeout(op);
        }
        
        try {
            synchronized (this) {
                registering = true;
                try {
                    selector.wakeup();
                    key.interestOps(interestOps);
                } finally {
                    registering = false;
                }
            }
            return true;
        } catch (CancelledKeyException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public void cancel(SelectableChannel channel) {
        cancel(channel, false);
    }
    
    public void cancel(SelectableChannel channel, boolean closing) {
        SelectionKey key = channel.keyFor(selector);
        
        if (key == null)
            return;
        if (closing)
            closed(key);
        
        ((State) key.attachment()).clearTimeouts();
        key.cancel();
    }
    
    private class State {
        private SelectableChannel channel;
        private EnumMap<Operation, Listener> listeners;
        private EnumMap<Operation, ScheduledFuture<?>> timeoutTasks;
        private Object attachment;
        
        public State(SelectableChannel channel) {
            this.channel = channel;
            listeners = new EnumMap<Operation, Listener>(Operation.class);
            timeoutTasks =
                new EnumMap<Operation, ScheduledFuture<?>>(Operation.class);
            attachment = null;
        }
        
        /**
         * Returns the channel's reactor attachment.
         * @return channel's reactor attachment
         */
        public Object getAttachment() {
            return attachment;
        }
        
        public void setAttachment(Object newAttachment) {
            attachment = newAttachment;
        }
        
        public void dispatch(int readyOps) throws IOException {
            for (Map.Entry<Operation, Listener> e : listeners.entrySet()) {
                Operation op = e.getKey();
                
                if ((readyOps & op.selectorOperation()) != 0) {
                    Listener listener = e.getValue();
                    
                    clearTimeout(op);
                    listener.ready(channel);
                }
            }
        }
        
        public void setListener(Operation op, Listener listener) {
            listeners.put(op, listener);
        }
        
        public void removeListener(Operation op) {
            listeners.remove(op);
        }
        
        public void setTimeout(Operation op, final TimeoutTask task,
            int delay)
        {
            clearTimeout(op);
            
            Runnable runnable = new Runnable() {
                public void run() {
                    try {
                        task.timedOut(channel);
                    } catch (IOException e) {
                        e.printStackTrace();
                        cancel(channel);
                        
                        try {
                            channel.close();
                        } catch (IOException ignored) { /* ignore */ }
                    }
                }
            };
            
            ScheduledFuture<?> future = timeoutService.schedule(runnable,
                delay, TimeUnit.SECONDS);
        }
        
        public boolean clearTimeout(Operation op) {
            ScheduledFuture<?> future = timeoutTasks.get(op);
            if (future != null) {
                if (!future.isCancelled()) {
                    if (!future.cancel(true)) {
                        System.err.println("warning: failed to cancel " +
                            "existing timeout task");
                        return false;
                    }
                }
                
                timeoutTasks.remove(op);
            }
            
            return (future != null);
        }
        
        public void clearTimeouts() {
            for (Operation op : timeoutTasks.keySet())
                clearTimeout(op);
        }
    }
}
