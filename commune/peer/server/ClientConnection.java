package commune.peer.server;

import commune.protocol.*;
import commune.peer.Reactor;
import commune.peer.Connection;
import commune.peer.source.AvailableResource;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

public class ClientConnection extends Connection {
    private Server server;
    private Map<Integer, AvailableResource> responses;
    private int nextResponseID;
    
    public ClientConnection(Server server, Reactor reactor,
        SocketChannel channel) throws IOException
    {
        super(reactor, channel);
        this.server = server;
        
        responses = new HashMap<Integer, AvailableResource>();
        nextResponseID = 0;
        
        reactor.register(channel, SelectionKey.OP_READ, new HelloListener());
    }
    
    private void sendResource(int id, SocketAddress destination,
        AvailableResource resource) throws IOException
    {
        DatagramChannel transferChannel = DatagramChannel.open();
        transferChannel.connect(destination);
        transferChannel.configureBlocking(false);
        
        System.out.printf("[server] starting transfer to %s%n",
            destination);
        
        TransferConnection tc = new TransferConnection(id, reactor,
            transferChannel, resource.read(), 10, 100, 0.9f);
        tc.queueNewData();
    }
    
    private class HelloListener extends MessageListener {
        public HelloListener() {
            super(HelloMessage.CODE);
        }
        
        protected void received(Message bare) throws IOException {
            HelloMessage message = (HelloMessage) bare;
            System.err.printf("[server] got hello from %s (%s)%n",
                getRemoteAddress(), message.getUserAgent());
            
            HelloMessage response = new HelloMessage("Commune Reference/0.2",
                true);
            sendMessage(response, new RequestListener());
        }
        
        protected void invalidMessage(InvalidMessageException e)
            throws IOException
        {
            System.err.printf("[server] invalid hello message received: %s%n",
                e.getMessage());
            try {
                channel.close();
            } catch (IOException ioe) {
                e.printStackTrace();
            }
        }
    }
    
    private class RequestListener extends MessageListener {
        public RequestListener() {
            super(RequestMessage.CODE);
        }
        
        protected void received(Message bare) throws IOException {
            RequestMessage message = (RequestMessage) bare;
            System.out.printf("[server] got request for %s from %s%n",
                message.getPath(), getRemoteAddress());
            
            ResponseMessage response;
            System.out.printf("[server] resource %s: ", message.getPath());
            AvailableResource resource = server.getResource(message.getPath());
            
            int serverID = nextResponseID++;
            if (resource != null) {
                System.out.println("OK!");
                
                response = new ResponseMessage(message.getID(), (short) 200,
                    "OK", resource.getSize(), resource.getContentType(),
                    serverID);
            } else {
                System.out.println("Not Found.");
                response = new ResponseMessage(message.getID(), (short) 404,
                    "Not Found");
            }
            
            responses.put(serverID, resource);
            
            sendMessage(response, new TransferStartListener());
        }
        
        protected void invalidMessage(InvalidMessageException e)
            throws IOException
        {
            System.err.printf("[server] invalid request message received:" +
                "%s%n", e.getMessage());
            try {
                channel.close();
            } catch (IOException ioe) {
                e.printStackTrace();
            }
        }
    }
    
    private class TransferStartListener extends MessageListener {
        public TransferStartListener() {
            super(TransferStartMessage.CODE);
        }
        
        protected void received(Message bare) throws IOException {
            TransferStartMessage message = (TransferStartMessage) bare;
            System.err.printf("[server] got transfer start request from %s%n",
                getRemoteAddress());
            
            AvailableResource resource = responses.get(message.getServerID());
            if (resource == null) {
                System.err.printf("[server] unknown response %d%n",
                    message.getServerID());
                return;
            }
            
            Socket sock = ((SocketChannel) channel).socket();
            InetSocketAddress remote =
                (InetSocketAddress) sock.getRemoteSocketAddress();
            InetSocketAddress transferAddr =
                new InetSocketAddress(remote.getAddress(), message.getPort());
            
            sendResource(message.getTransferID(), transferAddr, resource);
            
            reactor.register(channel, SelectionKey.OP_READ,
                new RequestListener(), 300);
        }
        
        protected void invalidMessage(InvalidMessageException e)
            throws IOException
        {
            System.err.printf("[server] invalid transfer start received: %s%n",
                e.getMessage());
            try {
                channel.close();
            } catch (IOException ioe) {
                e.printStackTrace();
            }
        }
    }
}