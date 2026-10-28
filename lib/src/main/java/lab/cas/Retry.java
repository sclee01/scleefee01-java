package lab.cas;

import java.util.Optional;
import java.util.function.Supplier;

public class Retry {


    public static <T> T retry(int maxAttempts, Supplier<T> work) {
        for (int i = 0; i <= maxAttempts; i++) {
            try {
                return work.get();
            } catch (RetryOnConcurrencyException e) {
                if (i == maxAttempts - 1) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("");
    }
}
