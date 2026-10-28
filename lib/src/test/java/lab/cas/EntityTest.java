package lab.cas;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTest {


    @Test
    void withNextVersionReturnsNewObjectAndKeepsOriginal() {
        Entity original = new Entity(1L, "orders", 1);

        Entity next = original.withNextVersion();

        assertThat(next).isEqualTo(new Entity(1L, "orders", 2));
        assertThat(original.version()).isEqualTo(1);
        assertThat(next).isNotSameAs(original);
    }
}
