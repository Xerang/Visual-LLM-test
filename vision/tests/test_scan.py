import io
import json
import os
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from fastapi.testclient import TestClient
from PIL import Image

from app import main

CATEGORIES = [{"code": "food.dried_fruit", "name": "말린 과일"}, {"code": "other", "name": "기타"}]


class FakeInteractions:
    def __init__(self, output):
        self.output = output
        self.request = None

    def create(self, **kwargs):
        self.request = kwargs
        if isinstance(self.output, Exception):
            raise self.output
        return SimpleNamespace(output_text=self.output)


class ScanApiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(main.app)
        buf = io.BytesIO()
        Image.new("RGB", (20, 15), "orange").save(buf, format="PNG")
        cls.image = buf.getvalue()

    def scan(self, request, fake=None, images=1):
        files = [("images", (f"p{i}.png", self.image, "image/png")) for i in range(images)]
        env = {"GEMINI_API_KEY": "test-key"}
        client = SimpleNamespace(interactions=fake or FakeInteractions('{"candidates":[],"image_notes":[]}'))
        with patch.dict(os.environ, env), patch.object(main, "get_client", return_value=client):
            return self.client.post("/v1/scan", files=files, data={"request": json.dumps(request)})

    def test_caption_price_and_category_enum(self):
        out = json.dumps({"candidates": [{"name": "말린 살구", "description": "주황색", "category_code": "food.dried_fruit",
                                          "visible_text": [], "review_notes": [], "suggested_price": 45}],
                          "image_notes": []}, ensure_ascii=False)
        fake = FakeInteractions(out)
        res = self.scan({"output_locale": "ru", "caption_text": "Курага 45", "categories": CATEGORIES}, fake, images=2)
        self.assertEqual(res.status_code, 200)
        body = res.json()
        self.assertEqual(body["candidates"][0]["suggested_price"], 45)
        self.assertEqual(body["prompt_version"], "product_scan_v3")
        schema = json.dumps(fake.request["response_format"]["schema"])
        self.assertIn("food.dried_fruit", schema)
        self.assertIn("러시아어", fake.request["input"][0]["text"])
        self.assertEqual([p["type"] for p in fake.request["input"]], ["text", "image", "image"])

    def test_no_caption_means_no_price_field(self):
        fake = FakeInteractions('{"candidates":[{"name":"x","description":"y","category_code":"other",'
                                '"visible_text":[],"review_notes":[]}],"image_notes":[]}')
        res = self.scan({"output_locale": "ko", "categories": CATEGORIES}, fake)
        self.assertEqual(res.status_code, 200)
        self.assertIsNone(res.json()["candidates"][0]["suggested_price"])
        self.assertNotIn("suggested_price", json.dumps(fake.request["response_format"]["schema"]))

    def test_unknown_category_from_model_is_rejected(self):
        fake = FakeInteractions('{"candidates":[{"name":"x","description":"y","category_code":"cars",'
                                '"visible_text":[],"review_notes":[]}],"image_notes":[]}')
        self.assertEqual(self.scan({"output_locale": "ko", "categories": CATEGORIES}, fake).status_code, 502)

    def test_bad_request_and_too_many_images(self):
        self.assertEqual(self.scan({"output_locale": "uz", "categories": CATEGORIES}).status_code, 400)
        self.assertEqual(self.scan({"output_locale": "ko", "categories": CATEGORIES}, images=4).status_code, 400)

    def test_rate_limit_is_passed_through_as_429(self):
        err = Exception("limit")
        err.status_code = 429
        res = self.scan({"output_locale": "ko", "categories": CATEGORIES}, FakeInteractions(err))
        self.assertEqual(res.status_code, 429)

    def test_gemini_overload_is_retryable_503_not_permanent_502(self):
        # 실제로 받은 응답: 503 "gemini-3.5-flash is currently experiencing high demand"
        for status in (500, 503):
            err = Exception("high demand")
            err.status_code = status
            res = self.scan({"output_locale": "ko", "categories": CATEGORIES}, FakeInteractions(err))
            self.assertEqual(res.status_code, 503, f"Gemini {status}")

    def test_bad_request_from_gemini_stays_permanent_502(self):
        err = Exception("bad schema")
        err.status_code = 400
        res = self.scan({"output_locale": "ko", "categories": CATEGORIES}, FakeInteractions(err))
        self.assertEqual(res.status_code, 502)


if __name__ == "__main__":
    unittest.main()
