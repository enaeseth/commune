package commune.peer;

import commune.net.*;
import commune.peer.MessageBroker;
import commune.peer.MessageSource;
import commune.peer.Receiver;
import commune.protocol.*;
import commune.source.AvailableResource;
import commune.source.Source;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.Future;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A connection between two Commune peers.
 */
public class Connection {
    public static final String USER_AGENT = "Commune Reference/0.5 (PEX)";
    
    private Source source;
    private PeerListener listener;
    private File storageFolder;
    private long localID;
    private int listeningPort;
    private SocketChannel channel;
    private MessageBroker broker;
    private Peer peer;
    private boolean helloReceived;
    private long lastContact;
    private Map<Integer, Request> requests;
    private Queue<Request> pendingRequests;
    
    public Connection(Reactor reactor, SocketChannel channel, Source source,
        PeerListener listener, File storageFolder, long localID,
        int listeningPort) throws IOException
    {
        this.channel = channel;
        this.source = source;
        this.listener = listener;
        this.storageFolder = storageFolder;
        this.localID = localID;
        this.listeningPort = listeningPort;
        
        peer = Peer.fromAddress((InetSocketAddress) getRemoteAddress());
        helloReceived = false;
        lastContact = 0;
        requests = new HashMap<Integer, Request>();
        pendingRequests = new LinkedList<Request>();
        
        broker = new MessageBroker(reactor, channel);
        configureBroker();
        reactor.attach(channel, this);
    }
    
    /**
     * Sends a hello message to the other peer in this connection.
     */
    public void sendHello() {
        System.out.printf("sending hello to %s%n", describeAddress());
        broker.send(new HelloMessage(USER_AGENT, localID, listeningPort,
            false));
    }
    
    /**
     * Requests the file at the given path from the other peer.
     */
    public Future<File> request(String path) throws IOException {
        Request request = createRequest(path, false);
        sendRequest(request);
        return request.getFileTask();
    }
    
    /**
     * Requests information on the file at the given path from the other peer.
     */
    public Future<Resource> describe(String path) throws IOException {
        Request request = createRequest(path, true);
        sendRequest(request);
        return request.getResourceTask();
    }
    
    private void sendRequest(Request request) throws IOException {
        long now = System.currentTimeMillis();
        if (now - lastContact >= 40000L) {
            // Send a "hello" if it's been at least 40 seconds since we last
            // received a message from this peer.
            
            pendingRequests.offer(request);
            sendHello();
        } else {
            request.send();
        }
    }
    
    /**
     * Returns the peer on the other end of the connection.
     * @return peer on the other end of the connection
     */
    public Peer getPeer() {
        return peer;
    }
    
    public SocketAddress getRemoteAddress() {
        return channel.socket().getRemoteSocketAddress();
    }
    
    /**
     * Gets a pretty string describing the address of the other peer.
     */
    public String describeAddress() {
        InetSocketAddress addr = (InetSocketAddress) getRemoteAddress();
        if (addr == null) {
            listener.peerDisconnected(peer);
            return null;
        }
        String host = addr.getAddress().getCanonicalHostName();
        
        return (host.contains(":"))
            ? String.format("[%s]:%d", host, addr.getPort()) // IPv6 literal
            : String.format("%s:%d", host, addr.getPort()); // everything else
    }
    
    /**
     * Returns true if the socket for this connection is actually connected,
     * false if otherwise.
     */
    public boolean isConnected() {
        return channel.socket().isConnected();
    }
    
    /**
     * Closes the connection to the other peer. Any IOExceptions encountered
     * while closing the socket channel are silently ignored.
     */
    public void close() {
        broker.cancel(true);
        try {
            channel.close();
        } catch (IOException e) {
            // ignore it
        }
    }
    
    public void exchangePeers(List<Peer> peers) {
        exchangePeers(peers, false);
    }
    
    void exchangePeers(List<Peer> peers, boolean response) {
        // System.out.printf("exchanging peers with %s", describeAddress());
        // if (response)
        //     System.out.println(" (response)");
        // else
        //     System.out.println();
        broker.send(new PeerExchangeMessage(peers, response));
    }
    
    private void gotContact() {
        lastContact = System.currentTimeMillis();
        listener.peerResponded(peer);
    }
    
    /**
     * Returns the timestamp at which the last message was received.
     * @return timestamp at which the last message was received
     */
    public long getLastContact() {
        return lastContact;
    }
    
    private Request createRequest(String path, boolean hypothetical) {
        Request request;
        
        synchronized (requests) {
            int highestID = -1;
            
            for (Integer activeID : requests.keySet()) {
                highestID = Math.max(activeID, highestID);
            }
            
            int id = highestID + 1;
            request = new Request(id, path, hypothetical);
            requests.put(id, request);
        }
        
        return request;
    }
    
    private Request getRequest(int id, boolean payload) {
        Request request = requests.get(id);
        if (request == null && !payload) {
            System.err.printf("error: got response from %s for " +
                "unrecognized request ID %d%n", describeAddress(), id);
        }
        return request;
    }
    
    private void closeRequest(Request request) {
        synchronized (requests) {
            requests.remove(request.getID());
        }
    }
    
    private void configureBroker() {
        broker.receive(HelloMessage.class, new HelloReceiver()).
            receive(RequestMessage.class, new RequestReceiver()).
            receive(ResponseMessage.class, new ResponseReceiver()).
            receive(PayloadMessage.class, new PayloadReceiver()).
            receive(PeerExchangeMessage.class, new PeerExchangeReceiver());
    }
    
    private class HelloReceiver implements Receiver<HelloMessage> {
        public void received(HelloMessage message) throws IOException {
            System.err.printf("got hello from %s, using %s",
                describeAddress(), message.getUserAgent());
            if (message.isAcknowledgement()) {
                System.err.println(" [ack]");
            } else {
                System.err.println();
            }
            
            Request pending;
            while ((pending = pendingRequests.poll()) != null)
                pending.send();
            
            if (!message.isAcknowledgement()) {
                // Send a reply.
                broker.send(new HelloMessage(USER_AGENT, localID,
                    listeningPort, true));
            }
            if (!helloReceived) {
                helloReceived = true;
                
                InetAddress remote =
                    ((InetSocketAddress) getRemoteAddress()).getAddress();
                peer = Peer.fromAddress(remote, message.getListeningPort(),
                    message.getPeerID(), message.getUserAgent());
                listener.peerConnected(peer, Connection.this,
                    !message.isAcknowledgement());
            }
        }
    }
    
    private class RequestReceiver implements Receiver<RequestMessage> {
        public void received(RequestMessage message) throws IOException {
            if (!helloReceived) {
                System.err.printf("error: got request from %s before hello%n",
                    describeAddress());
                close();
            }
            
            System.out.printf("got %s request for %s from %s: ",
                (message.isHypothetical() ? "hypothetical" : "actual"),
                message.getPath(), describeAddress());
            gotContact();
            
            AvailableResource resource = source.getResource(message.getPath());
            if (resource != null) {
                System.out.println("OK.");
                broker.send(new Response(message.getID(),
                    resource, message.isHypothetical()));
            } else {
                System.out.println("not found!");
                broker.send(new ResponseMessage(message.getID(), (short) 404,
                    "Not Found"));
            }
        }
    }
    
    private class ResponseReceiver implements Receiver<ResponseMessage> {
        public void received(ResponseMessage message) throws IOException {
            gotContact();
            
            Request request = getRequest(message.getID(), false);
            if (request != null)
                request.responseReceived(message);
        }
    }
    
    private class PayloadReceiver implements Receiver<PayloadMessage> {
        public void received(PayloadMessage message) throws IOException {
            gotContact();
            
            Request request = getRequest(message.getRequestID(), true);
            if (request != null)
                request.payloadReceived(message);
        }
    }
    
    private class PeerExchangeReceiver
        implements Receiver<PeerExchangeMessage>
    {
        public void received(PeerExchangeMessage message) throws IOException {
            gotContact();
            listener.peersDiscovered(message.getPeers(), Connection.this,
                message.isResponse());
        }
    }
    
    private File getOutputFile(String path) {
        String[] parts = path.split("/");
        return new File(storageFolder, parts[parts.length - 1]);
    }
    
    private class Request {
        private int id;
        private FutureTask<File> fileTask;
        private FutureTask<Resource> resourceTask;
        private String path;
        private boolean hypothetical;
        private long fileLength;
        private File outputFile;
        private RandomAccessFile outputAccess;
        private ByteBuffer outputBuffer;
        
        public Request(int id, String path, boolean hypothetical) {
            this.id = id;
            this.path = path;
            this.hypothetical = hypothetical;
            
            if (!hypothetical) {
                fileTask = new FutureTask<File>();
                resourceTask = null;
            } else {
                fileTask = null;
                resourceTask = new FutureTask<Resource>();
            }
            
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
         * Returns the request's file-yielding future task.
         * @return request's file-yielding future task
         */
        public FutureTask<File> getFileTask() {
            return fileTask;
        }
        
        /**
         * Returns the request's resource-yielding future task.
         * @return request's resource-yielding future task
         */
        public FutureTask<Resource> getResourceTask() {
            return resourceTask;
        }
        
        public void send() throws IOException {
            System.err.printf("requesting %s from %s%s%n", path,
                describeAddress(),
                (hypothetical ? " (hypothetically)" : ""));
            
            broker.send(new RequestMessage(id, path, hypothetical));
        }
        
        public void responseReceived(ResponseMessage message)
            throws IOException
        {
            if (message.getStatusCode() == 200) {
                System.out.printf("got OK for file %s from %s%n",
                    path, describeAddress());
                
                if (hypothetical) {
                    yieldResource(message);
                    return;
                }
                
                outputFile = getOutputFile(path);
                fileLength = message.getFileLength();
                outputAccess = new RandomAccessFile(outputFile, "rw");
                FileChannel channel = outputAccess.getChannel();
                outputBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0,
                    fileLength);
            } else {
                System.err.printf("got %s (%d) for file %s from %s%n",
                    message.getStatusDescription(), message.getStatusCode(),
                    path, describeAddress());
                IOException error = new IOException(String.format("%s (%d)",
                    message.getStatusDescription(), message.getStatusCode()));
                if (fileTask != null)
                    fileTask.setError(error);
                if (resourceTask != null)
                    resourceTask.setError(error);
                closeRequest(this);
            }
        }
        
        private void yieldResource(ResponseMessage message) {
            Resource resource = new Resource(path, message.getFileLength(),
                message.getContentType(), message.getDigest());
            resourceTask.set(resource);
            closeRequest(this);
        }
        
        public void payloadReceived(PayloadMessage message)
            throws IOException
        {
            outputBuffer.put(message.getBody());
            
            if (outputBuffer.position() >= fileLength) {
                System.out.printf("done receiving file %s%n", path);
                
                closeRequest(this);
                outputAccess.close();
                fileTask.set(outputFile);
            }
        }
        
        public String toString() {
            return String.format("<Request for %s from %s (%d)>",
                getPath(), describeAddress(), getID());
        }
    }
    
    private class Response implements MessageSource {
        private static final int CHUNK_SIZE = (1024 * 512) -
            Message.HEADER_LENGTH - PayloadMessage.OVERHEAD;
        
        private int id;
        private AvailableResource resource;
        private ResponseMessage initial;
        private ByteBuffer contents;
        
        public Response(int id, AvailableResource resource,
            boolean hypothetical) throws IOException
        {
            this.id = id;
            this.resource = resource;
            
            contents = resource.read();
            
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-1");
                digest.update(contents);
                contents.rewind();
            } catch (NoSuchAlgorithmException e) {
                digest = null;
            }
            
            initial = new ResponseMessage(id, (short) 200, "OK",
                resource.getSize(), resource.getContentType(),
                (digest != null ? digest.digest() : null));
            if (hypothetical)
                contents = null;
        }
        
        public Message next() {
            if (initial != null) {
                // Send the initial response message.
                Message nextMessage = initial;
                initial = null;
                return nextMessage;
            }
            
            if (contents == null || !contents.hasRemaining())
                return null;
            
            // Construct a new payload packet with the next chunk of the file.
            int remaining = contents.limit() - contents.position();
            byte[] dest = new byte[Math.min(remaining, CHUNK_SIZE)];
            contents.get(dest);
            
            return new PayloadMessage(id, contents.position(), dest);
        }
    }
}
