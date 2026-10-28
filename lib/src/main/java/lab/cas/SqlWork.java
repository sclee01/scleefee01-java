package lab.cas;

import java.sql.Connection;
import java.sql.SQLException;

public interface SqlWork<R> {

    R apply(Connection c) throws SQLException;
}
