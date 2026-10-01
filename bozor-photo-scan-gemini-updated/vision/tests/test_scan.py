import io
import importlib.metadata
import json
import os
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import httpx
from fastapi.testclient import TestClient
from google import genai
from google.genai.errors import ClientError
from PIL import Image

from app import main


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
    def test_interactions_sdk_uses_supported_schema(self):
        version = importlib.metadata.version("google-genai")
        self.assertGreaterEqual(int(version.split(".")[0]), 2)

    def test_sdk_2_serializes_photo_and_json_request(self):
        sent = []

        def fake_send(_client, request, **_kwargs):
            sent.append((request.url.path, json.loads(request.content)))
            return httpx.Response(200, json={
                "id": "int_test",
                "steps": [{"type": "model_output", "content": [{
                    "type": "text", "text": '{"candidates":[],"image_notes":[]}'
                }]}],
            }, request=request)

        proxy_env = {name: "" for name in (
            "ALL_PROXY", "HTTPS_PROXY", "HTTP_PROXY", "all_proxy", "https_proxy", "http_proxy"
        )}
        with patch.dict(os.environ, proxy_env), patch.object(httpx.Client, "send", fake_send):
            sdk = genai.Client(api_key="test-key")
            with patch.object(main, "get_client", return_value=sdk):
                result = main.scan_with_gemini(b"jpeg-bytes")
            sdk.close()

        self.assertEqual(result.candidates, [])
        self.assertEqual(sent[0][0], "/v1beta/interactions")
        body = sent[0][1]
        self.assertEqual(body["input"][0]["type"], "user_input")
        self.assertEqual([entry["type"] for entry in body["input"][0]["content"]], ["text", "image"])
        self.assertEqual(body["response_format"]["mime_type"], "application/json")

    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(main.app)
        buffer = io.BytesIO()
        Image.new("RGB", (20, 15), "white").save(buffer, format="PNG")
        cls.image = buffer.getvalue()

    def upload(self, data=None, name="product.png"):
        return self.client.post("/analyze", files={"file": (name, self.image if data is None else data, "image/png")})

    def test_invalid_file_and_missing_key(self):
        self.assertEqual(self.upload(data=b"not a picture").status_code, 400)
        with patch.dict(os.environ, {"GEMINI_API_KEY": "replace_with_your_key"}):
            self.assertEqual(self.upload().status_code, 503)

    def test_multiple_candidates_preserved_for_seller_selection(self):
        output = json.dumps({"candidates": [
            {"name": "컵", "category": "household", "description": "흰색 컵이 보입니다.",
             "visible_text": [], "review_notes": []},
            {"name": "셔츠", "category": "clothing", "description": "회색 셔츠가 보입니다.",
             "visible_text": ["TEST"], "review_notes": ["문자 확인 필요"]},
        ], "image_notes": []}, ensure_ascii=False)
        fake = FakeInteractions(output)
        with patch.dict(os.environ, {"GEMINI_API_KEY": "test-key"}), \
             patch.object(main, "get_client", return_value=SimpleNamespace(interactions=fake)):
            response = self.upload()
        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual([c["name"] for c in body["candidates"]], ["컵", "셔츠"])
        self.assertIsNone(body["candidates"][0]["price_amount"])
        self.assertIsNone(body["candidates"][1]["stock_quantity"])
        self.assertEqual(body["source"], "gemini")
        self.assertEqual(body["prompt_version"], "product_scan_v2")
        self.assertEqual(fake.request["response_format"]["mime_type"], "application/json")
        self.assertEqual(fake.request["input"][1]["mime_type"], "image/jpeg")

    def test_malformed_model_response_is_not_silently_accepted(self):
        fake = FakeInteractions('{"candidates": [{"name": "컵"}], "image_notes": []}')
        with patch.dict(os.environ, {"GEMINI_API_KEY": "test-key"}), \
             patch.object(main, "get_client", return_value=SimpleNamespace(interactions=fake)):
            response = self.upload()
        self.assertEqual(response.status_code, 502)

    def test_provider_model_error_is_specific_without_leaking_key(self):
        problem = ClientError(404, {"error": {"code": 404, "status": "NOT_FOUND",
                                               "message": "model not available"}})
        fake = FakeInteractions(problem)
        with patch.dict(os.environ, {"GEMINI_API_KEY": "test-key"}), \
             patch.object(main, "get_client", return_value=SimpleNamespace(interactions=fake)):
            response = self.upload()
        self.assertEqual(response.status_code, 502)
        self.assertIn("404", response.json()["detail"])
        self.assertNotIn("test-key", response.text)

    def test_sdk_2_rate_limit_is_returned_as_http_429(self):
        original_send = httpx.Client.send

        def fake_send(client, request, **kwargs):
            if request.url.path == "/v1beta/interactions":
                return httpx.Response(429, json={"error": {
                    "message": "Rate limit exceeded for model (limit: 20 requests per day)",
                    "code": "too_many_requests",
                }}, request=request)
            return original_send(client, request, **kwargs)

        proxy_env = {name: "" for name in (
            "ALL_PROXY", "HTTPS_PROXY", "HTTP_PROXY", "all_proxy", "https_proxy", "http_proxy"
        )}
        with patch.dict(os.environ, {**proxy_env, "GEMINI_API_KEY": "test-key"}), \
             patch.object(httpx.Client, "send", fake_send):
            sdk = genai.Client(api_key="test-key", http_options={"retry_options": {"attempts": 1}})
            with patch.object(main, "get_client", return_value=sdk):
                response = self.upload()
            sdk.close()

        self.assertEqual(response.status_code, 429)
        self.assertIn("요청 한도", response.json()["detail"])
        self.assertNotIn("test-key", response.text)

    def test_gemini_timeout_is_reported_before_spring_read_timeout(self):
        fake = FakeInteractions(TimeoutError("request timed out"))
        with patch.dict(os.environ, {"GEMINI_API_KEY": "test-key"}), \
             patch.object(main, "get_client", return_value=SimpleNamespace(interactions=fake)):
            response = self.upload()
        self.assertEqual(response.status_code, 504)
        self.assertIn("대기 시간", response.json()["detail"])


if __name__ == "__main__":
    unittest.main()
