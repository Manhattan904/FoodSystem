import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.LocalTime;
import java.io.File;

public class ApiServer {
    private static final String DB_DIR = "db";
    private static final String DB_URL = "jdbc:sqlite:db/campus_food.db";

    public static void main(String[] args) throws IOException {
        try { 
            Class.forName("org.sqlite.JDBC"); 
            initDatabase(); 
        } catch (Exception e) { 
            System.out.println("資料庫初始化失敗: " + e.getMessage());
            return; 
        }

        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;
        
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        
        server.createContext("/stalls", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getPath();
                if ("GET".equals(exchange.getRequestMethod())) {
                    if (path.equals("/stalls")) handleGetAllStalls(exchange);
                    else if (path.matches("/stalls/\\d+/crowd-level")) {
                        int stallId = Integer.parseInt(path.split("/")[2]);
                        handleGetCrowdLevel(exchange, stallId);
                    }
                }
            }
        });
        server.createContext("/reviews", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("POST".equals(exchange.getRequestMethod())) handlePostReview(exchange);
                else if ("GET".equals(exchange.getRequestMethod())) handleGetReviews(exchange);
            }
        });
        server.createContext("/checkins", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("POST".equals(exchange.getRequestMethod())) handlePostCheckin(exchange);
            }
        });
        server.setExecutor(null); 
        server.start();
        System.out.println("伺服器已成功啟動！正在監聽 Port: " + port);
        System.out.println("★ 目前運行模式：本地端關鍵字情緒分析引擎 (離線模式)");
    }

    private static void initDatabase() {
        new File(DB_DIR).mkdirs();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS stalls (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, location TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS reviews (id INTEGER PRIMARY KEY AUTOINCREMENT, stall_id INTEGER, content TEXT, sentiment TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS checkins (id INTEGER PRIMARY KEY AUTOINCREMENT, stall_id INTEGER, checkin_time DATETIME)");
            
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS count FROM stalls");
            if (rs.next() && rs.getInt("count") == 0) {
                // ★ 這裡已經幫你恢復成 5 家攤位了！
                stmt.execute("INSERT INTO stalls (name, location) VALUES " +
                             "('學餐一樓自助餐', '教學區'), " +
                             "('二樓拉麵店', '活動中心'), " +
                             "('三樓麥味登', '教學區'), " +
                             "('四樓素食自助餐', '活動中心'), " +
                             "('校門口咖啡廳', '圖書館')");
            }
        } catch (Exception e) {
            System.out.println("建表失敗: " + e.getMessage());
        }
    }

    private static void handlePostReview(HttpExchange exchange) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "utf-8"));
        String requestBody = br.readLine();
        if (requestBody == null || !requestBody.contains(",")) { sendResponse(exchange, 400, "{\"error\": \"格式錯誤\"}"); return; }
        String[] parts = requestBody.split(",", 2);
        int stallId = Integer.parseInt(parts[0].trim());
        String content = parts[1].trim();

        // 呼叫本地端的分析器，100% 不會因為網路或 API 報錯
        String sentiment = analyzeSentimentLocal(content);
        System.out.println("★ 判定結果: " + sentiment + " (評論: " + content + ")");

        String sql = "INSERT INTO reviews (stall_id, content, sentiment) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, stallId);
            pstmt.setString(2, content);
            pstmt.setString(3, sentiment);
            pstmt.executeUpdate();
            sendResponse(exchange, 200, "{\"status\": \"success\", \"sentiment_by_ai\": \"" + sentiment + "\"}");
        } catch (Exception e) { sendResponse(exchange, 500, "{\"error\": \"DB寫入失敗\"}"); }
    }

    // ★ 本地端情緒分析引擎
    private static String analyzeSentimentLocal(String reviewText) {
        String[] positiveWords = {"好吃", "讚", "喜歡", "棒", "美味", "不錯", "推", "好喝", "滿意", "便宜", "好"};
        String[] negativeWords = {"難吃", "噁心", "倒閉", "差", "爛", "雷", "難喝", "貴", "糟", "不推", "態度", "壞"};

        int score = 0;
        for (String word : positiveWords) { if (reviewText.contains(word)) score++; }
        for (String word : negativeWords) { if (reviewText.contains(word)) score--; }

        if (score < 0) {
            return "negative";
        } else {
            return "positive";
        }
    }

    private static void handleGetReviews(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        int stallId = Integer.parseInt(query.split("=")[1]);
        StringBuilder jsonBuilder = new StringBuilder("[");
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement("SELECT content, sentiment FROM reviews WHERE stall_id = ? ORDER BY id DESC")) {
            pstmt.setInt(1, stallId);
            ResultSet rs = pstmt.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) jsonBuilder.append(",");
                jsonBuilder.append(String.format("{\"content\":\"%s\", \"sentiment\":\"%s\"}", rs.getString("content").replace("\"", "\\\""), rs.getString("sentiment")));
                first = false;
            }
        } catch (Exception e) { sendResponse(exchange, 500, "{\"error\": \"DB錯誤\"}"); return; }
        jsonBuilder.append("]");
        sendResponse(exchange, 200, jsonBuilder.toString());
    }

    private static void handlePostCheckin(HttpExchange exchange) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "utf-8"));
        String stallIdStr = br.readLine(); 
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO checkins (stall_id, checkin_time) VALUES (?, datetime('now', 'localtime'))")) {
            pstmt.setInt(1, Integer.parseInt(stallIdStr.trim()));
            pstmt.executeUpdate();
            sendResponse(exchange, 200, "{\"status\": \"簽到成功\"}");
        } catch (Exception e) { sendResponse(exchange, 500, "{\"error\": \"簽到失敗\"}"); }
    }

    private static void handleGetAllStalls(HttpExchange exchange) throws IOException {
        StringBuilder jsonBuilder = new StringBuilder("[");
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM stalls")) {
            boolean first = true;
            while (rs.next()) {
                if (!first) jsonBuilder.append(",");
                jsonBuilder.append(String.format("{\"id\":%d, \"name\":\"%s\", \"location\":\"%s\"}", rs.getInt("id"), rs.getString("name"), rs.getString("location")));
                first = false;
            }
        } catch (Exception e) { sendResponse(exchange, 500, "{\"error\": \"DB錯誤\"}"); return; }
        jsonBuilder.append("]");
        sendResponse(exchange, 200, jsonBuilder.toString());
    }

    private static void handleGetCrowdLevel(HttpExchange exchange, int stallId) throws IOException {
        List<Integer> hourlyCounts = new ArrayList<>();
        int currentHourCount = 0;
        int currentHour = LocalTime.now().getHour();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement("SELECT strftime('%H', checkin_time) as hour, COUNT(*) as count FROM checkins WHERE stall_id = ? GROUP BY hour")) {
            pstmt.setInt(1, stallId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                int count = rs.getInt("count");
                hourlyCounts.add(count);
                if (Integer.parseInt(rs.getString("hour")) == currentHour) currentHourCount = count;
            }
        } catch (Exception e) { sendResponse(exchange, 500, "{\"error\": \"DB錯誤\"}"); return; }
        Collections.sort(hourlyCounts);
        int bestThreshold = hourlyCounts.size() > 1 ? hourlyCounts.get(hourlyCounts.size()/2) : 10;
        String crowdLevel = (currentHourCount >= bestThreshold) ? "忙碌 (Busy)" : "空閒 (Idle)";
        String json = String.format("{\"stall_id\": %d, \"current_hour_count\": %d, \"calculated_threshold\": %d, \"crowd_level\": \"%s\"}",
            stallId, currentHourCount, bestThreshold, crowdLevel);
        sendResponse(exchange, 200, json);
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = response.getBytes("UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}