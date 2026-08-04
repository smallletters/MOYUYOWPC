import java.sql.*;

public class C2 {
  public static void main(String[] a) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection(
        "jdbc:mysql://localhost:3306/moyuyo_dev?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
        "root", "")) {
      print(c, "DESCRIBE mo_coupon");
      print(c, "SELECT COUNT(*) total, COUNT(DISTINCT id) d_id FROM mo_coupon");
      print(c, "SELECT id, COUNT(*) c FROM mo_coupon GROUP BY id HAVING c > 1");
      print(c, "SELECT code, COUNT(*) c FROM mo_coupon GROUP BY code HAVING c > 1 LIMIT 5");
      print(c, "SELECT id, code FROM mo_coupon WHERE code IS NULL OR code = '' LIMIT 5");
      print(c, "SELECT id, code FROM mo_coupon ORDER BY id LIMIT 10");
    }
  }
  static void print(Connection c, String sql) throws Exception {
    System.out.println("\n--- " + sql);
    try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
      int n = 0;
      while (rs.next()) {
        StringBuilder sb = new StringBuilder("  ");
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) sb.append(rs.getString(i)).append(" | ");
        System.out.println(sb);
        if (++n > 30) { System.out.println("  ...(truncated)"); break; }
      }
      if (n == 0) System.out.println("  (no rows)");
    }
  }
}
