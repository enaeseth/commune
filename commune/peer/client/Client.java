package commune.peer.client;

import commune.peer.Reactor;
import commune.peer.ChannelListener;
import commune.protocol.*;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.concurrent.Future;

public class Client {
    private Reactor reactor;
    private File storage;
    
    public Client(Reactor reactor, File storageDirectory) {
        this.reactor = reactor;
        storage = storageDirectory;
    }
    
    public Future<File> request(String host, int port, String path)
        throws IOException
    {
        InetAddress address = InetAddress.getByName(host);
        SocketChannel channel = SocketChannel.open();
        FutureTask<File> task = new FutureTask<File>();
        
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(address, port));
        
        reactor.register(channel, SelectionKey.OP_CONNECT,
            new ServerListener(task, path), 15);
        return task;
    }
    
    private class ServerListener implements ChannelListener {
        private FutureTask<File> task;
        private String path;
        private boolean requestSent = false;
        private boolean responseRead = false;
        private ByteBuffer buffer = null;
        private int fileLength = 0;
        private int bytesRead = 0;
        private File outputFile = null;
        private FileChannel fileChannel = null;
        private ByteBuffer fileBuffer = null;
        
        public ServerListener(FutureTask<File> task, String path) {
            this.task = task;
            this.path = path;
        }
        
        public void ready(SelectableChannel channel, int operations)
            throws IOException
        {
            SocketChannel socket = (SocketChannel) channel;
            try {
                if ((operations & SelectionKey.OP_CONNECT) > 0) {
                    socket.finishConnect();
                    reactor.register(channel, SelectionKey.OP_WRITE,
                        this, 10);
                } else if ((operations & SelectionKey.OP_WRITE) > 0) {
                    if (!requestSent) {
                        sendRequest(socket);
                    } else {
                        sendAcknowledgement(socket);
                    }
                } else if ((operations & SelectionKey.OP_READ) > 0) {
                    if (!responseRead) {
                        readResponse(socket);
                    } else {
                        readResource(socket);
                    }
                } else {
                    // wtf?
                    socket.close();
                }
            } catch (IOException e) {
                task.setError(e);
                throw e;
            }
        }
        
        private void sendRequest(SocketChannel socket) throws IOException {
            if (buffer == null) {
                Request request = new Request("GET", path);
                request.addHeader("User-Agent", "Commune Reference/0.1");
                String requestString = request.toString();
                buffer = ByteBuffer.wrap(requestString.getBytes("UTF-8"));
            }
            
            if (buffer.hasRemaining()) {
                socket.write(buffer);
            } else {
                requestSent = true;
                buffer = null;
                reactor.register(socket, SelectionKey.OP_READ,
                    this, 20);
            }
        }
        
        private void readResponse(SocketChannel socket) throws IOException {
            if (buffer == null) {
                buffer = ByteBuffer.allocate(8192);
            }
            
            socket.read(buffer);
            int responseLength = findEndOfResponse();
            if (responseLength < 0) {
                // we haven't yet gotten the whole response
                
                if (buffer.limit() >= buffer.capacity()) {
                    // and we never will, because our response buffer is full.
                    // close the connection; a header of more than 8KiB is
                    // probably some kind of attack anyway.
                    System.err.printf("response head from %s was too large; " +
                        "dropping connection%n",
                        socket.socket().getRemoteSocketAddress());
                    socket.close();
                }
                
                return;
            }
            
            byte[] data = new byte[responseLength];
            buffer.rewind();
            buffer.get(data);
            
            try {
                Response response = Response.parse(data);
                
                String lengthString =
                    response.getFirstHeader("Content-Length");
                if (lengthString == null)
                    throw new IOException("No Content-Length received.");
                
                fileLength = Integer.parseInt(lengthString);
                buffer = null;
            } catch (InvalidResponseException e) {
                // The server's response was invalid.
                throw new IOException(e);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid Content-Length received.");
            }
        }
        
        private void readResource(SocketChannel socket) throws IOException {
            if (buffer == null) {
                outputFile = getOutputFile();
                FileOutputStream stream = new FileOutputStream(outputFile);
                fileChannel = stream.getChannel();
                fileBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE,
                    0, fileLength);
            }
            
            bytesRead += socket.read(fileBuffer);
            if (bytesRead >= fileLength) {
                fileChannel.close();
                reactor.register(socket, SelectionKey.OP_WRITE,
                    this, 10);
            }
        }
        
        private void sendAcknowledgement(SocketChannel socket)
            throws IOException
        {
            if (buffer == null) {
                Response response = new Response(202, "Accepted");
                String responseString = response.toString();
                buffer = ByteBuffer.wrap(responseString.getBytes("UTF-8"));
            }
            
            if (buffer.hasRemaining()) {
                socket.write(buffer);
            } else {
                task.set(outputFile);
                socket.close();
            }
        }
        
        private int findEndOfResponse() {
            byte b;
            int limit = buffer.limit();
            for (int i = 0; i < (limit - 1); i++) {
                b = buffer.get(i);
                if (b == '\n' && buffer.get(i + 1) == '\n')
                    return i;
                if (b == '\r' && i < (limit - 3) && buffer.get(i + 3) == '\n')
                    return i;
            }

            return -1;
        }
        
        private File getOutputFile() {
            String[] parts = path.split("/");
            return new File(storage, parts[parts.length - 1]);
        }
    }
}
