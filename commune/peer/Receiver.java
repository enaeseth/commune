package commune.peer;

import java.io.IOException;

public interface Receiver<T> {
    public void received(T message) throws IOException;
}
