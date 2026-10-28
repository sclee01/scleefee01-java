package lab.cas;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

public class EntityStore {

    private final DataSource ds;

    private static final String INSERT_SQL = "INSERT INTO entities (id, name, version) VALUES (?, ?, ?)";

    private static final String FIND_SQL = "SELECT id, name, version FROM entities WHERE id = ?";

    private static final String UPDATE_SQL = "UPDATE entities SET name = ?, version = ? WHERE id = ? AND version = ?";

    public EntityStore(DataSource ds) {
        this.ds = ds;
    }

    private <T> T withConnection(SqlWork<T> work) {
        try (Connection connection = ds.getConnection()) {
            return work.apply(connection);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Entity> find2(Long id) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(FIND_SQL)) {
                ps.setLong(1, id);
                try (ResultSet resultSet = ps.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    long id1 = resultSet.getLong("id");
                    String name = resultSet.getString("name");
                    int value = resultSet.getInt("version");
                    return Optional.of(new Entity(id1, name, value));
                }
            }
        });
    }

    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.apply(c);
                c.commit();
                return result;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public void insert(Entity entity) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {
            ps.setLong(1, entity.id());
            ps.setString(2, entity.name());
            ps.setInt(3, entity.version());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Entity> find(long id) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(FIND_SQL);) {
            ps.setLong(1, id);
            try (ResultSet resultSet = ps.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                long id1 = resultSet.getLong("id");
                String name = resultSet.getString("name");
                int value = resultSet.getInt("version");
                return Optional.of(new Entity(id1, name, value));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void update(Entity entity, int expectedVersion) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(UPDATE_SQL)) {
            ps.setString(1, entity.name());
            ps.setInt(2, entity.version());
            ps.setLong(3, entity.id());
            ps.setInt(4, expectedVersion);
            int rowUpdated = ps.executeUpdate();

            if (rowUpdated == 0) {
                throw new RetryOnConcurrencyException(
                        "id=" + entity.id() + " expected version=" + expectedVersion);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void update(Entity updated, Entity original) {
        update(updated, original.version());
    }
}
