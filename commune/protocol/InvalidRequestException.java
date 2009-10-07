package commune.protocol;

public class InvalidRequestException extends InvalidMessageException {
    public InvalidRequestException() {
        super();
    }
    
    public InvalidRequestException(String message) {
        super(message);
    }
    
    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
