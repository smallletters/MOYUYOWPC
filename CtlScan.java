import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class CtlScan {
  public static void main(String[] a) throws Exception {
    String root = "D:\\MOYUYOWPC\\moyuyo-server\\moyuyo-api\\src\\main\\java\\com\\moyuyo\\api\\controller";
    Map<String, List<String>> map = new TreeMap<>();
    Pattern cls = Pattern.compile("class\\s+(\\w+Controller)");
    Pattern req = Pattern.compile("@RequestMapping\\(\"([^\"]+)\"\\)");
    Pattern m = Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping\\(\"([^\"]*)\"");
    Files.walk(Paths.get(root)).filter(p -> p.toString().endsWith(".java")).forEach(p -> {
      try {
        String src = new String(Files.readAllBytes(p));
        Matcher mc = cls.matcher(src); if (!mc.find()) return;
        String c = mc.group(1);
        Matcher mr = req.matcher(src);
        String base = mr.find() ? mr.group(1) : "";
        List<String> paths = new ArrayList<>();
        Matcher mm = m.matcher(src);
        while (mm.find()) paths.add(mm.group(1) + " " + base + mm.group(2));
        map.put(c, paths);
      } catch (Exception e) { e.printStackTrace(); }
    });
    for (var e : map.entrySet()) {
      System.out.println("\n=== " + e.getKey() + " ===");
      e.getValue().stream().sorted().forEach(System.out::println);
    }
  }
}
