package lab.cas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityStoreTest {
    DataSource ds;
    EntityStore store;

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource pg = new PGSimpleDataSource();
        pg.setURL("jdbc:postgresql://localhost:15435/mini");
        pg.setUser("mini");
        pg.setPassword("mini");
        ds = pg;

        try (Connection c = ds.getConnection()) {
            c.createStatement().execute("DROP TABLE IF EXISTS entities");
            c.createStatement().execute(
                    "CREATE TABLE entities (id BIGINT PRIMARY KEY, name TEXT NOT NULL, version INT NOT NULL) ");
        }
        store = new EntityStore(ds);
    }

    @Test
    void insertThenFind() throws Exception {
        store.insert(new Entity(1L, "orders", 1));

        Optional<Entity> found = store.find(1L);

        assertThat(found).contains((new Entity(1L, "orders", 1)));
    }

    @Test
    void findMissingReturnsEmpty() {
        assertThat(store.find(99L)).isEmpty();
    }

    @Test
    void updateSucceedsWhenVersionMatches() {
        store.insert(new Entity(1L, "orders", 1));
        store.update(new Entity(1L, "orders-v2", 2), 1);

        assertThat(store.find(1L)).contains(new Entity(1L, "orders-v2", 2));
    }

    @Test
    void updateFailsWhenVersionIsStale() {
        store.insert(new Entity(1L, "orders", 1));
        store.update(new Entity(1L, "a-wins", 2), 1);

        assertThatThrownBy(() -> store.update(new Entity(1L, "b-loses", 2), 1))
                .isInstanceOf(RetryOnConcurrencyException.class);

        assertThat(store.find(1L)).contains(new Entity(1L, "a-wins", 2));
    }


    @Test
    void updateWithOriginalSucceeds() {
        store.insert(new Entity(1L, "orders", 1));
        Entity original = store.find(1L).orElseThrow();

        store.update(original.withName("orders-v2").withNextVersion(), original);

        assertThat(store.find(1L)).contains(new Entity(1L, "orders-v2", 2));
    }

    @Test
    void updateWithStaleOriginalFails() {
        store.insert(new Entity(1L, "orders", 1));
        Entity a = store.find(1L).orElseThrow();
        Entity b = store.find(1L).orElseThrow();

        store.update(a.withName("a-wins").withNextVersion(), a);

        assertThatThrownBy(() -> store.update(b.withName("b-loses").withNextVersion(), b))
                .isInstanceOf(RetryOnConcurrencyException.class);
        assertThat(store.find(1L)).contains(new Entity(1L, "a-wins", 2));
    }

    @Test
    void insertThenFind2() {
        store.insert(new Entity(1L, "orders", 1));
        assertThat(store.find2(1L)).contains(new Entity(1L, "orders", 1));
    }

    @Test
    void find2MissingReturnsEmpty() {
        assertThat(store.find2(99L)).isEmpty();
    }

    @Test
    void transactionCommitsOnSuccess() {
        store.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO entities (id, name, version) VALUES (1, 'orders', 1)")) {
                return ps.executeUpdate();
            }
        });

        assertThat(store.find(1L)).contains(new Entity(1L, "orders", 1));
    }

    @Test
    void transactionRollsBackOnException() {
        assertThatThrownBy(() ->
                store.inTransaction(c -> {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO entities (id, name, version) VALUES (1, 'orders', 1)")) {
                        ps.executeUpdate();
                    }
                    throw new IllegalStateException("boom");
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(store.find(1L)).isEmpty();
    }


    @Test
    void twoConcurrentUpdatesOnlyOneWins() throws Exception {
        store.insert(new Entity(1L, "orders", 1));
        Entity a = store.find(1L).orElseThrow();
        Entity b = store.find(1L).orElseThrow();

        Callable<String> taskA = () -> {
            store.update(a.withName("a").withNextVersion(), a);
            return "a";
        };
        Callable<String> taskB = () -> {
            store.update(b.withName("b").withNextVersion(), b);
            return "b";
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<String>> futures = List.of(pool.submit(taskA), pool.submit(taskB));

        int wins = 0;
        int conflicts = 0;
        for (Future<String> f : futures) {
            try {
                System.out.println("won: " + f.get());
                wins++;
            } catch (ExecutionException e) {
                System.out.println("lost: " + e.getCause().getMessage());
                if (e.getCause() instanceof RetryOnConcurrencyException) {
                    conflicts++;
                }
            }
        }
        pool.shutdown();

        assertThat(wins).isEqualTo(1);
        assertThat(conflicts).isEqualTo(1);
        assertThat(store.find(1L).orElseThrow().version()).isEqualTo(2);
    }


    @Test
    void twoConcurrentUpdatesWithRetryBothWin() throws Exception {
        store.insert(new Entity(1L, "orders", 1));
        AtomicInteger attempts = new AtomicInteger();

        Callable<String> taskA = () -> Retry.retry(3, () -> {
            attempts.incrementAndGet();
            Entity cur = store.find(1L).orElseThrow();
            store.update(cur.withName(cur.name() + "+a").withNextVersion(), cur);
            return "a";
        });
        Callable<String> taskB = () -> Retry.retry(3, () -> {
            attempts.incrementAndGet();
            Entity cur = store.find(1L).orElseThrow();
            store.update(cur.withName(cur.name() + "+b").withNextVersion(), cur);
            return "b";
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<String>> futures = List.of(pool.submit(taskA), pool.submit(taskB));
        for (Future<String> f : futures) {
            System.out.println("done: " + f.get());
        }
        pool.shutdown();

        Entity result = store.find(1L).orElseThrow();
        System.out.println("result: " + result + " attempts: " + attempts.get());
        assertThat(result.version()).isEqualTo(3);
    }


}