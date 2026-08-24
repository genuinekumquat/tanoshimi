import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckDb {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/tanoshimi?serverTimezone=Asia/Seoul", "tanoshimi", "")) {
            Statement s = c.createStatement();
            ResultSet rs = s.executeQuery("SELECT count(*) FROM activities");
            if(rs.next()) {
                System.out.println("Activities count: " + rs.getInt(1));
            }
        }
    }
}
