package commune.protocol;

public class InvalidMessageException extends Exception {
    public InvalidMessageException() {
        super();
    }
    
    public InvalidMessageException(String message) {
        super(message);
    }
    
    public InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
