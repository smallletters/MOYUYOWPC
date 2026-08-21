import java.sql.*;
public class Q2 {
  public static void main(String[] a) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/moyuyo_dev?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&user=root&password=dev123456");
         Statement s = c.createStatement()) {
      int n = s.executeUpdate("DELETE FROM mo_admin_user WHERE username='qa_mgr_real'");
      System.out.println("清理 qa_mgr_real，影响行数=" + n);
    }
  }
}
