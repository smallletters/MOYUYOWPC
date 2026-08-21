import java.sql.*;
public class R3 {
  public static void main(String[] a) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/moyuyo_dev?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&user=root&password=dev123456");
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery("SELECT id, sender_type, sender_name, content FROM mo_cs_message ORDER BY id")) {
      while (rs.next()) {
        System.out.println(rs.getLong(1)+" | "+rs.getString(2)+" | "+rs.getString(3)+" | "+rs.getString(4));
      }
    }
  }
}
