"""Real SDK request/response checks using an offline HTTP transport."""
import json
import unittest
from unittest.mock import patch
import httpx
from openai import OpenAI
from fastapi import HTTPException
from app import main

class OpenAITransportTest(unittest.TestCase):
    def call(self, content=None, status=200, response_status="completed", error=None):
        self.requests = []
        def handler(request):
            self.requests.append(json.loads(request.content))
            if error:
                raise error
            payload = {"id": "resp_test", "object": "response", "created_at": 0,
                       "model": main.MODEL_NAME, "status": response_status,
                       "output": [{"id": "msg_test", "type": "message", "role": "assistant",
                                   "status": "completed", "content": content or []}]}
            return httpx.Response(status, json=payload if status == 200 else {"error": {"message": "test"}})
        with OpenAI(api_key="test-key", max_retries=0,
                    http_client=httpx.Client(transport=httpx.MockTransport(handler))) as client:
            with patch.object(main, "get_client", return_value=client):
                return main.call_openai("scan", [b"jpeg"], main.model_schema(["other"], True))

    def test_sdk_serializes_strict_schema_and_images(self):
        result = self.call([{"type": "output_text", "text": '{"candidates":[],"image_notes":[]}', "annotations": []}])
        self.assertEqual(result.candidates, [])
        body = self.requests[0]
        self.assertFalse(body["store"])
        fmt = body["text"]["format"]
        self.assertTrue(fmt["strict"])
        self.assertFalse(fmt["schema"]["additionalProperties"])
        self.assertFalse(fmt["schema"]["$defs"]["ModelCandidate"]["additionalProperties"])
        image = body["input"][0]["content"][1]
        self.assertEqual(image["detail"], "high")
        self.assertEqual(image["image_url"], "data:image/jpeg;base64,anBlZw==")

    def test_refusal_and_incomplete_are_not_empty_success(self):
        for content, status in [([{"type": "refusal", "refusal": "test"}], "completed"), ([], "incomplete")]:
            with self.subTest(status=status), self.assertRaises(HTTPException) as caught:
                self.call(content, response_status=status)
            self.assertEqual(caught.exception.status_code, 502)

    def test_sdk_errors_keep_retry_contract(self):
        for upstream, expected in [(400,502),(401,502),(403,502),(404,502),(429,429),
                                   (408,504),(500,503),(502,503),(503,503),(504,504)]:
            with self.subTest(upstream=upstream), self.assertRaises(HTTPException) as caught:
                self.call(status=upstream)
            self.assertEqual(caught.exception.status_code, expected)
            self.assertEqual(len(self.requests), 1)
        for error, expected in [(httpx.ReadTimeout("test"),504),(httpx.ConnectError("test"),503)]:
            with self.subTest(error=error), self.assertRaises(HTTPException) as caught:
                self.call(error=error)
            self.assertEqual(caught.exception.status_code, expected)

    def test_placeholder_key_and_health(self):
        for key in ["", " ", "replace_with_your_key"]:
            with patch.dict(main.os.environ, {"OPENAI_API_KEY": key}):
                self.assertFalse(main.key_is_configured())
                self.assertEqual(main.health()["provider"], "openai")
