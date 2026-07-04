#!/usr/bin/env python3
"""수강신청 기간의 API 레이턴시 시계열을 Grafana(Prometheus)에서 떠서 보관한다.

구간 규칙(고정): [장바구니신청(1일차) 시작 ~ 선착순수강신청(2일차) 종료]
  - '예비장바구니', '예비선착순', '선착순 3일차' 등은 제외.
  - 매 학기 SNU 공식 일정(sugang-period)에서 자동 산출 → 날짜 하드코딩 없음.

동작:
  1) prod sugang-period 엔드포인트에서 이번 학기 일정을 읽어 구간을 계산.
  2) 구간이 이미 끝났고(모든 데이터 수집 완료) 아직 캡처 안 했으면,
  3) Grafana datasource proxy로 p50/p95/p99/RPS/에러율 시계열을 query_range로 떠서,
  4) monitoring/captures/<학기>.json.gz 로 저장(gzip: 메트릭은 압축률이 높음).

환경변수:
  GRAFANA_URL    (기본 https://grafana.wafflestudio.com)
  GRAFANA_TOKEN  (필수 — Grafana 서비스계정 API 토큰; 없으면 skip)
  APP_LABEL      (기본 snuclear-server)  ENV_LABEL (기본 prod)
  SUGANG_URL     (기본 prod sugang-period)
  STEP_SECONDS   (기본 60)
  FORCE          ("1"이면 '구간 종료 후'·'이미 캡처됨' 검사 무시 — 수동 재캡처용)
"""
import gzip
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from datetime import datetime, timezone, timedelta

KST = timezone(timedelta(hours=9))

GRAFANA_URL = os.environ.get("GRAFANA_URL", "https://grafana.wafflestudio.com").rstrip("/")
GRAFANA_TOKEN = os.environ.get("GRAFANA_TOKEN", "").strip()
APP = os.environ.get("APP_LABEL", "snuclear-server")
ENV = os.environ.get("ENV_LABEL", "prod")
SUGANG_URL = os.environ.get(
    "SUGANG_URL",
    "https://snuclear-server.wafflestudio.com/api/v1/syncwithsite/sugang-period",
)
STEP = int(os.environ.get("STEP_SECONDS", "60"))
FORCE = os.environ.get("FORCE", "") == "1"
CAPTURE_DIR = os.path.join(os.path.dirname(__file__), "captures")

SELECTOR = f'application="{APP}", env="{ENV}"'
BUCKET = f"http_server_requests_seconds_bucket{{{SELECTOR}}}"
COUNT = f"http_server_requests_seconds_count{{{SELECTOR}}}"
COUNT_5XX = f'http_server_requests_seconds_count{{{SELECTOR}, status=~"5.."}}'

# 대시보드와 동일한 PromQL. per-uri는 활성 엔드포인트만 반환되므로 파일이 작다.
def q_hist(quantile, by_uri):
    grp = "le, uri" if by_uri else "le"
    return f"histogram_quantile({quantile}, sum(rate({BUCKET}[5m])) by ({grp}))"

QUERIES = {
    "overall_p50": q_hist(0.5, False),
    "overall_p95": q_hist(0.95, False),
    "overall_p99": q_hist(0.99, False),
    "overall_rps": f"sum(rate({COUNT}[5m]))",
    "overall_err": f"sum(rate({COUNT_5XX}[5m])) / clamp_min(sum(rate({COUNT}[5m])), 1e-9)",
    "by_uri_p50": q_hist(0.5, True),
    "by_uri_p95": q_hist(0.95, True),
    "by_uri_p99": q_hist(0.99, True),
    "by_uri_rps": f"sum(rate({COUNT}[5m])) by (uri)",
    "by_uri_err": f"sum(rate({COUNT_5XX}[5m])) by (uri) / clamp_min(sum(rate({COUNT}[5m])) by (uri), 1e-9)",
}


def http_get(url, headers=None, timeout=30):
    req = urllib.request.Request(url, headers=headers or {})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode())


def parse_dates(date_str):
    return re.findall(r"\d{4}-\d{2}-\d{2}", date_str)


def compute_window():
    """sugang-period → (semester, start_epoch, end_epoch, meta) 또는 None."""
    data = http_get(SUGANG_URL)
    header = data.get("header", "")
    m = re.search(r"(\d{4})학년도\s*(\d)학기", header)
    semester = f"{m.group(1)}-{m.group(2)}" if m else None

    def find(pred):
        for row in data.get("body", []):
            if pred(row.get("category", "")):
                return row
        return None

    start_row = find(lambda c: "예비" not in c and "장바구니신청" in c and "1일차" in c)
    end_row = find(lambda c: "예비" not in c and "선착순수강신청" in c and "2일차" in c)
    if not (semester and start_row and end_row):
        return None

    start_date = parse_dates(start_row["date"])[0]
    end_date = parse_dates(end_row["date"])[-1]
    start_dt = datetime.strptime(start_date, "%Y-%m-%d").replace(tzinfo=KST)
    end_dt = datetime.strptime(end_date, "%Y-%m-%d").replace(
        hour=23, minute=59, second=59, tzinfo=KST
    )
    meta = {
        "semester": semester,
        "start": start_dt.isoformat(),
        "end": end_dt.isoformat(),
        "start_category": start_row["category"],
        "end_category": end_row["category"],
    }
    return semester, int(start_dt.timestamp()), int(end_dt.timestamp()), meta


def ds_uid():
    r = http_get(
        f"{GRAFANA_URL}/api/datasources/name/Prometheus",
        headers={"Authorization": f"Bearer {GRAFANA_TOKEN}"},
    )
    return r["uid"]


def query_range(uid, expr, start, end):
    params = urllib.parse.urlencode(
        {"query": expr, "start": start, "end": end, "step": STEP}
    )
    url = f"{GRAFANA_URL}/api/datasources/proxy/uid/{uid}/api/v1/query_range?{params}"
    r = http_get(url, headers={"Authorization": f"Bearer {GRAFANA_TOKEN}"})
    return r.get("data", {}).get("result", [])


def main():
    if not GRAFANA_TOKEN:
        print("::notice::GRAFANA_TOKEN 미설정 — 캡처 skip (토큰 세팅 후 동작).")
        return 0

    win = compute_window()
    if not win:
        print("::warning::sugang-period에서 구간을 산출하지 못함 — skip.")
        return 0
    semester, start, end, meta = win
    print(f"학기={semester} 구간={meta['start']} ~ {meta['end']} (step={STEP}s)")

    os.makedirs(CAPTURE_DIR, exist_ok=True)
    out_path = os.path.join(CAPTURE_DIR, f"{semester}.json.gz")

    now = int(datetime.now(KST).timestamp())
    if not FORCE:
        if os.path.exists(out_path):
            print(f"::notice::{semester} 이미 캡처됨 ({out_path}) — skip.")
            return 0
        if now <= end:
            print("::notice::구간이 아직 안 끝남 — 종료 후 캡처. skip.")
            return 0

    uid = ds_uid()
    captured = {}
    for name, expr in QUERIES.items():
        result = query_range(uid, expr, start, end)
        captured[name] = result
        n = sum(len(s.get("values", [])) for s in result)
        print(f"  {name}: series={len(result)} points={n}")

    payload = {
        "meta": meta,
        "captured_at": datetime.now(KST).isoformat(),
        "step_seconds": STEP,
        "labels": {"application": APP, "env": ENV},
        "queries": QUERIES,
        "data": captured,
    }
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode()
    with gzip.open(out_path, "wb") as f:
        f.write(raw)
    print(f"저장: {out_path} ({len(raw)} bytes raw)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
