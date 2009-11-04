package commune.net;

import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;

public class Reactor {
    private Selector selector;
    private ScheduledExecutorService timeoutService;
    
    public Reactor() throws IOException {
        selector = Selector.open();
        timeoutService = Executors.newScheduledThreadPool(1);
    }
    
    public boolean listen(SelectableChannel channel, Operation operation,
        Listener listener)
    {
        return listen(channel, EnumSet.of(operation), listener);
    }
    
    public boolean listen(SelectableChannel channel, Operation operation,
        Listener listener, int timeout, TimeoutTask task)
    {
        return listen(channel, EnumSet.of(operation), listener, timeout, task);
    }
    
    public boolean listen(SelectableChannel channel,
        EnumSet<Operation> operations, Listener listener)
    {
        return listen(channel, operations, listener, 0, null);
    }
    
    public boolean listen(SelectableChannel channel,
        EnumSet<Operation> operations, Listener listener, int timeout,
        TimeoutTask task)
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
            if (timeout > 0 && task != null)
                state.setTimeout(op, task, timeout);
            interestOps |= op.selectorOperation();
        }
        
        try {
            if (key != null) {
                key.interestOps(interestOps);
            } else {
                channel.register(selector, interestOps, state);
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
            key.interestOps(interestOps);
            return true;
        } catch (CancelledKeyException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public void cancel(SelectableChannel channel) {
        SelectionKey key = channel.keyFor(selector);
        
        if (key == null)
            return;
        
        ((State) key.attachment()).clearTimeouts();
        key.cancel();
    }
    
    private class State {
        private SelectableChannel channel;
        private EnumMap<Operation, Listener> listeners;
        private EnumMap<Operation, ScheduledFuture<?>> timeoutTasks;
        
        public State(SelectableChannel channel) {
            this.channel = channel;
            listeners = new EnumMap<Operation, Listener>(Operation.class);
            timeoutTasks =
                new EnumMap<Operation, ScheduledFuture<?>>(Operation.class);
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
