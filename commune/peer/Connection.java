package commune.peer;

import commune.protocol.Message;
import commune.protocol.InvalidMessageException;

import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;

public abstract class Connection {
    protected Reactor reactor;
    private SocketAddress remoteAddress;
    protected SelectableChannel channel;
    
    protected Connection(Reactor reactor, SelectableChannel channel) {
        this.reactor = reactor;
        this.channel = channel;
        
        if (channel instanceof DatagramChannel) {
            remoteAddress = ((DatagramChannel) channel).socket().
                getRemoteSocketAddress();
        } else if (channel instanceof SocketChannel) {
            remoteAddress = ((SocketChannel) channel).socket().
                getRemoteSocketAddress();
        } else {
            // shouldn't be reached
            remoteAddress = null;
        }
    }
    
    /**
     * Returns the channel that this connection is using.
     * @return channel that this connection is using
     */
    public SelectableChannel getChannel() {
        return channel;
    }
    
    /**
     * Returns the address and port of the remote process.
     * @return address and port of the remote process
     */
    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }
    
    protected void sendMessage(Message message) throws ClosedChannelException {
        sendMessage(message, true);
    }
    
    protected void sendMessage(Message message, boolean closeWhenDone)
        throws ClosedChannelException
    {
        MessageSender sender = new MessageSender(message, closeWhenDone);
        reactor.register(channel, SelectionKey.OP_WRITE, sender, 10);
    }
    
    /**
     * Sends the given message.
     * 
     * When the message has been sent, the given channel listener is registered
     * to handle a response from the other side.
     */
    protected void sendMessage(Message message, ChannelListener nextStep)
        throws ClosedChannelException
    {
        MessageSender sender = new MessageSender(message, nextStep);
        reactor.register(channel, SelectionKey.OP_WRITE, sender, 10);
    }
    
    protected void sendDatagramMessage(Message message, SocketAddress dest,
        ChannelListener nextStep) throws ClosedChannelException
    {
        MessageSender sender = new MessageSender(message, dest,
            nextStep, SelectionKey.OP_READ);
        reactor.register(channel, SelectionKey.OP_WRITE, sender, 10);
    }
    
    public String toString() {
        return String.format("Connection to %s", remoteAddress);
    }
    
    private class MessageSender implements ChannelListener {
        private ByteBuffer messageBuffer;
        private ChannelListener nextStep;
        private int nextOps;
        private boolean closeWhenDone;
        private SocketAddress dest;
        
        public MessageSender(Message message) {
            this(message, true);
        }
        
        public MessageSender(Message message, boolean closeWhenDone) {
            this(message, null, 0);
            this.closeWhenDone = closeWhenDone;
        }
        
        public MessageSender(Message message, ChannelListener nextStep) {
            this(message, nextStep, SelectionKey.OP_READ);
        }
        
        public MessageSender(Message message, ChannelListener nextStep,
            int nextOps)
        {
            this(message, null, nextStep, nextOps);
        }
        
        public MessageSender(Message message, SocketAddress dest,
            ChannelListener nextStep, int nextOps) 
        {
            messageBuffer = message.getBytes();
            this.dest = dest;
            this.nextStep = nextStep;
            this.nextOps = nextOps;
            this.closeWhenDone = true;
        }
        
        public void ready(final SelectableChannel c, int operations)
            throws IOException
        {
            if (messageBuffer.hasRemaining()) {
                if (dest != null) {
                    ((DatagramChannel) channel).send(messageBuffer, dest);
                } else {
                    ((ByteChannel) channel).write(messageBuffer);
                }
            } else {
                // Done sending.
                if (nextStep != null) {
                    // Start listening for a response.
                    reactor.register(c, nextOps, nextStep, 10);
                } else if (closeWhenDone) {
                    // No response expected; close connection.
                    channel.close();
                }
            }
        }
    }
    
    protected abstract class MessageListener implements ChannelListener {
        private short expectedCode;
        private ByteBuffer headerBuffer;
        private ByteBuffer overallBuffer;
        
        protected MessageListener(short code) {
            this.expectedCode = code;
            
            headerBuffer = ByteBuffer.allocate(Message.HEADER_LENGTH);
            overallBuffer = null;
        }
        
        private void read(ByteBuffer buffer) throws IOException {
            ((ByteChannel) channel).read(buffer);
        }
        
        public void ready(final SelectableChannel c, int operations)
            throws IOException
        {
            if (overallBuffer == null) {
                read(headerBuffer);
                if (headerBuffer.position() == headerBuffer.capacity())
                    processHeader();
            } else {
                read(overallBuffer);
                if (overallBuffer.position() == overallBuffer.capacity()) {
                    overallBuffer.flip();
                    processMessage();
                }
            }
        }
        
        private void processHeader() {
            headerBuffer.flip();
            
            int length = headerBuffer.getInt();
            short code = headerBuffer.getShort();
            headerBuffer.rewind();
            
            if (code != expectedCode) {
                System.err.printf("warning: got unexpected message 0x%02X%n",
                    code);
            } else {
                overallBuffer = ByteBuffer.allocate(length);
                overallBuffer.put(headerBuffer);
            }
        }
        
        private void processMessage() {
            try {
                Message message = Message.parseMessage(overallBuffer);
                try {
                    received(message);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            } catch (InvalidMessageException e) {
                try {
                    invalidMessage(e);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }
        
        protected abstract void received(Message message) throws IOException;
        protected abstract void invalidMessage(InvalidMessageException e)
            throws IOException;
    }
    
    protected abstract class DatagramMessageListener implements ChannelListener {
        private short expectedCode;
        
        public DatagramMessageListener(short code) {
            expectedCode = code;
        }
        
        public void ready(final SelectableChannel c, int operations)
            throws IOException
        {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            SocketAddress src = ((DatagramChannel) channel).receive(buffer);
            buffer.flip();
            
            try {
                Message message = Message.parseMessage(buffer);
                
                if (message.getType() != expectedCode) {
                    throw new InvalidMessageException("wrong type");
                }
                
                try {
                    received(message, src);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            } catch (InvalidMessageException e) {
                try {
                    invalidMessage(e);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }
        
        protected abstract void received(Message message, SocketAddress src)
            throws IOException;
        protected abstract void invalidMessage(InvalidMessageException e)
            throws IOException;
    }
}
