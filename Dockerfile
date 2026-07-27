# 纯运行镜像: 后端 JAR 已预编译
FROM eclipse-temurin:17-jre
WORKDIR /app

# 后端 JAR
COPY backend/target/finance-system-backend.jar ./backend.jar

# API Key 配置
COPY data/ ./data/

EXPOSE 8090
ENV DB_URL=jdbc:mysql://mysql:3306/finance_system?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
ENV DB_USERNAME=root
ENV DB_PASSWORD=root

ENTRYPOINT ["java", "-jar", "backend.jar", "--server.port=8090"]
