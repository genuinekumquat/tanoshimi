package net.datasa.tanoshimi;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DebugDb {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/tanoshimi?serverTimezone=Asia/Seoul", "tanoshimi", "")) {
            Statement s = c.createStatement();
            ResultSet rs = s.executeQuery("SELECT id, title, status FROM activities");
            while(rs.next()) {
                System.out.println(rs.getInt(1) + " | " + rs.getString(2) + " | " + rs.getString(3));
            }
        }
    }
}
