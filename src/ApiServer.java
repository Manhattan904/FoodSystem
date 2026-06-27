import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;

public class ApiServer {
    private static final String DB_URL = "jdbc:sqlite:campus_food.db";

    public static void main(String[] args) throws IOException {
        // 初始化資料庫環境
        initDatabase();
        
        // 取得 Railway 設定的 PORT，預設為 8080
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;
        
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        
        // 設定各個 API 端點，完全對應你原本的路由設計
        server.createContext("/stalls", ApiServer::handleGetAllStalls);
        server.createContext("/reviews", ApiServer::handleReviews);
        server.createContext("/checkins", ApiServer::handlePostCheckin);
        server.createContext("/stalls/", ApiServer::handleGetCrowdLevel); 
        
        server.setExecutor(null);
        server.start();
        System.out.println("★ 伺服器完整版已啟動，監聽 Port: " + port);
    }

    private static void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL); 
             Statement stmt = conn.createStatement()) {
            // 建立資料庫表結構
            stmt.execute("CREATE TABLE IF NOT EXISTS stalls (id INTEGER PRIMARY KEY, name TEXT, location TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS reviews (id INTEGER PRIMARY KEY AUTOINCREMENT, stall_id INTEGER, content TEXT, sentiment TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS checkins (id INTEGER PRIMARY KEY AUTOINCREMENT, stall_id INTEGER, checkin_time DATETIME)");
            
            // 初始化資料
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM stalls");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO stalls VALUES (1, '學餐一樓自助餐', '教學區'), (2, '二樓拉麵店', '活動中心'), (3, '三樓麥味登', '教學區'), (4, '四樓素食自助餐', '活動中心'), (5, '校門口咖啡廳', '圖書館')");
            }
        } catch (SQLException e) { 
            e.printStackTrace(); 
        }
    }

    private static void handleGetAllStalls(HttpExchange ex) throws IOException {
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DriverManager.getConnection(DB_URL); 
             Statement stmt = conn.createStatement(); 
             ResultSet rs = stmt.executeQuery("SELECT * FROM stalls")) {
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                json.append(String.format("{\"id\":%d, \"name\":\"%s\", \"location\":\"%s\"}", 
                            rs.getInt("id"), rs.getString("name"), rs.getString("location")));
                first = false;
            }
        } catch (SQLException e) { sendResponse(ex, 500, "{\"error\": \"資料庫讀取失敗\"}"); return; }
        json.append("]");
        sendResponse(ex, 200, json.toString());
    }

    private static void handleReviews(HttpExchange ex) throws IOException {
        if ("POST".equals(ex.getRequestMethod())) {
            InputStreamReader isr = new InputStreamReader(ex.getRequestBody(), "utf-8");
            BufferedReader br = new BufferedReader(isr);
            String body = br.readLine();
            String[] parts = body.split(",", 2); 
            String sentiment = analyzeSentimentLocal(parts[1]);
            
            try (Connection conn = DriverManager.getConnection(DB_URL); 
                 PreparedStatement pstmt = conn.prepareStatement("INSERT INTO reviews (stall_id, content, sentiment) VALUES (?, ?, ?)")) {
                pstmt.setInt(1, Integer.parseInt(parts[0]));
                pstmt.setString(2, parts[1]);
                pstmt.setString(3, sentiment);
                pstmt.executeUpdate();
                sendResponse(ex, 200, "{\"sentiment\":\"" + sentiment + "\"}");
            } catch (Exception e) { sendResponse(ex, 500, "{\"error\":\"評論寫入失敗\"}"); }
        } else {
            // GET 處理：回傳該攤位的評論
            String query = ex.getRequestURI().getQuery();
            int stallId = Integer.parseInt(query.split("=")[1]);
            StringBuilder json = new StringBuilder("[");
            try (Connection conn = DriverManager.getConnection(DB_URL); 
                 PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM reviews WHERE stall_id = ?")) {
                pstmt.setInt(1, stallId);
                ResultSet rs = pstmt.executeQuery();
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append(String.format("{\"content\":\"%s\", \"sentiment\":\"%s\"}", 
                                rs.getString("content"), rs.getString("sentiment")));
                    first = false;
                }
            } catch (SQLException e) { }
            json.append("]");
            sendResponse(ex, 200, json.toString());
        }
    }

    private static void handlePostCheckin(HttpExchange ex) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(ex.getRequestBody(), "utf-8"));
        int stallId = Integer.parseInt(br.readLine().trim());
        try (Connection conn = DriverManager.getConnection(DB_URL); 
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO checkins (stall_id, checkin_time) VALUES (?, datetime('now', 'localtime'))")) {
            pstmt.setInt(1, stallId);
            pstmt.executeUpdate();
            sendResponse(ex, 200, "{\"status\": \"成功\"}");
        } catch (Exception e) { sendResponse(ex, 500, "{\"error\":\"簽到處理錯誤\"}"); }
    }

    private static void handleGetCrowdLevel(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");
        int stallId = Integer.parseInt(parts[2]);
        
        int count = 0;
        try (Connection conn = DriverManager.getConnection(DB_URL); 
             PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM checkins WHERE stall_id = ? AND date(checkin_time) = date('now')")) {
            pstmt.setInt(1, stallId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) count = rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        
        String level = (count > 5) ? "忙碌 (Busy)" : "空閒 (Idle)";
        String json = String.format("{\"crowd_level\":\"%s\", \"current_hour_count\":%d, \"calculated_threshold\":10}", level, count);
        sendResponse(ex, 200, json);
    }

    private static String analyzeSentimentLocal(String reviewText) {
        String[] positiveWords = {"好吃", "讚", "喜歡", "棒", "美味", "不錯", "推", "好喝", "滿意", "便宜", "好"};
        String[] negativeWords = {"難吃", "噁心", "倒閉", "差", "爛", "雷", "難喝", "貴", "糟", "不推", "態度", "壞"};
        int score = 0;
        for (String w : positiveWords) if (reviewText.contains(w)) score++;
        for (String w : negativeWords) if (reviewText.contains(w)) score--;
        return (score >= 0) ? "positive" : "negative";
    }

    private static void sendResponse(HttpExchange ex, int code, String res) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = res.getBytes("UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        OutputStream os = ex.getResponseBody();
        os.write(bytes);
        os.close();
    }
}