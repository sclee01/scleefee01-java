package lab.cas;

public class RetryOnConcurrencyException extends RuntimeException{

    public RetryOnConcurrencyException(String message) {
        super(message);
    }
}
