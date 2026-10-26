# 수강신청 레이턴시 캡처

Prometheus는 최근 10일치만 보관(클러스터 공유 설정)한다. 수강신청처럼 **짧고 예측 가능한
트래픽 스파이크 구간**만 골라서 그 시계열을 떠 두면, retention을 늘리지 않고도 학기별로 영구 보존된다.

## 구간 규칙 (고정)

`[장바구니신청(1일차) 시작 ~ 선착순수강신청(2일차) 종료]`

- SNU 공식 일정(`/api/v1/syncwithsite/sugang-period`)에서 매 학기 자동 산출 → 날짜 하드코딩 없음.
- `예비장바구니`, `예비선착순`, `선착순 3일차`, `수강신청변경` 등은 제외.
- 예) 2026-2학기 → 2026-08-04 ~ 2026-08-10.

## 동작

- `.github/workflows/capture-latency.yml` 이 매일 1회 실행.
- 구간이 **끝난 뒤**(모든 데이터 수집 완료) 아직 캡처 안 했으면, Grafana를 통해 Prometheus에서
  p50/p95/p99/RPS/에러율 시계열(overall + 엔드포인트별)을 떠서 `captures/<학기>.json.gz` 로 커밋.
- 즉 학기당 파일 1개, 자동. 손 델 것 없음.

## 사전 준비 (1회)

Grafana 서비스계정 토큰이 필요하다(자동화가 OAuth 없이 API 접근하려면):

1. `grafana.wafflestudio.com` → Administration → Service accounts → 새 계정(Viewer 권한) → 토큰 발급
2. snuclear-server 리포 → Settings → Secrets → Actions → `GRAFANA_TOKEN` 등록

토큰이 없으면 워크플로는 조용히 skip한다(실패 아님).

## 저장 형식 / 다시 보기

`captures/<학기>.json.gz` — gzip된 JSON. 구조:

```
{
  "meta":   { semester, start, end, start_category, end_category },
  "step_seconds": 60,
  "queries": { <이름>: <PromQL> },
  "data":    { overall_p95: [Prometheus matrix], by_uri_p95: [...], ... }
}
```

`data`의 각 값은 Prometheus `query_range` 결과(matrix)라, 그대로 다시 그릴 수 있다:

```python
import gzip, json
d = json.load(gzip.open("captures/2026-2.json.gz"))
for s in d["data"]["by_uri_p95"]:
    uri = s["metric"].get("uri")
    xy = [(int(t), float(v)) for t, v in s["values"]]   # (epoch, seconds)
    ...  # matplotlib 등으로 플롯
```

수동 재캡처(구간 종료 전 지금까지 데이터로): Actions → Capture enrollment latency → Run workflow → force 체크.
