FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY . /app
# 下載 SQLite JDBC 以便連線資料庫
RUN wget https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar -O sqlite-jdbc.jar
# 編譯程式碼
RUN javac -cp "sqlite-jdbc.jar" src/ApiServer.java
# 啟動程式
CMD ["java", "-cp", ".:sqlite-jdbc.jar:src", "ApiServer"]