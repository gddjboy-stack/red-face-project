package com.redface.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * 表格读取工具（C20-4B）。把 CSV 与 Excel 统一读成二维字符串。
 *
 * <p><b>为何全部按文本读取</b>：子订单编号是 18 位以上的长数字，若按数值读取，
 * Excel 与 POI 都会转成 double 并以科学计数法呈现，末几位直接变成 0。
 * 而子订单编号正是我们的幂等键——末位丢失会导致不同订单被判为同一笔，
 * 少算人气且极难察觉。日期同理，按数值读会得到 45000 这样的序列号。
 */
public final class SheetReader {

    private SheetReader() {
    }

    /**
     * 读取 CSV。支持 UTF-8 BOM 与双引号包裹字段内的逗号、换行。
     *
     * @param in 输入流
     * @return 二维字符串
     * @throws IOException 读取失败
     */
    public static List<List<String>> readCsv(InputStream in) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder field = new StringBuilder();
            List<String> current = new ArrayList<>();
            boolean inQuotes = false;
            boolean first = true;
            int c;
            while ((c = reader.read()) != -1) {
                char ch = (char) c;
                if (first) {
                    first = false;
                    if (ch == '\uFEFF') {
                        continue; // 跳过 UTF-8 BOM，否则首列表头名匹配不上
                    }
                }
                if (inQuotes) {
                    if (ch == '"') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next == '"') {
                            field.append('"'); // 转义的双引号
                        } else {
                            inQuotes = false;
                            if (next != -1) {
                                reader.reset();
                            }
                        }
                    } else {
                        field.append(ch);
                    }
                    continue;
                }
                switch (ch) {
                    case '"' -> inQuotes = true;
                    case ',' -> {
                        current.add(field.toString());
                        field.setLength(0);
                    }
                    case '\r' -> {
                        // 忽略，等 \n 处理换行
                    }
                    case '\n' -> {
                        current.add(field.toString());
                        field.setLength(0);
                        rows.add(current);
                        current = new ArrayList<>();
                    }
                    default -> field.append(ch);
                }
            }
            if (field.length() > 0 || !current.isEmpty()) {
                current.add(field.toString());
                rows.add(current);
            }
        }
        return rows;
    }

    /**
     * 读取 Excel（xls/xlsx）第一个工作表。
     *
     * @param in 输入流
     * @return 二维字符串
     * @throws IOException 读取失败
     */
    public static List<List<String>> readExcel(InputStream in) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            if (wb.getNumberOfSheets() == 0) {
                return rows;
            }
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            for (int i = 0; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    int lastCell = row.getLastCellNum();
                    for (int j = 0; j < lastCell; j++) {
                        cells.add(cellToString(row.getCell(j)));
                    }
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    /** 单元格转文本。数值一律走 BigDecimal 去尾零，绝不经过 double 的科学计数法。 */
    private static String cellToString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    yield numericToString(cell);
                }
            }
            case NUMERIC -> numericToString(cell);
            default -> "";
        };
    }

    private static String numericToString(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return new BigDecimal(String.valueOf(cell.getNumericCellValue()))
                .stripTrailingZeros().toPlainString();
    }
}
