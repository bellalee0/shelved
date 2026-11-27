package com.shelved.domain.room.dto;

import com.shelved.common.entity.MysteryCase;

/**
 * 원작 공개 정보. 종료 후 결과 응답에서만 쓴다
 */
public record RoomResultSource(
        String novelTitle,
        String novelAuthor,
        String novelIntro
) {

    public static RoomResultSource from(MysteryCase mysteryCase) {
        return new RoomResultSource(
                mysteryCase.getSourceNovelTitle(),
                mysteryCase.getSourceNovelAuthor(),
                mysteryCase.getSourceNovelIntro()
        );
    }

    // 원작 정보가 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "RoomResultSource[]";
    }
}
