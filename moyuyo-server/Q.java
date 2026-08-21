import java.sql.*;
public class Q {
  public static void main(String[] a) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/moyuyo_dev?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&user=root&password=dev123456");
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery("SELECT id,username,name,email,role,status,LENGTH(password) FROM mo_admin_user ORDER BY id")) {
      while (rs.next()) {
        System.out.println(rs.getLong(1)+" | "+rs.getString(2)+" | "+rs.getString(3)+" | "+rs.getString(4)+" | role="+rs.getString(5)+" | status="+rs.getString(6)+" | pwd_len="+rs.getInt(7));
      }
    }
  }
}
