package commune.protocol;

public class InvalidResponseException extends InvalidMessageException {
    public InvalidResponseException() {
        super();
    }
    
    public InvalidResponseException(String message) {
        super(message);
    }
    
    public InvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
