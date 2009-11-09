package commune.peer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import commune.net.*;
import commune.protocol.Message;
import commune.protocol.InvalidMessageException;

public class MessageBroker {
    private Reactor reactor;
    private SocketChannel channel;
    private Queue<Message> outgoing;
    private Queue<MessageSource> sources;
    private Map<Short, Receiver<Message>> receivers;
    private MessageReader reader;
    private MessageWriter writer;
    
    public MessageBroker(Reactor reactor, SocketChannel channel) {
        this.reactor = reactor;
        this.channel = channel;
        
        outgoing = new LinkedList<Message>();
        sources = new LinkedList<MessageSource>();
        receivers = new HashMap<Short, Receiver<Message>>();
        reader = new MessageReader();
        writer = new MessageWriter();
    }
    
    public <T extends Message> MessageBroker receive(Class<T> type,
        Receiver<T> receiver)
    {
        short code;
        
        try {
            Field field = type.getDeclaredField("CODE");
            code = (Short) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        receivers.put(code, (Receiver<Message>) receiver);
        return this;
    }
    
    public MessageBroker send(Message message) {
        synchronized (outgoing) {
            outgoing.offer(message);
            reactor.listen(channel, Operation.WRITE, writer);
        }
        return this;
    }
    
    public MessageBroker send(MessageSource source) {
        synchronized (outgoing) {
            sources.offer(source);
            reactor.listen(channel, Operation.WRITE, writer);
        }
        return this;
    }
    
    public void cancel() {
        reactor.cancel(channel);
    }
    
    private class MessageReader implements Listener {
        private ByteBuffer headerBuffer;
        private ByteBuffer overallBuffer;
        
        public MessageReader() {
            headerBuffer = ByteBuffer.allocate(Message.HEADER_LENGTH);
            overallBuffer = null;
            reactor.listen(channel, Operation.READ, this);
        }
        
        public void ready(SelectableChannel channel) throws IOException {
            if (overallBuffer == null) {
                ((ByteChannel) channel).read(headerBuffer);
                if (headerBuffer.position() == headerBuffer.capacity())
                    processHeader();
            } else {
                ((ByteChannel) channel).read(overallBuffer);
                if (overallBuffer.position() == overallBuffer.capacity()) {
                    overallBuffer.flip();
                    processMessage();
                    overallBuffer = null;
                }
            }
        }
        
        private void processHeader() {
            headerBuffer.flip();
            
            int length = headerBuffer.getInt();
            short code = headerBuffer.getShort();
            headerBuffer.rewind();
            
            overallBuffer = ByteBuffer.allocate(length);
            overallBuffer.put(headerBuffer);
        }
        
        private void processMessage() {
            try {
                Message message = Message.parseMessage(overallBuffer);
                try {
                    Receiver<Message> r = receivers.get(message.getType());
                    if (r != null) {
                        r.received(message);
                    }
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            } catch (InvalidMessageException e) {
                e.printStackTrace();
            }
        }
    }
    
    private class MessageWriter implements Listener {
        private ByteBuffer buffer;
        private MessageSource source;
        
        public MessageWriter() {
            buffer = null;
            source = null;
        }
        
        public void ready(SelectableChannel channel) throws IOException {
            if (buffer == null) {
                synchronized (outgoing) {
                    Message nextMessage = outgoing.poll();
                    if (nextMessage == null)
                        nextMessage = getFromSource();
                    if (nextMessage == null) {
                        reactor.remove(channel, Operation.WRITE);
                        return;
                    }
                    
                    buffer = nextMessage.getBytes();
                }
            }
            
            if (buffer.hasRemaining()) {
                ((ByteChannel) channel).write(buffer);
            } else {
                buffer = null;
            }
        }
        
        private Message getFromSource() {
            Message message = null;
            
            while (message == null) {
                if (source == null) {
                    source = sources.poll();
                }

                if (source != null) {
                    message = source.next();
                    if (message == null)
                        source = null;
                } else {
                    break;
                }
            }
            
            return message;
        }
    }
}
