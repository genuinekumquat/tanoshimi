import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
public class DebugDb2 {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/tanoshimi", "tanoshimi", "")) {
            Statement s = c.createStatement();
            ResultSet rs = s.executeQuery("SELECT id, status FROM trip_schedules");
            while(rs.next()) {
                System.out.println("Schedule ID=" + rs.getInt(1) + " status=" + rs.getString(2));
            }
        }
    }
}
