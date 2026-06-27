FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY . .

# 下載所需的 JAR 檔
RUN wget https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar -O sqlite-jdbc.jar && \
    wget https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.7/slf4j-api-2.0.7.jar -O slf4j-api.jar && \
    wget https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.7/slf4j-simple-2.0.7.jar -O slf4j-simple.jar

# 編譯所有 java 檔，並把 jar 加入 classpath
RUN javac -cp ".:sqlite-jdbc.jar:slf4j-api.jar:slf4j-simple.jar" src/*.java

# 執行：明確指定 classpath，包含目前目錄(.)、所有 jar、以及 src 資料夾
CMD ["java", "-cp", ".:sqlite-jdbc.jar:slf4j-api.jar:slf4j-simple.jar:src", "ApiServer"]