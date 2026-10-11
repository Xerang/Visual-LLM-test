package com.bozor.scan;

/** 스캔 관련 상태값 모음(docs/03-api.md 값 목록). */
public final class ScanEnums {
    private ScanEnums() {}

    /** QUEUED → RUNNING → SUCCEEDED | FAILED. 재시도 가능한 오류는 RUNNING → QUEUED. */
    public enum JobStatus { QUEUED, RUNNING, SUCCEEDED, FAILED }

    /** CAMERA(앱 촬영) | UPLOAD(갤러리) | SNS_IMPORT(저장해 둔 SNS 게시물 사진·캡션) */
    public enum SourceType { CAMERA, UPLOAD, SNS_IMPORT }

    /** AI = AI 제안, SELLER = AI가 놓쳐 판매자가 추가(재현율 계산용) */
    public enum Origin { AI, SELLER }

    public enum Decision { PENDING, ACCEPTED, REJECTED }
}
