import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
public class DebugDb3 {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/tanoshimi", "tanoshimi", "")) {
            Statement s = c.createStatement();
            ResultSet rs = s.executeQuery("DESCRIBE trip_schedule_items");
            while(rs.next()) {
                System.out.println(rs.getString("Field") + " - " + rs.getString("Type") + " - " + rs.getString("Default"));
            }
        }
    }
}
