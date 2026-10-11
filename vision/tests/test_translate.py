import json
import os
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from fastapi.testclient import TestClient

from app import main
from tests.test_scan import FakeResponses, api_error


class TranslateApiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(main.app)

    def translate(self, body, fake):
        client = SimpleNamespace(responses=fake)
        with patch.dict(os.environ, {"OPENAI_API_KEY": "test-key"}), patch.object(main, "get_client", return_value=client):
            return self.client.post("/v1/translate", json=body)

    def test_each_target_locale_is_returned_once_in_request_order(self):
        out = json.dumps({"tg": {"name": "Қайсии хушк", "description": "Бе донак."},
                          "ru": {"name": "Курага", "description": "Без косточек."}}, ensure_ascii=False)
        fake = FakeResponses(out)
        res = self.translate({"source_locale": "ko", "target_locales": ["ru", "tg"], "name": "말린 살구",
                              "description": "씨를 뺀 말린 살구.", "category_name": "말린 과일"}, fake)
        self.assertEqual(res.status_code, 200)
        body = res.json()
        self.assertEqual([t["locale"] for t in body["translations"]], ["ru", "tg"])
        self.assertEqual(body["translations"][0]["name"], "Курага")
        self.assertEqual(body["prompt_version"], "product_translate_v1")
        schema = fake.request["text_format"].model_json_schema()
        self.assertEqual(set(schema["required"]), {"ru", "tg"})
        self.assertEqual([p["type"] for p in fake.request["input"][0]["content"]], ["input_text"])   # 사진 없이 글만 보낸다
        prompt = fake.request["input"][0]["content"][0]["text"]
        self.assertIn("말린 살구", prompt)
        self.assertIn("키릴 문자", prompt)

    def test_without_description_schema_has_no_description(self):
        fake = FakeResponses('{"en": {"name": "Dried apricots"}}')
        res = self.translate({"source_locale": "ko", "target_locales": ["en"], "name": "말린 살구"}, fake)
        self.assertEqual(res.status_code, 200)
        self.assertIsNone(res.json()["translations"][0]["description"])
        item = fake.request["text_format"].model_json_schema()["$defs"]["ModelTranslation"]
        self.assertEqual(list(item["properties"]), ["name"])

    def test_long_name_is_cut_to_db_length(self):
        fake = FakeResponses(json.dumps({"ru": {"name": "А" * 200}}))
        res = self.translate({"source_locale": "ko", "target_locales": ["ru"], "name": "이름"}, fake)
        self.assertEqual(len(res.json()["translations"][0]["name"]), 120)

    def test_source_locale_cannot_be_a_target_and_duplicates_are_rejected(self):
        fake = FakeResponses("{}")
        self.assertEqual(self.translate({"source_locale": "ko", "target_locales": ["ko"], "name": "x"}, fake).status_code, 400)
        self.assertEqual(self.translate({"source_locale": "ko", "target_locales": ["ru", "ru"], "name": "x"}, fake).status_code, 422)
        self.assertIsNone(fake.request, "잘못된 요청은 OpenAI를 부르지 않는다")

    def test_rate_limit_is_passed_through(self):
        fake = FakeResponses(api_error(429))
        res = self.translate({"source_locale": "ko", "target_locales": ["ru"], "name": "x"}, fake)
        self.assertEqual(res.status_code, 429)


if __name__ == "__main__":
    unittest.main()
