import java.sql.*;
public class FixPreset6 {
  public static void main(String[] a) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    String url = "jdbc:mysql://localhost:3306/moyuyo_dev?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&user=root&password=dev123456";
    try (Connection c = DriverManager.getConnection(url);
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery("SELECT id, name, description FROM mo_admin_role WHERE id=1")) {
      while (rs.next()) {
        System.out.println("id=" + rs.getLong(1) + " | name=" + rs.getString(2) + " | desc=" + rs.getString(3));
      }
    }
  }
}
