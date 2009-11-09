package commune.net;

import java.nio.channels.SelectionKey;

public enum Operation {
    /**
     * Data is available to be read.
     */
    READ(SelectionKey.OP_READ),
    
    /**
     * The channel is ready for data to be written to it.
     */
    WRITE(SelectionKey.OP_WRITE),
    
    /**
     * The channel has been connected.
     */
    CONNECT(SelectionKey.OP_CONNECT),
    
    /**
     * A new connection is available to be accepted.
     */
    ACCEPT(SelectionKey.OP_ACCEPT);
    
    private final int selectorOperation;
    
    Operation(int selectorOperation) {
        this.selectorOperation = selectorOperation;
    }
    
    public int selectorOperation() {
        return selectorOperation;
    }
}
