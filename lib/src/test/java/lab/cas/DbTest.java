package lab.cas;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class DbTest {


    @Test
    void selectOne() throws Exception {
        String url = "jdbc:postgresql://localhost:15433/mini";
        try (Connection conn = DriverManager.getConnection(url,"mini","mini")) {
            ResultSet rs = conn.createStatement().executeQuery("select 1");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

}