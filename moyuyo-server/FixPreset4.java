import java.sql.*;
public class FixPreset4 {
  public static void main(String[] a) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    String url = "jdbc:mysql://localhost:3306/moyuyo_dev?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&user=root&password=dev123456";
    try (Connection c = DriverManager.getConnection(url)) {
      try (Statement s = c.createStatement();
           ResultSet rs = s.executeQuery("SELECT id, name, code, is_preset FROM mo_admin_role ORDER BY id")) {
        while (rs.next()) System.out.println(rs.getLong(1) + " | " + rs.getString(2) + " | " + rs.getString(3) + " | preset=" + rs.getInt(4));
      }
    }
  }
}
