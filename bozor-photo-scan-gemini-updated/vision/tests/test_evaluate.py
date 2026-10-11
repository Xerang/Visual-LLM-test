import io
import json
import tempfile
import unittest
from pathlib import Path

from fastapi import HTTPException
from PIL import Image

from app import main
from tools import evaluate

CATEGORIES = [main.CategoryOption(code=c, name=c) for c in ["food.dried_fruit", "food.nuts", "household.kitchen", "other"]]


def png(color: str) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (16, 12), color).save(buf, format="PNG")
    return buf.getvalue()


class ScoreTest(unittest.TestCase):
    def test_multiset_matching_counts_duplicates_once_per_gold_item(self):
        s = evaluate.Score()
        s.add(["food.nuts", "food.nuts", "other"], ["food.nuts", "food.dried_fruit"])
        self.assertEqual(s.matched, 1)
        self.assertAlmostEqual(s.precision, 1 / 3)
        self.assertAlmostEqual(s.recall, 1 / 2)
        self.assertEqual(s.exact_rate, 0)

    def test_top_level_is_more_lenient_than_leaf(self):
        rows = [{"ok": True, "elapsed_ms": 1000, "predicted": ["food.nuts"], "expected": ["food.dried_fruit"]},
                {"ok": False, "status": 502}]
        s = evaluate.summarize(rows)
        self.assertEqual((s["items"], s["errors"]), (1, 1))
        self.assertEqual(s["leaf_precision"], 0)
        self.assertEqual(s["top_precision"], 1)
        self.assertEqual(s["top_exact"], 1)

    def test_empty_prediction_gives_no_precision_but_zero_recall(self):
        s = evaluate.Score()
        s.add([], ["food.nuts"])
        self.assertIsNone(s.precision)
        self.assertEqual(s.recall, 0)


class GoldsetTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        (self.dir / "a.png").write_bytes(png("orange"))

    def write(self, items):
        path = self.dir / "gold.json"
        path.write_text(json.dumps({"items": items}), encoding="utf-8")
        return path

    def test_rejects_unknown_category_and_missing_file(self):
        leaf = {c.code for c in CATEGORIES}
        with self.assertRaisesRegex(ValueError, "없는 카테고리"):
            evaluate.load_goldset(self.write([{"id": "1", "images": ["a.png"], "expected": ["food"]}]), leaf)
        with self.assertRaisesRegex(ValueError, "사진 파일이 없다"):
            evaluate.load_goldset(self.write([{"id": "1", "images": ["x.png"], "expected": ["other"]}]), leaf)

    def test_categories_tree_is_flattened_to_leaves_like_the_service(self):
        tree = [{"code": "food", "name": "식품", "children": [{"code": "food.nuts", "name": "견과류", "children": []}]},
                {"code": "other", "name": "기타", "children": []}]
        path = self.dir / "cat.json"
        path.write_text(json.dumps(tree, ensure_ascii=False), encoding="utf-8")
        self.assertEqual([c.code for c in evaluate.load_categories(str(path), "")], ["food.nuts", "other"])


class RunTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        for name, color in [("a.png", "orange"), ("b.png", "brown")]:
            (self.dir / name).write_bytes(png(color))
        gold = self.dir / "gold.json"
        gold.write_text(json.dumps({"items": [
            {"id": "a", "images": ["a.png"], "caption": "Курага 45", "expected": ["food.dried_fruit"]},
            {"id": "b", "images": ["b.png"], "expected": ["food.nuts", "household.kitchen"]},
        ]}), encoding="utf-8")
        self.items = evaluate.load_goldset(gold, {c.code for c in CATEGORIES})
        self.out = self.dir / "out"
        self.calls = []

    def fake_scan(self, codes_by_model, fail_429_on=None):
        def scan(req, images, model):
            self.calls.append((model, req.caption_text))
            if fail_429_on and (model, len(self.calls)) == fail_429_on:
                raise HTTPException(status_code=429, detail="limit")
            return [main.Candidate(name=c, description=None, category_code=c, visible_text=[], review_notes=[],
                                   suggested_price=None) for c in codes_by_model[model]], []
        return scan

    def test_results_are_cached_so_rerun_spends_no_quota(self):
        scan = self.fake_scan({"m1": ["food.dried_fruit"]})
        calls, stopped = evaluate.evaluate(self.items, ["m1"], CATEGORIES, "ko", self.out, 10, 0, scan)
        self.assertEqual((calls, stopped), (2, None))
        calls, _ = evaluate.evaluate(self.items, ["m1"], CATEGORIES, "ko", self.out, 10, 0, scan)
        self.assertEqual(calls, 0)
        self.assertEqual(self.calls[0], ("m1", "Курага 45"))   # 캡션이 그대로 전달된다

    def test_rate_limit_stops_immediately_and_resumes_later(self):
        scan = self.fake_scan({"m1": ["food.dried_fruit"], "m2": ["other"]}, fail_429_on=("m2", 3))
        calls, stopped = evaluate.evaluate(self.items, ["m1", "m2"], CATEGORIES, "ko", self.out, 10, 0, scan)
        self.assertEqual(calls, 3)
        self.assertIn("429", stopped)
        calls, stopped = evaluate.evaluate(self.items, ["m1", "m2"], CATEGORIES, "ko", self.out, 10, 0,
                                           self.fake_scan({"m1": [], "m2": ["other"]}))
        self.assertEqual((calls, stopped), (2, None))   # m2 의 두 장만 다시 부른다

    def test_report_scores_each_model_on_current_goldset(self):
        scan = self.fake_scan({"good": ["food.dried_fruit"], "bad": ["other"]})
        evaluate.evaluate(self.items, ["good", "bad"], CATEGORIES, "ko", self.out, 10, 0, scan)
        text = evaluate.report(self.items, ["good", "bad"], self.out)
        # good: a 1/1 맞음, b 0/1(예측 food.dried_fruit, 정답 2개) → 정밀도 1/2, 재현율 1/3
        self.assertIn("| good | 2 | 0 | 50% / 100% | 33% / 67% |", text)
        self.assertIn("| bad | 2 | 0 | 0% / 0% | 0% / 0% |", text)
        self.assertTrue((self.out / "summary.md").exists())

    def test_max_calls_limits_quota_use(self):
        calls, stopped = evaluate.evaluate(self.items, ["m1"], CATEGORIES, "ko", self.out, 1, 0,
                                           self.fake_scan({"m1": ["other"]}))
        self.assertEqual(calls, 1)
        self.assertIn("--max-calls", stopped)


if __name__ == "__main__":
    unittest.main()
