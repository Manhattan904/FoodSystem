FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY . .

# 下載必要的函式庫
RUN wget https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar -O sqlite-jdbc.jar && \
    wget https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.7/slf4j-api-2.0.7.jar -O slf4j-api.jar && \
    wget https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.7/slf4j-simple-2.0.7.jar -O slf4j-simple.jar

# 編譯所有程式
RUN javac -cp ".:sqlite-jdbc.jar:slf4j-api.jar:slf4j-simple.jar" src/*.java

# 執行
CMD ["java", "-cp", ".:sqlite-jdbc.jar:slf4j-api.jar:slf4j-simple.jar:src", "ApiServer"]