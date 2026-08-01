package com.redface;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 两份 schema 一致性校验。
 *
 * <p>本项目有两份建表脚本：{@code db/db_schema.sql}（生产 MySQL）与
 * {@code src/test/resources/schema-h2.sql}（测试 H2）。两者靠人工同步。
 *
 * <p>C20-4B 期间发现 {@code players.display_code} 只存在于 MySQL 版，H2 版缺失，
 * 而当时全量 154 项测试<b>全部通过</b>——因为没有任何测试碰过这一列。这意味着
 * 「测试全绿」并不能证明两份 schema 一致，漏项会一直潜伏到某个功能真正用到它为止；
 * 若那个时刻发生在 8/9 现场，代价是整场结算不可用。
 *
 * <p>本测试把这条隐式假设变成显式断言：任何一方新增或漏加列都会立即失败。
 * 它不连数据库，只做文本比对，因此不受运行环境影响。
 */
class SchemaParityC20Test {

    /** 定位仓库根目录。测试的工作目录是 backend/redface-backend。 */
    private Path repoRoot() {
        Path cur = Paths.get("").toAbsolutePath();
        while (cur != null && !Files.exists(cur.resolve("db/db_schema.sql"))) {
            cur = cur.getParent();
        }
        if (cur == null) {
            throw new IllegalStateException("未能定位仓库根目录（找不到 db/db_schema.sql）");
        }
        return cur;
    }

    @Test
    @DisplayName("MySQL 与 H2 两份 schema 的表与列必须完全一致")
    void twoSchemasMustMatch() throws IOException {
        Path root = repoRoot();
        Map<String, Set<String>> mysql = parse(root.resolve("db/db_schema.sql"));
        Map<String, Set<String>> h2 = parse(
                root.resolve("backend/redface-backend/src/test/resources/schema-h2.sql"));

        List<String> problems = new ArrayList<>();

        Set<String> onlyInMysql = new TreeSet<>(mysql.keySet());
        onlyInMysql.removeAll(h2.keySet());
        for (String t : onlyInMysql) {
            problems.add("表「" + t + "」只存在于生产 MySQL schema，测试 H2 schema 缺失"
                    + "——依赖该表的功能在测试环境无法验证");
        }

        Set<String> onlyInH2 = new TreeSet<>(h2.keySet());
        onlyInH2.removeAll(mysql.keySet());
        for (String t : onlyInH2) {
            problems.add("表「" + t + "」只存在于测试 H2 schema，生产 MySQL schema 缺失"
                    + "——上线即报错");
        }

        Set<String> common = new TreeSet<>(mysql.keySet());
        common.retainAll(h2.keySet());
        for (String t : common) {
            Set<String> mc = new TreeSet<>(mysql.get(t));
            Set<String> hc = new TreeSet<>(h2.get(t));

            Set<String> missingInH2 = new TreeSet<>(mc);
            missingInH2.removeAll(hc);
            if (!missingInH2.isEmpty()) {
                problems.add("表「" + t + "」的列 " + missingInH2
                        + " 只在生产 MySQL 有，测试 H2 缺失——相关功能测试跑不起来"
                        + "，但全量测试可能仍显示为绿");
            }

            Set<String> missingInMysql = new TreeSet<>(hc);
            missingInMysql.removeAll(mc);
            if (!missingInMysql.isEmpty()) {
                problems.add("表「" + t + "」的列 " + missingInMysql
                        + " 只在测试 H2 有，生产 MySQL 缺失——测试通过但生产必挂");
            }
        }

        assertTrue(problems.isEmpty(),
                "两份 schema 不一致，共 " + problems.size() + " 处：\n  - "
                        + String.join("\n  - ", problems));
    }

    /**
     * 从建表脚本中提取「表名 → 列名集合」。
     *
     * <p>只取列定义，忽略 PRIMARY KEY / UNIQUE KEY / KEY / CONSTRAINT 等约束行，
     * 因为两份脚本在索引写法上本就存在合理差异（如 MySQL 的 ENGINE、COMMENT），
     * 强行比对索引会产生大量噪音而掩盖真正的列级漏项。
     */
    private Map<String, Set<String>> parse(Path file) throws IOException {
        String sql = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, Set<String>> result = new LinkedHashMap<>();

        Pattern create = Pattern.compile(
                "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(\\w+)`?\\s*\\(",
                Pattern.CASE_INSENSITIVE);
        Matcher m = create.matcher(sql);
        while (m.find()) {
            String table = m.group(1).toLowerCase();
            int bodyStart = m.end();
            int depth = 1;
            int i = bodyStart;
            while (i < sql.length() && depth > 0) {
                char c = sql.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                }
                i++;
            }
            String body = sql.substring(bodyStart, Math.max(bodyStart, i - 1));
            result.put(table, extractColumns(body));
        }
        return result;
    }

    private Set<String> extractColumns(String body) {
        Set<String> cols = new LinkedHashSet<>();
        int depth = 0;
        // MySQL 版的 COMMENT 字符串里含逗号（如 '±10代表±0.1, pk_win为+5'），
        // 若不识别引号就按逗号切分，会把注释片段误判成新列名。
        boolean inQuote = false;
        StringBuilder cur = new StringBuilder();
        List<String> segments = new ArrayList<>();
        for (char c : body.toCharArray()) {
            if (c == '\'') {
                inQuote = !inQuote;
            }
            if (!inQuote) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                }
            }
            if (c == ',' && depth == 0 && !inQuote) {
                segments.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        segments.add(cur.toString());

        for (String seg : segments) {
            // 去掉行注释，避免注释里的词被当成列名
            StringBuilder cleaned = new StringBuilder();
            for (String line : seg.split("\n")) {
                int idx = line.indexOf("--");
                cleaned.append(idx >= 0 ? line.substring(0, idx) : line).append(' ');
            }
            String s = cleaned.toString().trim();
            if (s.isEmpty()) {
                continue;
            }
            String upper = s.toUpperCase();
            if (upper.startsWith("PRIMARY KEY") || upper.startsWith("UNIQUE KEY")
                    || upper.startsWith("UNIQUE ") || upper.startsWith("KEY ")
                    || upper.startsWith("INDEX ") || upper.startsWith("CONSTRAINT")
                    || upper.startsWith("FOREIGN KEY") || upper.startsWith("CHECK")) {
                continue;
            }
            Matcher cm = Pattern.compile("^`?(\\w+)`?").matcher(s);
            if (cm.find()) {
                cols.add(cm.group(1).toLowerCase());
            }
        }
        return cols;
    }
}
