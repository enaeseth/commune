package commune.peer.client;

import commune.protocol.*;
import commune.net.Reactor;
import commune.peer.MessageBroker;
import commune.peer.Receiver;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.Future;

/**
 * A client's connection to a server.
 */
public class ServerConnection {
    private MessageBroker broker;
    private SocketChannel channel;
    private File storage;
    private long lastContact;
    private Map<Integer, Request> requests;
    private InetAddress localAddress;
    
    private Queue<Request> pendingRequests;
    
    public ServerConnection(Reactor reactor, SocketChannel channel,
        File storage) 
    {
        this.storage = storage;
        this.channel = channel;
        broker = new MessageBroker(reactor, channel);
        lastContact = 0;
        
        localAddress = channel.socket().getLocalAddress();
        requests = new HashMap<Integer, Request>();
        pendingRequests = new LinkedList<Request>();
        
        configureBroker();
    }
    
    private void configureBroker() {
        broker.receive(HelloMessage.class, new HelloReceiver()).
            receive(ResponseMessage.class, new ResponseReceiver()).
            receive(PayloadMessage.class, new PayloadReceiver());
    }
    
    public SocketAddress getRemoteAddress() {
        return channel.socket().getRemoteSocketAddress();
    }
    
    public boolean isConnected() {
        return channel.socket().isConnected();
    }
    
    public Future<File> request(String path) throws IOException {
        Request request = createRequest(path);
        long now = System.currentTimeMillis();
        if (now - lastContact >= 40000L) {
            // Send a "hello" if it's been at least 40 seconds since we last
            // received a message from this server.
            
            pendingRequests.offer(request);
            sendHello();
        } else {
            request.send();
        }
        
        return request.getTask();
    }
    
    public void sendHello() {
        broker.send(new HelloMessage("Commune Reference/0.3", false));
    }
    
    private Request createRequest(String path) {
        FutureTask<File> task = new FutureTask<File>();
        Request request;
        
        synchronized (requests) {
            int highestID = -1;
            
            for (Integer activeID : requests.keySet()) {
                highestID = Math.max(activeID, highestID);
            }
            
            int id = highestID + 1;
            request = new Request(id, path, task);
            requests.put(id, request);
        }
        
        return request;
    }
    
    private void closeRequest(Request request) {
        synchronized (requests) {
            requests.remove(request.getID());
        }
    }
    
    private class HelloReceiver implements Receiver<HelloMessage> {
        public void received(HelloMessage message) throws IOException {
            System.err.printf("[client] got hello from %s (%s)%n",
                getRemoteAddress(), message.getUserAgent());
            lastContact = System.currentTimeMillis();
            
            // Send any pending requests
            Request pending;
            while ((pending = pendingRequests.poll()) != null) {
                pending.send();
            }
        }
    }
    
    private Request getRequest(int id) {
        Request request = requests.get(id);
        if (request == null) {
            System.err.printf("[client] error: got response from %s " +
                "for unrecognized request ID %d%n", getRemoteAddress(), id);
        }
        return request;
    }
    
    private class ResponseReceiver implements Receiver<ResponseMessage> {
        public void received(ResponseMessage message) throws IOException {
            lastContact = System.currentTimeMillis();
            
            Request request = getRequest(message.getID());
            if (request != null)
                request.responseReceived(message);
        }
    }
    
    private class PayloadReceiver implements Receiver<PayloadMessage> {
        public void received(PayloadMessage message) throws IOException {
            lastContact = System.currentTimeMillis();
            Request request = getRequest(message.getRequestID());
            if (request != null)
                request.payloadReceived(message);
        }
    }
    
    private File getOutputFile(String path) {
        String[] parts = path.split("/");
        return new File(storage, parts[parts.length - 1]);
    }
    
    private class Request {
        private int id;
        private FutureTask<File> task;
        private String path;
        private long fileLength;
        private File outputFile;
        private RandomAccessFile outputAccess;
        private ByteBuffer outputBuffer;
        
        public Request(int id, String path, FutureTask<File> task) {
            this.id = id;
            this.path = path;
            this.task = task;
            outputFile = null;
            outputAccess = null;
            outputBuffer = null;
        }
        
        /**
         * Returns the request's ID.
         * @return request's ID
         */
        public int getID() {
            return id;
        }
        
        /**
         * Returns the requested path.
         * @return requested path
         */
        public String getPath() {
            return path;
        }
        
        /**
         * Returns the request task.
         * @return request task
         */
        public FutureTask<File> getTask() {
            return task;
        }
        
        public void send() throws IOException {
            System.err.printf("[client] requesting %s from %s%n",
                path, getRemoteAddress());
            
            broker.send(new RequestMessage(id, path));
        }
        
        public void responseReceived(ResponseMessage message)
            throws IOException
        {
            if (message.getStatusCode() == 200) {
                System.out.printf("[client] got OK for file %s from %s%n",
                    path, getRemoteAddress());
                
                outputFile = getOutputFile(path);
                fileLength = message.getFileLength();
                outputAccess = new RandomAccessFile(outputFile, "rw");
                FileChannel channel = outputAccess.getChannel();
                outputBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0,
                    fileLength);
            } else {
                System.err.printf("[client] got %s (%d) for file %s from %s%n",
                    message.getStatusDescription(), message.getStatusCode(),
                    path, getRemoteAddress());
                task.setError(new IOException(String.format("%s (%d)",
                    message.getStatusDescription(), message.getStatusCode())));
                closeRequest(this);
            }
        }
        
        public void payloadReceived(PayloadMessage message)
            throws IOException
        {
            outputBuffer.put(message.getBody());
            
            if (outputBuffer.position() >= fileLength) {
                System.out.printf("[client] done receiving file %s%n", path);
                
                closeRequest(this);
                outputAccess.close();
                task.set(outputFile);
            }
        }
    }
}
