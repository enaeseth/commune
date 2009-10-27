package commune.peer.server;

import commune.peer.Reactor;
import commune.peer.ChannelListener;
import commune.peer.source.ResourceManager;
import commune.peer.source.AvailableResource;
import commune.protocol.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.nio.*;
import java.nio.channels.*;

public class Server {
    private Reactor reactor;
    private ResourceManager manager;
    private ServerSocketChannel serverChannel;
    
    public static final int DEFAULT_PORT = 2666;
    
    public Server(Reactor reactor, ResourceManager manager) {
        this.reactor = reactor;
        this.manager = manager;
        
        serverChannel = null;
    }
    
    public void listen(int port) throws IOException {
        serverChannel = ServerSocketChannel.open();
        
        ServerSocket socket = serverChannel.socket();
        socket.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        
        reactor.register(serverChannel, SelectionKey.OP_ACCEPT,
            new AcceptListener());
    }
    
    private class AcceptListener implements ChannelListener {
        public void ready(SelectableChannel channel, int operations)
            throws IOException
        {
            ServerSocketChannel server = (ServerSocketChannel) channel;
            SocketChannel client = server.accept();
            client.configureBlocking(false);
            System.out.printf("Got connection from %s.%n",
                client.socket().getRemoteSocketAddress());
            
            reactor.register(client, SelectionKey.OP_READ,
                new RequestListener(), 10);
        }
    }
    
    private class RequestListener implements ChannelListener {
        private ByteBuffer buffer;
        
        public RequestListener() {
            buffer = ByteBuffer.allocate(4096);
        }
        
        public void ready(SelectableChannel channel, int operations)
            throws IOException
        {
            SocketChannel client = (SocketChannel) channel;
            
            client.read(buffer);
            
            int requestLength = findEndOfRequest();
            if (requestLength < 0) {
                // we haven't yet gotten the whole request
                
                if (buffer.position() >= buffer.limit()) {
                    // and we never will, because our request buffer is full.
                    // close the connection; a request header of more than 4KiB
                    // is probably some kind of attack anyway.
                    System.err.printf("request from %s was too large; " +
                        "dropping connection%n",
                        client.socket().getRemoteSocketAddress());
                    client.close();
                }
                
                return;
            }
            
            byte[] data = new byte[requestLength];
            buffer.rewind();
            buffer.get(data);
            
            try {
                handleRequest(client, Request.parse(data));
            } catch (InvalidRequestException e) {
                System.err.printf("invalid request from %s: %s%n",
                    client.socket().getRemoteSocketAddress(),
                    e.getMessage());
                client.close();
            }
        }
        
        private void handleRequest(SocketChannel client, Request request)
            throws IOException
        {
            String path = request.getResource();
            System.out.printf("Got request for %s: ", path);
            AvailableResource resource = manager.getResource(path);
            Response response;
            ChannelListener responder;
            
            if (resource == null) {
                response = new Response(404, "Not Found");
                responder = new Responder(response,
                    String.format("The resource you requested (%s) was not " +
                    "found.", path));
            } else {
                response = new Response(200, "OK");
                response.addHeader("Content-Type", resource.getContentType());
                responder = new Responder(response, resource.read());
            }
            System.out.println(response.getStatusText());
            
            reactor.register(client, SelectionKey.OP_WRITE, responder, 15);
        }
        
        private int findEndOfRequest() {
            byte b;
            int limit = buffer.limit();
            for (int i = 0; i < (limit - 1); i++) {
                b = buffer.get(i);
                if (b == '\n' && buffer.get(i + 1) == '\n')
                    return i + 2;
                if (b == '\r' && i < (limit - 3) && buffer.get(i + 3) == '\n')
                    return i + 4;
            }

            return -1;
        }
    }
    
    private class Responder implements ChannelListener {
        private ByteBuffer headerBuffer;
        private ByteBuffer bodyBuffer;
        
        public Responder(Response response, String message) {
            response.addHeader("Content-Type", "text/plain; charset=utf-8");
            
            try {
                byte[] bytes = message.getBytes("UTF-8");
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                setupBuffers(response, buffer);
            } catch (IOException e) {
                // All JVM's support UTF-8.
                throw new RuntimeException(e);
            }
        }
        
        public Responder(Response response, ByteBuffer body) {
            setupBuffers(response, body);
        }
        
        private void setupBuffers(Response response, ByteBuffer body) {
            response.addHeader("Server", "Commune Reference/0.1");
            response.addHeader("Content-Length",
                Integer.toString(body.limit()));
            
            String responseString = response.toString();
            try {
                byte[] responseBytes = responseString.getBytes("UTF-8");
                headerBuffer = ByteBuffer.wrap(responseBytes);
            } catch (IOException e) {
                // All JVM's support UTF-8.
                throw new RuntimeException(e);
            }
            
            bodyBuffer = body;
        }
        
        public void ready(SelectableChannel channel, int operations)
            throws IOException
        {
            SocketChannel client = (SocketChannel) channel;
            
            if (headerBuffer.hasRemaining()) {
                client.write(headerBuffer);
            } else if (bodyBuffer.hasRemaining()) {
                client.write(bodyBuffer);
            } else {
                reactor.register(client, SelectionKey.OP_READ,
                    new AcknowledgementListener(), 10);
            }
        }
    }
    
    private class AcknowledgementListener implements ChannelListener {
        private ByteBuffer buffer;
        
        public AcknowledgementListener() {
            buffer = ByteBuffer.allocate(1024);
        }
        
        public void ready(SelectableChannel channel, int operations)
            throws IOException
        {
            SocketChannel client = (SocketChannel) channel;
            
            client.read(buffer);
            int responseLength = findEndOfResponse();
            if (responseLength < 0) {
                // we haven't yet gotten the whole response
                
                if (buffer.position() >= buffer.limit()) {
                    // and we never will, because our response buffer is full.
                    // close the connection; a header of more than 1KiB is
                    // probably some kind of attack anyway.
                    System.err.printf("acknowledgement from %s was too " +
                        "large; dropping connection%n",
                        client.socket().getRemoteSocketAddress());
                    client.close();
                }
                
                return;
            }
            
            System.out.printf("Read acknowledgement; closing connection " +
                "from %s.%n", client.socket().getRemoteSocketAddress());
            reactor.cancel(client);
            client.close();
        }
        
        private int findEndOfResponse() {
            byte b;
            int limit = buffer.limit();
            for (int i = 0; i < (limit - 1); i++) {
                b = buffer.get(i);
                if (b == '\n' && buffer.get(i + 1) == '\n')
                    return i + 2;
                if (b == '\r' && i < (limit - 3) && buffer.get(i + 3) == '\n')
                    return i + 4;
            }

            return -1;
        }
    }
}
