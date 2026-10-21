package lab.retry;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class RetryRunnerTest {


    @Test
    void runsOnceWhenFirstAttemptSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        RetryRunner.retry(3).run(() -> attempts.incrementAndGet());
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void retriesUntilSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        RetryRunner.retry(3).run(() -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new IllegalStateException();
                    }
                }
        );
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void throwsLastExceptionWhenAllAtemptsFail() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() ->
                RetryRunner.retry(3).run(() -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("boom " + attempts.get());
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom 3");
    }

    @Test
    void doesNotRetryOtherExceptions() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() ->
                RetryRunner.retry(3)
                        .onlyRetryOn(IllegalStateException.class)
                        .run(() -> {
                            attempts.incrementAndGet();
                            throw new IllegalArgumentException("bad input");   // 다른 예외
                        }))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }


    @Test
    void rejectNonPositiveMaxAttempts() {
        assertThatThrownBy(() -> RetryRunner
                .retry(0)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
