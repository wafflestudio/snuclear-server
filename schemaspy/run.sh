#!/usr/bin/env bash
# SnuClear DB 스키마 문서(SchemaSpy) 생성
# 사용법:
#   ./run.sh          문서 생성 후 http://localhost:8081 서빙 (로컬 확인용)
#   ./run.sh gen      문서만 생성해 ../src/main/resources/static/schema 에 복사 (CI/배포용, 서빙 X)
#   ./run.sh down     스택 정리
set -euo pipefail
cd "$(dirname "$0")"

MYSQL_CONNECTOR_VERSION="9.7.0"
DRIVER_JAR="drivers/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar"
COMPOSE="docker compose -f docker-compose.schemaspy.yaml"
STATIC_DEST="../src/main/resources/static/schema"

ensure_driver() {
  # SchemaSpy 이미지는 MySQL 드라이버를 번들하지 않으므로 없으면 받아둔다.
  if [[ ! -f "$DRIVER_JAR" ]]; then
    echo "[run] MySQL 커넥터 다운로드: $DRIVER_JAR"
    mkdir -p drivers
    curl -fsSL -o "$DRIVER_JAR" \
      "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${MYSQL_CONNECTOR_VERSION}/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar"
  fi
}

case "${1:-}" in
  down)
    $COMPOSE down -v
    exit 0
    ;;

  gen)
    # CI/배포용: MySQL→Flyway→SchemaSpy 만 실행(웹 서빙 없음)하고
    # 산출물을 앱 정적 리소스 경로로 복사 → bootJar 에 포함되어 백엔드 도메인에서 서빙됨.
    ensure_driver
    rm -rf output && mkdir -p output
    echo "[run] 문서 생성 (MySQL → Flyway → SchemaSpy)"
    # run: 의존성(db healthy, flyway completed_successfully)을 조건대로 기동한 뒤 schemaspy 실행,
    #      schemaspy 종료코드를 그대로 반환한다. (web 서비스는 기동하지 않음)
    set +e
    $COMPOSE run --rm schemaspy
    rc=$?
    set -e
    if [[ $rc -ne 0 ]]; then
      echo "[run] SchemaSpy 실패 (exit $rc)" >&2
      $COMPOSE down -v
      exit $rc
    fi
    echo "[run] 정적 리소스로 복사: $STATIC_DEST"
    rm -rf "$STATIC_DEST" && mkdir -p "$STATIC_DEST"
    cp -r output/. "$STATIC_DEST/"
    $COMPOSE down -v
    echo "[run] 완료. bootJar 시 static/schema 로 패키징됨."
    exit 0
    ;;

  *)
    ensure_driver
    rm -rf output && mkdir -p output
    echo "[run] 스택 기동 (MySQL → Flyway → SchemaSpy → nginx)"
    $COMPOSE up --build --remove-orphans -d
    echo
    echo "[run] 문서 서버: http://localhost:8081"
    echo "[run] 정리: ./run.sh down"
    ;;
esac
