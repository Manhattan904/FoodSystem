import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;

public class ApiServer {
    private static final String DB_URL = "jdbc:sqlite:db/campus_food.db";

    public static void main(String[] args) throws IOException {
        initDatabase(); 
        
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;
        
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        
        // 根目錄回應，確認服務存活
        server.createContext("/", exchange -> {
            sendResponse(exchange, 200, "API is running. Use /stalls to get data.");
        });

        // 取得攤位清單 API
        server.createContext("/stalls", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) handleGetAllStalls(exchange);
        });

        // 簽到 API
        server.createContext("/checkins", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) handlePostCheckin(exchange);
        });
        
        server.setExecutor(null); 
        server.start();
        System.out.println("伺服器已啟動，監聽 Port: " + port);
    }

    private static void initDatabase() {
        new File("db").mkdirs();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS stalls (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, location TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS checkins (id INTEGER PRIMARY KEY AUTOINCREMENT, stall_id INTEGER, checkin_time DATETIME)");
            
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM stalls");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO stalls (name, location) VALUES ('學餐一樓自助餐', '教學區'), ('二樓拉麵店', '活動中心'), ('三樓麥味登', '教學區'), ('四樓素食自助餐', '活動中心'), ('校門口咖啡廳', '圖書館')");
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void handleGetAllStalls(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM stalls")) {
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                json.append(String.format("{\"id\":%d, \"name\":\"%s\", \"location\":\"%s\"}", rs.getInt("id"), rs.getString("name"), rs.getString("location")));
                first = false;
            }
        } catch (Exception e) { sendResponse(exchange, 500, "{\"error\": \"DB錯誤\"}"); return; }
        json.append("]");
        sendResponse(exchange, 200, json.toString());
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

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        // 設定 CORS，允許前端跨網域存取
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