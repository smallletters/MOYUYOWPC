import java.sql.*;

public class CheckCoupon {
    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/moyuyo_dev?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
                "root", "")) {
            // 查看表是否存在以及列
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("DESCRIBE mo_coupon")) {
                System.out.println("=== mo_coupon columns ===");
                while (rs.next()) {
                    System.out.printf("  %s %s%n", rs.getString(1), rs.getString(2));
                }
            }
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT id, code FROM mo_coupon ORDER BY id LIMIT 20")) {
                System.out.println("=== sample data ===");
                while (rs.next()) {
                    System.out.printf("  id=%s code=%s%n", rs.getString(1), rs.getString(2));
                }
            }
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) total, COUNT(DISTINCT id) d_id, COUNT(DISTINCT code) d_code FROM mo_coupon")) {
                if (rs.next()) {
                    System.out.printf("=== counts: total=%s distinct_id=%s distinct_code=%s%n",
                        rs.getString(1), rs.getString(2), rs.getString(3));
                }
            }
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT id, COUNT(*) c FROM mo_coupon GROUP BY id HAVING c > 1 LIMIT 5")) {
                System.out.println("=== duplicate ids ===");
                while (rs.next()) {
                    System.out.printf("  id=%s cnt=%s%n", rs.getString(1), rs.getString(2));
                }
            }
        }
    }
}
