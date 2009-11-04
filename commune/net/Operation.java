package commune.net;

import java.nio.channels.SelectionKey;

public enum Operation {
    READ(SelectionKey.OP_READ),
    WRITE(SelectionKey.OP_WRITE),
    CONNECT(SelectionKey.OP_CONNECT),
    ACCEPT(SelectionKey.OP_ACCEPT);
    
    private final int selectorOperation;
    
    Operation(int selectorOperation) {
        this.selectorOperation = selectorOperation;
    }
    
    public int selectorOperation() {
        return selectorOperation;
    }
}
