import java.sql.*;
public class R {
  public static void main(String[] a) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/moyuyo_dev?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&user=root&password=dev123456");
         Statement s = c.createStatement()) {
      // 手动执行 V20260820_01 的修复（Flyway 已经记录该 migration applied，不会重跑）
      int n = s.executeUpdate(
        "UPDATE mo_admin_role SET is_preset=1, update_time=NOW() " +
        "WHERE name IN ('超级管理员','运营管理员','客服人员','财务人员','数据查看员') " +
        "AND (is_preset=0 OR is_preset IS NULL)"
      );
      System.out.println("fix is_preset, n=" + n);
    }
  }
}
