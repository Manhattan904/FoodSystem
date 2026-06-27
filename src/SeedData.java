import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class SeedData {
    public static void main(String[] args) {
        // 強制載入驅動 (避免之前遇到找不到驅動的問題)
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.out.println("找不到驅動程式：" + e.getMessage());
            return;
        }

        String url = "jdbc:sqlite:db/campus_food.db";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            System.out.println("開始灌入種子資料，請稍候...");

            // 1. 清空舊資料 (避免重複執行時資料爆增)
            stmt.execute("DELETE FROM reviews;");
            stmt.execute("DELETE FROM checkins;");
            stmt.execute("DELETE FROM stalls;");
            stmt.execute("DELETE FROM sqlite_sequence WHERE name IN ('stalls', 'checkins', 'reviews');");

            // 2. 寫入 5 個攤位 
            String[] stalls = {
                "INSERT INTO stalls (name, location) VALUES ('阿姨炒飯', '學餐一樓');",
                "INSERT INTO stalls (name, location) VALUES ('雞排英雄', '學餐外側');",
                "INSERT INTO stalls (name, location) VALUES ('健康水煮餐', '學餐二樓');",
                "INSERT INTO stalls (name, location) VALUES ('滷味大師', '後門美食街');",
                "INSERT INTO stalls (name, location) VALUES ('早安三明治', '校門口');"
            };
            for (String s : stalls) stmt.execute(s);

            // 3. 寫入 100 筆評論 (正負各 50 筆，包含指定詞彙) 
            String[] posReviews = {
                "阿姨給的份量超多，划算又好吃！", "老闆很親切，健康吃這個好吃無負擔。", 
                "早上的阿姨很親切，好吃出餐快。", "非常划算，每次來都覺得很親切！"
            };
            String[] negReviews = {
                "人太多了每次都要等太久，老闆態度不好。", "態度不好，而且份量變少，等太久！", 
                "趕時間還讓我等太久，態度不好！", "滷得不夠入味，態度不好，份量變少。"
            };
            for(int i = 0; i < 50; i++) {
                int stallId = (i % 5) + 1;
                String pos = posReviews[i % posReviews.length];
                String neg = negReviews[i % negReviews.length];
                stmt.execute("INSERT INTO reviews (stall_id, content, sentiment) VALUES (" + stallId + ", '" + pos + "', 'positive');");
                stmt.execute("INSERT INTO reviews (stall_id, content, sentiment) VALUES (" + stallId + ", '" + neg + "', 'negative');");
            }

            // 4. 寫入兩週的簽到紀錄 (製造尖峰與離峰的極端差異) 
            int checkinCount = 0;
            for (int day = 1; day <= 14; day++) {
                String dateStr = String.format("2026-06-%02d", day);
                
                for (int stall = 1; stall <= 5; stall++) {
                    // 午餐尖峰 11:30 - 13:00 (高人流) 
                    for(int i = 0; i < 20; i++) {
                        stmt.execute("INSERT INTO checkins (stall_id, checkin_time) VALUES (" + stall + ", '" + dateStr + " 12:" + String.format("%02d", i*2) + ":00');");
                        checkinCount++;
                    }
                    // 晚餐尖峰 17:30 - 19:00 (高人流) 
                    for(int i = 0; i < 18; i++) {
                        stmt.execute("INSERT INTO checkins (stall_id, checkin_time) VALUES (" + stall + ", '" + dateStr + " 18:" + String.format("%02d", i*3) + ":00');");
                        checkinCount++;
                    }
                    // 離峰時段 14:00 - 16:00 (低人流) 
                    for(int i = 0; i < 2; i++) {
                        stmt.execute("INSERT INTO checkins (stall_id, checkin_time) VALUES (" + stall + ", '" + dateStr + " 15:" + String.format("%02d", i*10) + ":00');");
                        checkinCount++;
                    }
                }
            }

            System.out.println("資料灌入大成功！");
            System.out.println("共新增了 5 個攤位、100 筆評價，以及 " + checkinCount + " 筆簽到紀錄！");

        } catch (Exception e) {
            System.out.println("發生錯誤：" + e.getMessage());
        }
    }
}