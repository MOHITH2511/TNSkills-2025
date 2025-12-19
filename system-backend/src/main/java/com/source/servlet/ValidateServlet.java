package com.bellecroissant.servlet;

import com.bellecroissant.util.DBConnection;

import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

@WebServlet("/validate-trip-logs")
@MultipartConfig
public class ValidateOrderItemsServlet extends HttpServlet {

    private static final String FUTURE_DATE = "FUTURE_DATE";
    private static final String INVALID_ORDER_ID = "INVALID_ORDER_ID";
    private static final String PARSE_ERROR = "PARSE_ERROR";

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        
        System.out.println("Content-Type: " + req.getContentType());

        resp.setContentType("application/json");

        List<String> validRows = new ArrayList<>();
        Map<String, String> invalidRows = new LinkedHashMap<>();

        try {
            String action = req.getParameter("action"); 

            if ("status".equalsIgnoreCase(action)) {
                try (Connection conn = DBConnection.getConnection();
                     Statement st = conn.createStatement()) {
                    ResultSet rs = st.executeQuery("SELECT COUNT(*) AS cnt FROM order_items");
                    int cnt = 0;
                    if (rs.next()) cnt = rs.getInt("cnt");

                    PrintWriter out = resp.getWriter();
                    out.println("{");
                    out.println("\"count\": " + cnt + ",");
                    out.println("\"sample\": [");

                    ResultSet rs2 = st.executeQuery("SELECT order_id, product_name, price, quantity, item_date FROM order_items ORDER BY item_date DESC LIMIT 10");
                    boolean first = true;
                    while (rs2.next()) {
                        if (!first) out.println(",");
                        first = false;
                        out.println("{\"order_id\": " + rs2.getInt("order_id") + ", \"product_name\": \"" + rs2.getString("product_name") + "\", \"price\": " + rs2.getBigDecimal("price") + ", \"quantity\": " + rs2.getInt("quantity") + ", \"item_date\": \"" + rs2.getDate("item_date") + "\"}");
                    }

                    out.println("]");
                    out.println("}");
                } catch (Exception ex) {
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    resp.getWriter().println("{\"error\": \"status query failed: " + ex.getMessage() + "\"}");
                }
                return;
            }
            if ("insert".equalsIgnoreCase(action)) {
                File cleaned = new File("D:/zoho/belle_croissant/output/cleaned-order-item.csv");
                if (!cleaned.exists()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("{\"error\":\"cleaned CSV not found. Run validation first.\"}");
                    return;
                }

                List<String> rows = new ArrayList<>();
                try (BufferedReader r = new BufferedReader(new FileReader(cleaned))) {
                    String l;
                    int cnt = 0;
                    while ((l = r.readLine()) != null) {
                        cnt++;
                        if (cnt == 1) continue;
                        l = l.trim();
                        if (l.isEmpty()) continue;
                        rows.add(l);
                    }
                }

                int inserted = 0;
                File outputDir = new File("D:/zoho/belle_croissant/output");
                File insertLog = new File(outputDir, "insert-log.txt");
                try {
                    inserted = bulkInsertOrderItems(rows);
                    try (PrintWriter pw = new PrintWriter(new FileWriter(insertLog, true))) {
                        pw.println("Timestamp: " + java.time.LocalDateTime.now());
                        pw.println("Inserted rows: " + inserted);
                        pw.println("Source cleaned rows: " + rows.size());
                        pw.println("----");
                    }
                } catch (Exception ex) {
                    try (PrintWriter pw = new PrintWriter(new FileWriter(insertLog, true))) {
                        pw.println("Timestamp: " + java.time.LocalDateTime.now());
                        pw.println("Insert FAILED");
                        pw.println("Source cleaned rows: " + rows.size());
                        pw.println("Error: " + ex.toString());
                        StringWriter sw = new StringWriter();
                        ex.printStackTrace(new PrintWriter(sw));
                        pw.println(sw.toString());
                        pw.println("----");
                    } catch (Exception ignore) {
                    }

                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    resp.getWriter().println('{'+"\"error\":\"Insert failed: " + ex.getMessage().replaceAll("\"","\\\"") + "\"}" );
                    return;
                }

                PrintWriter out = resp.getWriter();
                out.println("{");
                out.println("\"insertedCount\": " + inserted);
                out.println("}");
                return;
            }

            Part filePart = req.getPart("file");

            if (filePart == null || filePart.getSize() == 0) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "CSV file missing");
                return;
            }

            System.out.println("file size = " + filePart.getSize());
            System.out.println("file name = " + filePart.getSubmittedFileName());

            Set<Integer> validOrderIds = loadValidOrderIds();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(filePart.getInputStream(), "UTF-8")
            );

            String line;
            int lineCount = 0;

            int priceGt500Count = 0;
            int futureDateCount = 0;
            int invalidOrderIdCount = 0;
            int parseErrorCount = 0;

            while ((line = reader.readLine()) != null) {
                lineCount++;

                line = line.trim();
                if (line.isEmpty()) continue;

                System.out.println("LINE " + lineCount + ": " + line);

                if (lineCount == 1) continue;

                try {
                    String[] cols = line.split(",");

                    if (cols.length < 5) {
                        invalidRows.put(line, PARSE_ERROR);
                        continue;
                    }

                    int orderId = Integer.parseInt(cols[0].trim());
                    LocalDate itemDate = LocalDate.parse(cols[4].trim());

                 
                    try {
                        java.math.BigDecimal price = new java.math.BigDecimal(cols[2].trim());
                        if (price.compareTo(new java.math.BigDecimal("500")) > 0) {
                            priceGt500Count++;
                        }
                    } catch (Exception ignore) {
                    }

                    if (itemDate.isAfter(LocalDate.now())) {
                        invalidRows.put(line, FUTURE_DATE);
                        futureDateCount++;
                    } else if (!validOrderIds.contains(orderId)) {
                        invalidRows.put(line, INVALID_ORDER_ID);
                        invalidOrderIdCount++;
                    } else {
                        validRows.add(line);
                    }

                } catch (Exception ex) {
                    invalidRows.put(line, PARSE_ERROR);
                    parseErrorCount++;
                }
            }

            System.out.println("TOTAL LINES READ = " + lineCount);

            File cleanedCsv = generateCleanedCsv(validRows);
            System.out.println("Cleaned CSV generated at: " + cleanedCsv.getAbsolutePath());

            int inserted = 0;

            File outputDir = cleanedCsv.getParentFile();
            if (outputDir != null) {
                File task1 = new File(outputDir, "Task1.txt");
                try (PrintWriter pw = new PrintWriter(new FileWriter(task1))) {
                    pw.println("Task: Price Analysis");
                    pw.println("Metric: Count of items with price > 500");
                    pw.println("Result: " + priceGt500Count);
                }

                File task2 = new File(outputDir, "Task2.txt");
                try (PrintWriter pw = new PrintWriter(new FileWriter(task2))) {
                    pw.println("Task: Data Quality Report");
                    pw.println("Future date rows: " + futureDateCount);
                    pw.println("Invalid order ID rows: " + invalidOrderIdCount);
                    pw.println("Parse error rows: " + parseErrorCount);
                }

                File invalidCsv = new File(outputDir, "invalid-rows.csv");
                try (PrintWriter pw = new PrintWriter(new FileWriter(invalidCsv))) {
                    pw.println("row,reason");
                    for (Map.Entry<String, String> e : invalidRows.entrySet()) {
                        String r = e.getKey().replaceAll("\n", " ").replaceAll(",", " ");
                        pw.println(r + "," + e.getValue());
                    }
                }
            }


            PrintWriter out = resp.getWriter();
            out.println("{");
            out.println("\"validCount\": " + validRows.size() + ",");
            out.println("\"invalidCount\": " + invalidRows.size() + ",");
            out.println("\"insertedCount\": " + inserted + ",");
            out.println("\"priceGt500Count\": " + priceGt500Count + ",");
            out.println("\"futureDateCount\": " + futureDateCount + ",");
            out.println("\"invalidOrderIdCount\": " + invalidOrderIdCount + ",");
            out.println("\"parseErrorCount\": " + parseErrorCount + ",");

            out.println("\"invalidSample\": [");
            int shown = 0;
            for (Map.Entry<String, String> e : invalidRows.entrySet()) {
                if (shown++ > 9) break;
                String r = e.getKey().replaceAll("\\\"", "\\\\\"");
                out.println("{\"row\": \"" + r + "\", \"reason\": \"" + e.getValue() + "\"}");
                if (shown <= 10 && shown < invalidRows.size()) out.println(",");
            }
            out.println("]");
            out.println("}");

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().println(
                "{\"error\":\"Internal server error while processing CSV\"}"
            );
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        doPost(req, resp);
    }

    private Set<Integer> loadValidOrderIds() throws Exception {
        Set<Integer> orderIds = new HashSet<>();

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT VechileId FROM Vechiles")) {

            while (rs.next()) {
                orderIds.add(rs.getInt("VechileId"));
            }
        }
        return orderIds;
    }

    private File generateCleanedCsv(List<String> validRows) throws IOException {
    File outputFile = new File(
        "D:/zoho/fleet_management/output/cleaned-trip-logs.csv"
    );

    File parent = outputFile.getParentFile();
    if (parent != null && !parent.exists()) {
        if (!parent.mkdirs()) {
            throw new IOException("Failed to create output directory: " + parent.getAbsolutePath());
        }
    }

    try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {

        writer.println("order_id,product_name,price,quantity,item_date");

        for (String row : validRows) {
            writer.println(row);
        }
    }

    return outputFile;
    }

    private int bulkInsertOrderItems(List<String> validRows) throws Exception {

    String sql =
        "INSERT IGNORE INTO order_items " +
        "(order_id, product_name, price, quantity, item_date) " +
        "VALUES (?, ?, ?, ?, ?)";

    int insertedCount = 0;

    try (Connection conn = DBConnection.getConnection()) {

        try (Statement st = conn.createStatement()) {
            try {
                st.execute("ALTER TABLE order_items ADD UNIQUE KEY unique_order_item (order_id, product_name, item_date)");
            } catch (SQLException e) {
            }
        } catch (Exception ignore) {
        }
        try {
            java.sql.DatabaseMetaData md = conn.getMetaData();
            File outDir = new File("D:/zoho/belle_croissant/output");
            if (!outDir.exists()) outDir.mkdirs();
            File metaLog = new File(outDir, "insert-log.txt");
            try (PrintWriter pw = new PrintWriter(new FileWriter(metaLog, true))) {
                pw.println("Timestamp: " + java.time.LocalDateTime.now());
                pw.println("DB URL: " + md.getURL());
                pw.println("DB User: " + md.getUserName());
                pw.println("Catalog: " + conn.getCatalog());
                pw.println("----");
            }
        } catch (Exception ignore) {
        }

        conn.setAutoCommit(false);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String row : validRows) {
                String[] cols = row.split(",");

                ps.setInt(1, Integer.parseInt(cols[0].trim()));
                ps.setString(2, cols[1].trim());
                ps.setBigDecimal(3, new java.math.BigDecimal(cols[2].trim()));
                ps.setInt(4, Integer.parseInt(cols[3].trim()));
                ps.setDate(5, java.sql.Date.valueOf(cols[4].trim()));

                ps.addBatch();
            }

            int[] results = ps.executeBatch();
            conn.commit();

         
            int realInserted = 0;
            if (results != null) {
                for (int r : results) {
                    if (r == Statement.SUCCESS_NO_INFO || r > 0) realInserted++;
                }
            }
            insertedCount = realInserted;

        } catch (Exception ex) {
            conn.rollback();
            throw ex;
        }
    }

    return insertedCount;
    }
}
