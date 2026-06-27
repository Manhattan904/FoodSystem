import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.*;
import java.time.LocalTime;

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

        // Render 會動態指定 PORT
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;
        
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        
        // 處理根目錄 (避免 404)
        server.createContext("/", exchange -> {
            String response = "API is running. Use /stalls to get data.";
            byte[] bytes = response.getBytes("UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        });

        server.createContext("/stalls", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("GET".equals(exchange.getRequestMethod())) handleGetAllStalls(exchange);
            }
        });
        
        server.setExecutor(null); 
        server.start();
        System.out.println("伺服器已成功啟動！監聽 Port: " + port);
    }

    private static void initDatabase() {
        new File(DB_DIR).mkdirs();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS stalls (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, location TEXT)");
            
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS count FROM stalls");
            if (rs.next() && rs.getInt("count") == 0) {
                stmt.execute("INSERT INTO stalls (name, location) VALUES ('學餐一樓自助餐', '教學區'), ('二樓拉麵店', '活動中心'), ('三樓麥味登', '教學區'), ('四樓素食自助餐', '活動中心'), ('校門口咖啡廳', '圖書館')");
            }
        } catch (Exception e) { System.out.println("建表失敗: " + e.getMessage()); }
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

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = response.getBytes("UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}