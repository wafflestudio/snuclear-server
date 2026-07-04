# SchemaSpy — SnuClear DB 스키마 문서

SnuClear DB 스키마를 정적 HTML로 문서화한다. **인증 없이** 브라우저로 바로 열린다.

제공 내용:
- **Relationships** — FK 관계 테이블 목록 + 관계도 (`relationships.html`)
- **Tables / Columns** — 테이블별 컬럼 상세, 타입, 제약, 인덱스 (`tables/<name>.html`, `columns.html`)
- **Constraints** — PK/FK 제약 목록 (`constraints.html`)

## 실행

```bash
cd snuclear-server/schemaspy
./run.sh            # 문서 생성 후 http://localhost:8081 서빙
./run.sh down       # 스택 정리 (볼륨 포함)
```

Docker만 있으면 된다. 열기: <http://localhost:8081>

## 동작 방식

`docker-compose.schemaspy.yaml` 이 4단계를 순서대로 실행한다:

1. **db** — 문서 생성 전용 임시 MySQL 8.4 (개발/운영 DB와 분리)
2. **flyway** — `src/main/resources/db/migration` 의 마이그레이션을 그대로 적용해 실제와 동일한 스키마 생성
3. **schemaspy** — 스키마를 읽어 `output/` 에 HTML 생성
4. **web** — nginx 로 `output/` 을 `:8081` 에 서빙

마이그레이션을 추가한 뒤 문서를 갱신하려면 `./run.sh` 를 다시 실행하면 된다.

## 배포 (백엔드 도메인에서 서빙)

로컬 서빙(nginx :8081) 외에, **백엔드 도메인에서 직접** 열람 가능하다.

- 배포 시 CI(`.github/workflows/_deploy.yml`)가 `./schemaspy/run.sh gen` 으로 문서를 생성해
  `src/main/resources/static/schema/` 에 배치 → `bootJar` 로 앱 이미지에 패키징된다.
- Spring 이 정적 리소스로 서빙하고, `SecurityConfig` 에서 `/schema/**` 를 `permitAll` 로 열어
  **인증 없이** 접근된다. (`/schema` → `/schema/index.html` 리다이렉트: `SchemaDocsConfig`)
- 열람: `https://<백엔드도메인>/schema` (prod: `https://snuclear-server.wafflestudio.com/schema`)
- SchemaSpy 이미지는 amd64 전용이라 arm64 배포 러너에서는 QEMU 로 실행된다(워크플로우에 설정됨).

> ⚠️ 스키마 문서는 전체 테이블/컬럼 구조를 공개적으로 노출한다. `permitAll` 은 프로필 구분 없이
> 적용되므로 dev·prod 어느 쪽이든 서버가 떠 있으면 `/schema` 가 열린다. 특정 환경만 노출하려면
> `SecurityConfig` 의 `/schema/**` 등록과 CI 생성 단계를 프로필/브랜치로 분기해야 한다.

## 참고

- SchemaSpy 이미지는 MySQL JDBC 드라이버를 번들하지 않아 `run.sh` 가 `drivers/` 에 커넥터를 자동으로 받아둔다.
- `drivers/`, `output/` 은 `.gitignore` 대상(다운로드/생성물).
- 설정은 `schemaspy.properties` 에서 조정한다. Flyway 관리 테이블(`flyway_schema_history`)은 문서에서 제외돼 있다.
