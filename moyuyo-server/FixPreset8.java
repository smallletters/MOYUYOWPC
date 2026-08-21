import java.sql.*;
public class FixPreset8 {
  public static void main(String[] a) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    String url = "jdbc:mysql://localhost:3306/moyuyo_dev?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&user=root&password=dev123456";
    try (Connection c = DriverManager.getConnection(url);
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery("SELECT config_key, config_value FROM mo_system_config ORDER BY config_key")) {
      while (rs.next()) {
        System.out.println(rs.getString(1) + " = " + rs.getString(2));
      }
    }
  }
}
