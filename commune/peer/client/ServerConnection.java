package commune.peer.client;

import commune.protocol.*;
import commune.peer.Reactor;
import commune.peer.Connection;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.Future;

public class ServerConnection extends Connection {
    private File storage;
    private long helloTime;
    private Map<Integer, Request> requests;
    private Map<Integer, TransferConnection> transfers;
    private int nextRequestID;
    private int nextTransferID;
    private InetAddress localAddress;
    
    public ServerConnection(Reactor reactor, SocketChannel channel,
        File storage) 
    {
        super(reactor, channel);
        this.storage = storage;
        helloTime = 0;
        
        localAddress = channel.socket().getLocalAddress();
        requests = new HashMap<Integer, Request>();
        transfers = new HashMap<Integer, TransferConnection>();
        nextRequestID = 0;
        nextTransferID = 0;
    }
    
    public void sayHello(Runnable onReceipt) throws IOException {
        System.err.printf("[client] sending hello to %s%n",
            getRemoteAddress());
        
        HelloMessage message = new HelloMessage("Commune Reference/0.2",
            false);
        sendMessage(message, new HelloListener(onReceipt));
    }
    
    public Future<File> request(String path) throws IOException {
        final FutureTask<File> task = new FutureTask<File>();
        int id = nextRequestID++;
        final Request request = new Request(id, path, task);
        requests.put(id, request);
        
        long now = System.currentTimeMillis();
        if (now - helloTime >= 60000L) {
            // Send a new "hello" if it's been at least 60 seconds since the
            // last one.
            
            sayHello(new Runnable() {
                public void run() {
                    try {
                        request.send();
                    } catch (IOException e) {
                        task.setError(e);
                    }
                }
            });
        } else {
            request.send();
        }
        
        return task;
    }
    
    private File getOutputFile(String path) {
        String[] parts = path.split("/");
        return new File(storage, parts[parts.length - 1]);
    }
    
    private void startTransfer(Request request, int serverID, long length) {
        try {
            DatagramChannel transferChannel = DatagramChannel.open();
            SocketAddress bindAddress = new InetSocketAddress(localAddress, 0);
            DatagramSocket transferSocket = transferChannel.socket();
            transferSocket.bind(bindAddress);
            int port = transferSocket.getLocalPort();
            
            System.err.printf("[client] ready to get transfer on %s%n",
                transferSocket.getLocalSocketAddress());
            
            int transferID = nextTransferID++;
            transferChannel.configureBlocking(false);
            
            TransferConnection tc = new TransferConnection(reactor,
                transferChannel, request.getTask(),
                getOutputFile(request.getPath()), length, transferID);
            
            sendMessage(new TransferStartMessage(serverID, transferID, port),
                false);
        } catch (IOException e) {
            request.getTask().setError(e);
        }
    }
    
    private class HelloListener extends MessageListener {
        private Runnable onReceipt;
        
        public HelloListener(Runnable onReceipt) {
            super(HelloMessage.CODE);
            this.onReceipt = onReceipt;
        }
        
        protected void received(Message bare) throws IOException {
            HelloMessage message = (HelloMessage) bare;
            System.err.printf("[client] got hello back from %s (%s)%n",
                getRemoteAddress(), message.getUserAgent());
            helloTime = System.currentTimeMillis();
            onReceipt.run();
        }
        
        protected void invalidMessage(InvalidMessageException e)
            throws IOException
        {
            System.err.printf("[client] invalid hello message received: %s%n",
                e.getMessage());
            try {
                channel.close();
            } catch (IOException ioe) {
                e.printStackTrace();
            }
        }
    }
    
    private class Request {
        private int id;
        private FutureTask<File> task;
        private String path;
        
        public Request(int id, String path, FutureTask<File> task) {
            this.id = id;
            this.path = path;
            this.task = task;
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
            
            RequestMessage message = new RequestMessage(id, path);
            sendMessage(message, new ResponseListener());
        }
        
        public void responseReceived(ResponseMessage message) {
            if (message.getStatusCode() == 200) {
                System.out.printf("[client] got OK for file %s from %s%n",
                    path, getRemoteAddress());
                startTransfer(this, message.getServerID(),
                    message.getFileLength());
            } else {
                System.err.printf("[client] got %s (%d) for file %s from %s%n",
                    message.getStatusDescription(), message.getStatusCode(),
                    path, getRemoteAddress());
                task.setError(new IOException(String.format("%s (%d)",
                    message.getStatusDescription(), message.getStatusCode())));
            }
        }
    }
    
    private class ResponseListener extends MessageListener {
        public ResponseListener() {
            super(ResponseMessage.CODE);
        }
        
        protected void received(Message bare) throws IOException {
            ResponseMessage message = (ResponseMessage) bare;
            
            Request request = requests.get(message.getClientID());
            if (request == null) {
                System.err.printf("[client] got response for unknown request" +
                    " %d%n", message.getClientID());
            } else {
                request.responseReceived(message);
            }
        }
        
        protected void invalidMessage(InvalidMessageException e)
            throws IOException
        {
            System.err.printf("[client] invalid response message received: " +
                "%s%n", e.getMessage());
            e.printStackTrace();
            try {
                channel.close();
            } catch (IOException ioe) {
                e.printStackTrace();
            }
        }
    }
}