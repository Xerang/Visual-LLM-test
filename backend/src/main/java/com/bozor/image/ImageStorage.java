package com.bozor.image;

/**
 * 사진 파일 저장소. 지금은 LocalImageStorage(로컬 디스크)를 쓰고,
 * 실증 전에 S3 구현체로 바꾼다(임시 결정 #6). DB에는 key만 저장하므로 테이블은 그대로다.
 */
public interface ImageStorage {
    void save(String key, byte[] bytes);

    byte[] load(String key);
}
