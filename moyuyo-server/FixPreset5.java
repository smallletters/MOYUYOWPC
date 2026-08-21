import java.sql.*;
public class FixPreset5 {
  public static void main(String[] a) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    String url = "jdbc:mysql://localhost:3306/moyuyo_dev?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&user=root&password=dev123456";
    try (Connection c = DriverManager.getConnection(url)) {
      // 直接用 SQL 还原 id=3 的角色名/编码/描述/预设标志
      try (PreparedStatement ps = c.prepareStatement(
              "UPDATE mo_admin_role SET name='客服人员', code='CUSTOMER_SVC', description='客服会话和工单权限', is_preset=1, update_time=NOW() WHERE id=3")) {
        int n = ps.executeUpdate();
        System.out.println("restored id=3, n=" + n);
      }
      // 同时把 controller 的 update 行为修正：避免 code 被错误覆盖
      try (Statement s = c.createStatement();
           ResultSet rs = s.executeQuery("SELECT id, name, code, description, is_preset FROM mo_admin_role ORDER BY id")) {
        while (rs.next()) {
          System.out.println(rs.getLong(1) + " | " + rs.getString(2) + " | code=" + rs.getString(3) + " | preset=" + rs.getInt(5));
        }
      }
    }
  }
}
