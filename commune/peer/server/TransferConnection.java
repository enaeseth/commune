package commune.peer.server;

import commune.protocol.*;
import commune.peer.Reactor;
import commune.peer.Connection;
import commune.peer.ChannelListener;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

/**
 * Manages a UDP connection for sending a file to a client.
 */
public class TransferConnection extends Connection {
    private int transferID;
    private ByteBuffer content;
    private int windowSize;
    private int maxLength;
    private float effort;
    
    private PacketQueue queue;
    private long position;
    private Random random;
    private Timer timeout;
    
    public TransferConnection(int id, Reactor reactor, DatagramChannel channel,
        ByteBuffer content, int windowSize, int maxLength, float effort)
        throws IOException
    {
        super(reactor, channel);
        
        this.transferID = id;
        this.content = content;
        this.effort = effort;
        this.maxLength = maxLength - Message.HEADER_LENGTH -
            PayloadMessage.OVERHEAD;
        this.windowSize = windowSize * this.maxLength;
        
        position = 0L;
        random = new Random();
        queue = new PacketQueue();
        timeout = new Timer(true);
        
        reactor.register(channel, SelectionKey.OP_READ | SelectionKey.OP_WRITE,
            new Listener());
    }
    
    private class PacketQueue {
        private LinkedList<PayloadMessage> transmissionQueue;
        private LinkedList<PayloadMessage> acknowledgementQueue;
        private boolean retransferPending;
        
        public PacketQueue() {
            transmissionQueue = new LinkedList<PayloadMessage>();
            acknowledgementQueue = new LinkedList<PayloadMessage>();
            retransferPending = false;
        }
        
        public int getOutstanding() {
            int outstanding = 0;
            synchronized (this) {
                for (PayloadMessage message : acknowledgementQueue) {
                    outstanding += message.getBody().length;
                }
            }
            return outstanding;
        }
        
        public boolean enqueue(PayloadMessage packet) {
            if (getOutstanding() >= windowSize)
                return false;
            
            synchronized (this) {
                transmissionQueue.offer(packet);
                acknowledgementQueue.offer(packet);
                return true;
            }
        }
        
        public synchronized boolean acknowledge(long offset) {
            PayloadMessage expected = getUnacknowledged();
            long expectedOffset = expected.getOffset();
            
            // System.out.printf("\t\t\t\t%d%n", offset);
            
            if (expectedOffset == offset) {
                acknowledgementQueue.remove();
                return true;
            } else {
                if (!retransferPending) {
                    System.out.printf("[server] got ack for offset %d, but " +
                        "expected %d%n", offset, expectedOffset);
                    requeue();
                }
                return false;
            }
        }
        
        public synchronized void requeue() {
            transmissionQueue.clear();
            
            // System.out.print("requeue: ");
            for (PayloadMessage message : acknowledgementQueue) {
                transmissionQueue.offer(message);
                // System.out.print(".");
            }
            
            if (transmissionQueue.size() > 0)
                retransferPending = true;
            // System.out.println();
        }
        
        public synchronized PayloadMessage getNext() {
            PayloadMessage message = transmissionQueue.poll();
            if (transmissionQueue.size() <= 0)
                retransferPending = false;
            return message;
        }
        
        public synchronized PayloadMessage getUnacknowledged() {
            return acknowledgementQueue.getFirst();
        }
    }
    
    private boolean isDone() {
        return (content.position() >= content.limit());
    }
    
    private void scheduleTimeout() {
        timeout.purge();
        timeout.schedule(new TimerTask() {
            public void run() {
                queue.requeue();
            }
        }, 2200L);
    }
    
    public boolean queueNewData() {
        // System.out.print("queueNewData: ");
        if (isDone()) {
            return false;
        }
        
        while (!isDone()) {
            int length = Math.min(maxLength,
                content.limit() - content.position());
            byte[] body = new byte[length];
            synchronized (content) {
                content.position((int) position);
                content.get(body);
            }
            
            PayloadMessage message = new PayloadMessage(transferID,
                position, body);
            if (!queue.enqueue(message))
                break;
            // System.out.print(".");
            position += length;
        }
        // System.out.println();
        
        scheduleTimeout();
        return true;
    }
    
    private void handleAck(AcknowledgementMessage message) {
        long offset = message.getOffset();
        
        if (queue.acknowledge(offset)) {
            queueNewData();
        } else {
            queue.requeue();
            scheduleTimeout();
        }
    }
    
    private class Listener implements ChannelListener {
        private ByteBuffer currentOutgoing = null;
        
        public void ready(SelectableChannel channel, int operations)
            throws IOException
        {
            if ((operations & SelectionKey.OP_READ) != 0) {
                ByteBuffer buf = ByteBuffer.allocate(64);
                ((DatagramChannel) channel).read(buf);
                buf.flip();
                
                try {
                    Message message = Message.parseMessage(buf);
                    
                    if (!(message instanceof AcknowledgementMessage))
                        throw new InvalidMessageException("wrong type");
                    
                    handleAck((AcknowledgementMessage) message);
                } catch (InvalidMessageException e) {
                    System.err.printf("[server] got invalid payload ack: %s%n",
                        e.getMessage());
                }
            }
            
            if ((operations & SelectionKey.OP_WRITE) != 0) {
                if (currentOutgoing == null) {
                    PayloadMessage payload = null;
                    
                    synchronized (queue) {
                        do {
                            payload = queue.getNext();
                        } while (random.nextFloat() > effort);
                    }
                    
                    if (payload == null) {
                        if (isDone())
                            channel.close();
                        return;
                    }
                    
                    // System.err.printf("%d%n", payload.getOffset());
                    
                    // System.err.printf("[server] new payload to send; " +
                    //     "offset = %d%n", payload.getOffset());
                    currentOutgoing = payload.getBytes();
                    // byte[] bytes = new byte[currentOutgoing.limit()];
                    // currentOutgoing.get(bytes);
                    // System.out.println(Arrays.toString(bytes));
                    // currentOutgoing.rewind();
                }
                
                if (currentOutgoing.hasRemaining()) {
                    ((DatagramChannel) channel).write(currentOutgoing);
                } else {
                    currentOutgoing = null;
                }
            }
        }
    }
    
    private static class Trace {
        public long offset;
        public int length;
        
        public Trace(long offset, int length) {
            this.offset = offset;
            this.length = length;
        }
        
        public String toString() {
            return String.format("(%d, %d)", offset, length);
        }
    }
}
