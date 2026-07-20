FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

COPY moyuyo-api/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -sf http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-jar", \
  "-Xms${JVM_XMS:-512m}", \
  "-Xmx${JVM_XMX:-1024m}", \
  "-XX:+UseZGC", \
  "-XX:MaxGCPauseMillis=100", \
  "-Dspring.profiles.active=prod", \
  "app.jar"]
