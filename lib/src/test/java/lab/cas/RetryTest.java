package lab.cas;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class RetryTest {

    @Test
    void succeedsAfterTwoConflicts() {
        AtomicInteger calls = new AtomicInteger();

        String result = Retry.retry(3, () -> {
            if (calls.incrementAndGet() < 3) {
                throw new RetryOnConcurrencyException("conflict");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> Retry.retry(3, () -> {
            calls.incrementAndGet();
            throw new RetryOnConcurrencyException("conflict");
        })).isInstanceOf(RetryOnConcurrencyException.class);

        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryOtherExceptions() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> Retry.retry(3, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("db down");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(calls.get()).isEqualTo(1);
    }


}
