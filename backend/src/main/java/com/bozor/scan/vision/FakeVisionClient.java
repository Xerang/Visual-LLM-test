package com.bozor.scan.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Gemini 없이 고정된 가짜 후보를 돌려준다(app.vision.mode=fake).
 * 무료 키의 일일 한도를 아끼면서 프런트·검수 화면을 개발하고, 테스트에서 AI 결과를 고정하기 위해 쓴다.
 *
 * 캡션에 아래 문구를 넣으면 실패를 흉내 낸다(재시도 화면 확인용).
 * - "#fail429" → 요청 한도 초과(재시도됨)
 * - "#fail"    → 응답 형식 오류(바로 실패)
 * - "#empty"   → 상품을 하나도 찾지 못함(후보 0개, "상품 없음" 검수 확인용)
 */
@Component
@ConditionalOnProperty(prefix = "app.vision", name = "mode", havingValue = "fake")
public class FakeVisionClient implements VisionClient {
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:[.,]\\d+)?)");
    private final ObjectMapper json;

    public FakeVisionClient(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public VisionScanResult scan(VisionScanRequest request) {
        String caption = request.captionText() == null ? "" : request.captionText();
        if (caption.contains("#fail429")) {
            throw new VisionException(VisionException.RATE_LIMITED, true, "가짜 AI: 요청 한도 초과를 흉내 냈습니다.");
        }
        if (caption.contains("#fail")) {
            throw new VisionException(VisionException.BAD_RESPONSE, false, "가짜 AI: 응답 형식 오류를 흉내 냈습니다.");
        }
        if (caption.contains("#empty")) {
            return new VisionScanResult(raw(List.of()), "fake", "fake_v1", 5, List.of(),
                    List.of("가짜 AI: 상품을 찾지 못한 경우를 흉내 냈습니다."));
        }
        VisionScanRequest.CategoryOption first = pick(request.categories(), "food.dried_fruit", 0);
        VisionScanRequest.CategoryOption second = pick(request.categories(), "household.etc", request.categories().size() - 1);
        List<VisionScanResult.Candidate> candidates = new ArrayList<>();
        candidates.add(new VisionScanResult.Candidate("가짜 후보: " + first.name(),
                "가짜 AI가 만든 설명입니다. 실제 사진 분석 결과가 아닙니다.", first.code(),
                List.of(), List.of("가짜 AI 응답입니다. 실제 값으로 고쳐 주세요."), priceFrom(caption)));
        candidates.add(new VisionScanResult.Candidate("가짜 후보: " + second.name(),
                "두 번째 가짜 후보입니다.", second.code(), List.of(), List.of(), null));
        return new VisionScanResult(raw(candidates), "fake", "fake_v1", 5, candidates,
                List.of("가짜 AI(app.vision.mode=fake) 응답입니다."));
    }

    private static VisionScanRequest.CategoryOption pick(List<VisionScanRequest.CategoryOption> options,
                                                         String code, int fallbackIndex) {
        return options.stream().filter(o -> o.code().equals(code)).findFirst()
                .orElse(options.get(Math.max(0, fallbackIndex)));
    }

    /** 실제 프롬프트 규칙과 같게, 캡션에 적힌 숫자만 가격으로 쓴다. */
    private static BigDecimal priceFrom(String caption) {
        Matcher m = NUMBER.matcher(caption);
        return m.find() ? new BigDecimal(m.group(1).replace(',', '.')) : null;
    }

    private ObjectNode raw(List<VisionScanResult.Candidate> candidates) {
        ObjectNode root = json.createObjectNode();
        ArrayNode list = root.putArray("candidates");
        for (VisionScanResult.Candidate c : candidates) {
            ObjectNode n = list.addObject();
            n.put("name", c.name());
            n.put("description", c.description());
            n.put("category_code", c.categoryCode());
            n.putArray("visible_text");
            ArrayNode notes = n.putArray("review_notes");
            c.reviewNotes().forEach(notes::add);
            n.put("suggested_price", c.suggestedPrice());
        }
        root.putArray("image_notes").add("가짜 AI(app.vision.mode=fake) 응답입니다.");
        root.put("model", "fake");
        root.put("prompt_version", "fake_v1");
        root.put("elapsed_ms", 5);
        return root;
    }
}
