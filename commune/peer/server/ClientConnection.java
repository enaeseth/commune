package commune.peer.server;

import commune.net.*;
import commune.peer.MessageBroker;
import commune.peer.MessageSource;
import commune.peer.Receiver;
import commune.protocol.*;
import commune.source.AvailableResource;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

public class ClientConnection {
    private Server server;
    private SocketChannel channel;
    private MessageBroker broker;
    private boolean helloReceived;
    
    public ClientConnection(Server server, Reactor reactor,
        SocketChannel channel) throws IOException
    {
        this.server = server;
        this.channel = channel;
        
        helloReceived = false;
        broker = new MessageBroker(reactor, channel);
        configureBroker();
    }
    
    public SocketAddress getRemoteAddress() {
        return channel.socket().getRemoteSocketAddress();
    }
    
    public void close() {
        broker.cancel();
        try {
            channel.close();
        } catch (IOException e) {
            // ignore it
        }
    }
    
    private void configureBroker() {
        broker.receive(HelloMessage.class, new HelloReceiver()).
            receive(RequestMessage.class, new RequestReceiver());
    }
    
    private class HelloReceiver implements Receiver<HelloMessage> {
        public void received(HelloMessage message) throws IOException {
            System.err.printf("[server] got hello from %s (%s)%n",
                getRemoteAddress(), message.getUserAgent());
            
            helloReceived = true;
            broker.send(new HelloMessage("Commune Reference/0.4 (PEX)", true));
        }
    }
    
    private class RequestReceiver implements Receiver<RequestMessage> {
        public void received(RequestMessage message) throws IOException {
            if (!helloReceived) {
                System.err.printf("[server] error: got request from %s " +
                    "before hello%n", getRemoteAddress());
                close();
            }
            
            System.out.printf("[server] got request for %s from %s%n",
                message.getPath(), getRemoteAddress());
            
            System.out.printf("[server] resource %s: ", message.getPath());
            AvailableResource resource = server.getResource(message.getPath());
            if (resource != null) {
                System.out.println("OK.");
                
                Response response = new Response(message.getID(), resource);
                broker.send(response);
            } else {
                System.out.println("not found!");
                broker.send(new ResponseMessage(message.getID(), (short) 404,
                    "Not Found"));
            }
        }
    }
    
    private class Response implements MessageSource {
        private static final int CHUNK_SIZE = (1024 * 512) -
            Message.HEADER_LENGTH - PayloadMessage.OVERHEAD;
        
        private int id;
        private AvailableResource resource;
        private ResponseMessage initial;
        private ByteBuffer contents;
        
        public Response(int id, AvailableResource resource) throws IOException
        {
            this.id = id;
            this.resource = resource;
            
            initial = new ResponseMessage(id, (short) 200, "OK",
                resource.getSize(), resource.getContentType());
            contents = resource.read();
        }
        
        public Message next() {
            if (initial != null) {
                // Send the initial response message.
                Message nextMessage = initial;
                initial = null;
                return nextMessage;
            }
            
            if (!contents.hasRemaining())
                return null;
            
            // Construct a new payload packet with the next chunk of the file.
            int remaining = contents.limit() - contents.position();
            byte[] dest = new byte[Math.min(remaining, CHUNK_SIZE)];
            contents.get(dest);
            
            return new PayloadMessage(id, contents.position(), dest);
        }
    }
}
