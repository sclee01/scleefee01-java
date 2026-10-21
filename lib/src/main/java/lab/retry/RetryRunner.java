package lab.retry;

public class RetryRunner {

    private final int maxAttempts;

    private Class<? extends RuntimeException> retryOn;

    private RetryRunner(int maxAttempts) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, but was " + maxAttempts);
        }
        this.maxAttempts = maxAttempts;
    }

    public static RetryRunner retry(int maxAttempts) {
        return new RetryRunner(maxAttempts);
    }

    public void run(Runnable task) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                task.run();
                return;
            } catch (RuntimeException e) {
                if (retryOn != null && !retryOn.isInstance(e)) {
                    throw e;
                }
                if (attempt + 1 == maxAttempts) {
                    throw e;
                }
            }
        }
    }

    public RetryRunner onlyRetryOn(Class<? extends RuntimeException> retryOn) {
        this.retryOn = retryOn;
        return this;
    }

}
