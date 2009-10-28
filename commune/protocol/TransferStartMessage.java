package commune.protocol;

import java.nio.ByteBuffer;

public class TransferStartMessage extends Message {
    private static final short CODE = 0x20;
    
    private int serverID;
    private int transferID;
    private short port;
    
    public TransferStartMessage(int serverID, int transferID, short port) {
        super(CODE);
        this.serverID = serverID;
        this.transferID = transferID;
        this.port = port;
    }
    
    /**
     * Returns the server response identifier of the resource that should be
     * transferred.
     * @return server response identifier of the resource that should be
     *         transferred.
     */
    public int getServerID() {
        return serverID;
    }
    
    /**
     * Returns the key that the client will use to identify payload packets.
     * @return key that the client will use to identify payload packets
     */
    public int getTransferID() {
        return transferID;
    }
    
    /**
     * Returns the port to which the transfer should be sent.
     * @return port to which the transfer should be sent
     */
    public short getPort() {
        return port;
    }
    
    public ByteBuffer getBytes() {
        return formatMessage(getServerID(), getTransferID(), getPort());
    }
    
    static {
        Message.addParser(CODE, new MessageParser() {
            public Message parse(ByteBuffer buf, int length)
                throws InvalidMessageException
            {
                int serverID = buf.getInt();
                int transferID = buf.getInt();
                short port = buf.getShort();
                
                return new TransferStartMessage(serverID, transferID, port);
            }
        });
    }
}
