package commune.peer.client;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.*;

public class FutureTask<V> implements Future<V> {
    private V value;
    private boolean set;
    private Throwable error;
    private final Lock lock;
    private final Condition ready;
    
    public FutureTask() {
        value = null;
        set = false;
        lock = new ReentrantLock();
        ready = lock.newCondition();
    }
    
    public boolean cancel(boolean mayInterrupt) {
        return false;
    }
    
    public V get() throws ExecutionException, InterruptedException {
        lock.lock();
        try {
            ready.await();
            
            if (error != null)
                throw new ExecutionException(error);
            
            return value;
        } finally {
            lock.unlock();
        }
    }
    
    public V get(long timeout, TimeUnit unit)
        throws ExecutionException, InterruptedException, TimeoutException
    {
        lock.lock();
        try {
            if (!ready.await(timeout, unit)) {
                throw new TimeoutException();
            }
            
            if (error != null)
                throw new ExecutionException(error);
            
            return value;
        } finally {
            lock.unlock();
        }
    }
    
    public boolean isCancelled() {
        return false;
    }
    
    public boolean isDone() {
        return set;
    }
    
    void set(V value) {
        lock.lock();
        try {
            this.value = value;
            this.set = true;
            ready.signalAll();
        } finally {
            lock.unlock();
        }
    }
    
    void setError(Throwable error) {
        lock.lock();
        try {
            this.error = error;
            this.set = true;
            ready.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
