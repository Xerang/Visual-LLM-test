"""모델 비교 도구: 같은 정답셋을 여러 Gemini 모델로 돌려 같은 지표로 비교한다.

근거: 4차 회의 차주 계획 "동일 사진을 대상으로 기존 모델과 다른 AI API 모델의 결과를 비교한다".
서비스와 같은 함수(app.main.scan_images)를 부르므로 측정값은 실제 /v1/scan 의 성능이다.

사용:
    cd vision
    .venv/bin/python -m tools.evaluate goldset.json --models gemini-3.5-flash,gemini-3.5-flash-lite

정답셋(goldset.json) 형식 — 사진 경로는 정답셋 파일 기준 상대 경로:
    {
      "items": [
        {"id": "001", "images": ["photos/001.jpg"], "caption": null,
         "expected": ["food.dried_fruit", "food.nuts"]},
        {"id": "002", "images": ["photos/002a.jpg", "photos/002b.jpg"], "caption": "Курага 45 сомони/кг",
         "expected": ["food.dried_fruit"]}
      ]
    }
    expected 는 사진에 들어 있는 상품마다 정답 소분류 코드 하나씩(같은 카테고리 상품이 둘이면 두 번).

채점 규칙(카테고리 기준, 다중집합 일치):
    맞은 수   = 예측 코드 목록과 정답 코드 목록에 공통으로 들어 있는 개수(중복 포함)
    정밀도    = 맞은 수 / 예측 후보 수      (엉뚱한 후보를 덜 낼수록 높다)
    재현율    = 맞은 수 / 정답 상품 수      (상품을 덜 놓칠수록 높다)
    완전 일치 = 예측 목록과 정답 목록이 똑같은 사진의 비율
    대분류 지표는 코드의 앞부분(food.nuts → food)으로 같은 계산을 한다.
한계: 상품명·설명의 질, 캡션 가격 추출은 채점하지 않는다(사람이 따로 본다).

무료 한도 보호:
    - 결과를 <out>/results.jsonl 에 저장하고, 다시 실행하면 이미 성공한 (모델, 사진) 조합은 다시 부르지 않는다.
    - 429(요청 한도)가 나면 그 자리에서 멈춘다. 다음 날 같은 명령을 다시 실행하면 이어서 한다.
    - --max-calls 로 이번 실행의 최대 호출 수, --sleep 으로 호출 사이 대기 시간을 정한다.
"""

import argparse
import hashlib
import json
import statistics
import sys
import time
import urllib.request
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from fastapi import HTTPException

from app import main


# ---- 채점 -----------------------------------------------------------------

def top_level(code: str) -> str:
    return code.split(".")[0]


@dataclass
class Score:
    predicted: int = 0
    expected: int = 0
    matched: int = 0
    exact: int = 0
    items: int = 0

    def add(self, predicted: list[str], expected: list[str]) -> None:
        self.predicted += len(predicted)
        self.expected += len(expected)
        self.matched += sum((Counter(predicted) & Counter(expected)).values())
        self.exact += int(Counter(predicted) == Counter(expected))
        self.items += 1

    @property
    def precision(self) -> float | None:
        return self.matched / self.predicted if self.predicted else None

    @property
    def recall(self) -> float | None:
        return self.matched / self.expected if self.expected else None

    @property
    def exact_rate(self) -> float | None:
        return self.exact / self.items if self.items else None


def summarize(rows: list[dict]) -> dict:
    """한 모델의 결과 줄들로 지표를 계산한다. 실패한 호출은 지표에서 빼고 따로 센다."""
    ok = [r for r in rows if r["ok"]]
    leaf, top = Score(), Score()
    for r in ok:
        leaf.add(r["predicted"], r["expected"])
        top.add([top_level(c) for c in r["predicted"]], [top_level(c) for c in r["expected"]])
    times = [r["elapsed_ms"] for r in ok]
    return {
        "items": len(ok),
        "errors": len(rows) - len(ok),
        "leaf_precision": leaf.precision, "leaf_recall": leaf.recall, "leaf_exact": leaf.exact_rate,
        "top_precision": top.precision, "top_recall": top.recall, "top_exact": top.exact_rate,
        "median_ms": statistics.median(times) if times else None,
        "mean_ms": statistics.mean(times) if times else None,
    }


# ---- 정답셋·카테고리 ----------------------------------------------------------

def load_categories(path: str | None, url: str) -> list[main.CategoryOption]:
    """서비스와 같은 허용 목록: 활성 말단 카테고리. GET /api/v1/categories 응답(트리)을 받는다."""
    if path:
        tree = json.loads(Path(path).read_text(encoding="utf-8"))
    else:
        with urllib.request.urlopen(url, timeout=10) as res:
            tree = json.loads(res.read().decode("utf-8"))
    leaves = []
    for root in tree:
        nodes = root["children"] or [root]
        leaves += [main.CategoryOption(code=n["code"], name=n["name"]) for n in nodes]
    return sorted(leaves, key=lambda c: c.code)   # 서비스(ScanJobProcessor)와 같은 순서


def load_goldset(path: Path, leaf_codes: set[str]) -> list[dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    items, seen = [], set()
    for i, item in enumerate(data["items"]):
        where = f"items[{i}]"
        if item["id"] in seen:
            raise ValueError(f"{where}: id 중복 {item['id']}")
        seen.add(item["id"])
        unknown = [c for c in item["expected"] if c not in leaf_codes]
        if unknown:
            raise ValueError(f"{where}: 없는 카테고리 코드 {unknown} (말단 코드만 쓸 수 있다)")
        if not 1 <= len(item["images"]) <= main.MAX_IMAGES:
            raise ValueError(f"{where}: 사진은 1~{main.MAX_IMAGES}장")
        files = [path.parent / p for p in item["images"]]
        missing = [str(f) for f in files if not f.exists()]
        if missing:
            raise ValueError(f"{where}: 사진 파일이 없다 {missing}")
        items.append({**item, "files": files})
    return items


def item_key(item: dict) -> str:
    """사진 내용·캡션·정답이 바뀌면 다시 평가하도록 키에 넣는다."""
    h = hashlib.sha256()
    for f in item["files"]:
        h.update(f.read_bytes())
    h.update((item.get("caption") or "").encode())
    h.update(json.dumps(sorted(item["expected"])).encode())
    return f"{item['id']}:{h.hexdigest()[:16]}"


# ---- 실행 -----------------------------------------------------------------

def evaluate(items, models, categories, locale, out: Path, max_calls: int, sleep: float, scan=main.scan_images):
    out.mkdir(parents=True, exist_ok=True)
    results_path = out / "results.jsonl"
    done = {}
    if results_path.exists():
        for line in results_path.read_text(encoding="utf-8").splitlines():
            r = json.loads(line)
            if r["ok"]:
                done[(r["model"], r["prompt_version"], r["key"])] = r
    calls, stopped = 0, None
    with results_path.open("a", encoding="utf-8") as log:
        for model in models:
            for item in items:
                key = item_key(item)
                if (model, main.PROMPT_VERSION, key) in done:
                    continue
                if calls >= max_calls:
                    stopped = f"--max-calls {max_calls} 에 도달해 멈췄다. 다시 실행하면 이어서 한다."
                    break
                if calls and sleep:
                    time.sleep(sleep)
                calls += 1
                req = main.ScanRequest(output_locale=locale, caption_text=item.get("caption"),
                                       categories=categories)
                row = {"model": model, "prompt_version": main.PROMPT_VERSION, "key": key, "id": item["id"],
                       "expected": item["expected"]}
                started = time.monotonic()
                try:
                    images = [main.normalized_image(f.read_bytes()) for f in item["files"]]
                    candidates, _ = scan(req, images, model)
                    row.update(ok=True, elapsed_ms=round((time.monotonic() - started) * 1000),
                               predicted=[c.category_code for c in candidates],
                               names=[c.name for c in candidates])
                except HTTPException as exc:
                    row.update(ok=False, status=exc.status_code, error=str(exc.detail))
                log.write(json.dumps(row, ensure_ascii=False) + "\n")
                log.flush()
                if row["ok"]:
                    done[(model, main.PROMPT_VERSION, key)] = row
                elif row["status"] == 429:
                    stopped = f"{model}: 요청 한도(429). 한도가 풀린 뒤 같은 명령을 다시 실행하면 이어서 한다."
                    break
            if stopped:
                break
    return calls, stopped


def report(items, models, out: Path) -> str:
    """현재 정답셋에 해당하는 최신 성공 결과만으로 표를 만든다(예전 사진·정답으로 낸 결과는 제외)."""
    keys = {item_key(i) for i in items}
    latest = {}
    for line in (out / "results.jsonl").read_text(encoding="utf-8").splitlines():
        r = json.loads(line)
        if r["prompt_version"] == main.PROMPT_VERSION and r["key"] in keys and r["model"] in models:
            latest[(r["model"], r["key"])] = r   # 같은 조합은 마지막 줄이 기준
    fmt = lambda v, pct=True: "–" if v is None else (f"{v * 100:.0f}%" if pct else f"{v / 1000:.1f}s")
    lines = [f"정답셋 {len(items)}장, 프롬프트 {main.PROMPT_VERSION}", "",
             "| 모델 | 평가 | 실패 | 정밀도(소/대) | 재현율(소/대) | 완전 일치(소/대) | 시간(중앙값) |",
             "|---|---|---|---|---|---|---|"]
    for model in models:
        s = summarize([r for (m, _), r in latest.items() if m == model])
        lines.append(f"| {model} | {s['items']} | {s['errors']} | {fmt(s['leaf_precision'])} / {fmt(s['top_precision'])} "
                     f"| {fmt(s['leaf_recall'])} / {fmt(s['top_recall'])} | {fmt(s['leaf_exact'])} / {fmt(s['top_exact'])} "
                     f"| {fmt(s['median_ms'], pct=False)} |")
    text = "\n".join(lines) + "\n"
    (out / "summary.md").write_text(text, encoding="utf-8")
    return text


def main_cli(argv=None) -> int:
    p = argparse.ArgumentParser(description="정답셋으로 Gemini 모델을 비교한다.")
    p.add_argument("goldset", type=Path)
    p.add_argument("--models", default=main.MODEL_NAME, help="쉼표로 구분. 예: gemini-3.5-flash,gemini-3.5-flash-lite")
    p.add_argument("--locale", default="ko", choices=["ko", "ru", "tg", "en"])
    p.add_argument("--categories", help="GET /api/v1/categories 응답을 저장한 파일. 없으면 --categories-url 로 받는다")
    p.add_argument("--categories-url", default="http://localhost:8080/api/v1/categories?locale=ko")
    p.add_argument("--out", type=Path, default=Path("eval-results"))
    p.add_argument("--max-calls", type=int, default=20, help="이번 실행의 최대 Gemini 호출 수(무료 한도 보호)")
    p.add_argument("--sleep", type=float, default=4.0, help="호출 사이 대기 초(분당 요청 한도 보호)")
    args = p.parse_args(argv)

    if not main.key_is_configured():
        print("vision/.env 에 GEMINI_API_KEY 를 넣어야 한다.", file=sys.stderr)
        return 2
    models = [m.strip() for m in args.models.split(",") if m.strip()]
    try:
        categories = load_categories(args.categories, args.categories_url)
    except OSError as exc:   # URLError(백엔드 꺼짐)·파일 없음
        print(f"카테고리 목록을 읽지 못했다({exc}). 백엔드를 켜거나, "
              f"curl {args.categories_url} > categories.json 으로 저장한 파일을 --categories 로 지정한다.",
              file=sys.stderr)
        return 2
    try:
        items = load_goldset(args.goldset, {c.code for c in categories})
    except (OSError, ValueError, KeyError) as exc:
        print(f"정답셋 오류: {exc}", file=sys.stderr)
        return 2
    calls, stopped = evaluate(items, models, categories, args.locale, args.out, args.max_calls, args.sleep)
    print(f"이번 실행 Gemini 호출 {calls}회")
    if stopped:
        print(stopped)
    print(report(items, models, args.out))
    return 0


if __name__ == "__main__":
    sys.exit(main_cli())
