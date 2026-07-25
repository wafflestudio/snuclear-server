# 1단계: 부트 jar를 레이어별로 분해한다.
# 통짜 jar를 그대로 COPY하면 코드 한 줄만 바뀌어도 123MB 레이어 전체를
# 레지스트리에 다시 푸시하게 된다. 의존성과 애플리케이션을 분리하면
# 의존성이 그대로인 배포에서는 애플리케이션 레이어(약 15MB)만 전송된다.
FROM eclipse-temurin:21-jre AS builder
WORKDIR /builder

# bootJar 산출물만 지정한다. build/libs 에는 -plain.jar 가 함께 생길 수 있어
# *.jar 로 받으면 어떤 jar가 잡힐지 불확실해진다.
ARG JAR_FILE=build/libs/*-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# 2단계: 분해된 레이어를 변경 빈도가 낮은 순서로 쌓는다.
# 순서가 곧 캐시 적중률이므로 dependencies 를 가장 먼저 둔다.
FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=Asia/Seoul

COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

# 분해된 형태에서는 jar 대신 JarLauncher 로 기동한다.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
