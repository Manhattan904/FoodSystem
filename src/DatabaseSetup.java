import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DatabaseSetup {
    public static void main(String[] args) {
        
        // 【新增這段】強制 Java 載入 SQLite 驅動程式
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.out.println("慘了，Java 還是找不到驅動程式：" + e.getMessage());
            return; // 找不到就提早結束程式
        }

        // 設定資料庫檔案的路徑
        String url = "jdbc:sqlite:db/campus_food.db";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            System.out.println("成功連接到 SQLite 資料庫！");

            // 1. 建立 stalls (攤位) 資料表
            String createStallsTable = "CREATE TABLE IF NOT EXISTS stalls ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT NOT NULL,"
                    + "location TEXT"
                    + ");";
            stmt.execute(createStallsTable);
            System.out.println("stalls 資料表檢查/建立完成");

            // 2. 建立 checkins (簽到) 資料表
            String createCheckinsTable = "CREATE TABLE IF NOT EXISTS checkins ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "stall_id INTEGER NOT NULL,"
                    + "checkin_time DATETIME NOT NULL,"
                    + "FOREIGN KEY (stall_id) REFERENCES stalls(id)"
                    + ");";
            stmt.execute(createCheckinsTable);
            System.out.println("checkins 資料表檢查/建立完成");

            // 3. 建立 reviews (評論) 資料表
            String createReviewsTable = "CREATE TABLE IF NOT EXISTS reviews ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "stall_id INTEGER NOT NULL,"
                    + "content TEXT NOT NULL,"
                    + "sentiment TEXT,"
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                    + "FOREIGN KEY (stall_id) REFERENCES stalls(id)"
                    + ");";
            stmt.execute(createReviewsTable);
            System.out.println("reviews 資料表檢查/建立完成");

            System.out.println("所有資料表皆已準備就緒！可以開始塞資料了！");

        } catch (Exception e) {
            System.out.println("發生錯誤：" + e.getMessage());
        }
    }
}