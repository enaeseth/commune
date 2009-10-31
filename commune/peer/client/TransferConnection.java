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

public class TransferConnection extends Connection {
    private FutureTask<File> task;
    private File output;
    private long fileLength;
    private int id;
    private long lastReceived;
    private long offset;
    private FileChannel fileChannel;
    private ByteBuffer fileBuffer;
    
    public TransferConnection(Reactor reactor, DatagramChannel channel,
        FutureTask<File> task, File output, long fileLength, int id)
        throws IOException
    {
        super(reactor, channel);
        
        fileChannel = new RandomAccessFile(output, "rw").getChannel();
        fileBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE,
            0, fileLength);
        
        this.task = task;
        this.output = output;
        this.fileLength = fileLength;
        this.id = id;
        this.lastReceived = 0L;
        this.offset = 0L;
        
        reactor.register(channel, SelectionKey.OP_READ, new PayloadListener());
    }
    
    private class PayloadListener extends DatagramMessageListener {
        public PayloadListener() {
            super(PayloadMessage.CODE);
        }
        
        protected void received(Message bare, SocketAddress src)
            throws IOException
        {
            PayloadMessage message = (PayloadMessage) bare;
            
            if (message.getTransferID() != id) {
                System.err.printf("[client] got payload with unexpected " +
                    "transfer ID %d%n", message.getTransferID());
                return;
            }
            
            // System.err.printf("\t\t%d%n", message.getOffset());
            
            if (message.getOffset() != offset) {
                // System.err.printf("[client] got payload with offset %d; was " +
                    // "expecting %d%n", message.getOffset(), offset);
                sendDatagramMessage(new AcknowledgementMessage(id,
                    lastReceived), src, this);
                return;
            } else {
                // System.err.printf("[client] got payload with correct " +
                //     "offset %d%n", offset);
            }
            
            ByteBuffer body = ByteBuffer.wrap(message.getBody());
            while (body.hasRemaining()) {
                fileBuffer.put(body);
            }
            
            AcknowledgementMessage ack = new AcknowledgementMessage(id,
                offset);
            lastReceived = offset;
            offset += body.limit();
            
            System.out.printf("%d / %d (%.02f%%)\r",
                offset, fileLength, ((double) offset) / fileLength * 100.0);
            
            // System.err.printf("[client] next expected offset = %d%n", offset);
            
            if (offset >= fileLength) {
                System.out.printf("%nDone!%n");
                try {
                    fileChannel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
                System.out.printf("[client] finished transfer (%d bytes)%n",
                    offset);
                task.set(output);
                sendDatagramMessage(ack, src, null);
            } else {
                sendDatagramMessage(ack, src, this);
            }
        }
        
        protected void invalidMessage(InvalidMessageException e)
            throws IOException
        {
            System.err.printf("[client] invalid payload message received: " +
                "%s%n", e.getMessage());
            task.setError(e);
            try {
                channel.close();
            } catch (IOException ce) {
                ce.printStackTrace();
            }
        }
    }
}
