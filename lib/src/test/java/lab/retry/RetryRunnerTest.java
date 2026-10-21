package lab.retry;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class RetryRunnerTest {


    @Test
    void runsOnceWhenFirstAttemptSucceeds() {
        AtomicInteger attempts = new AtomicInteger();

        RetryRunner.retry(3).run(() -> attempts.incrementAndGet());

        assertThat(attempts.get()).isEqualTo(1);
    }
}
