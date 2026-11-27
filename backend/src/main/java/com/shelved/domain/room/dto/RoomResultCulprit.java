package com.shelved.domain.room.dto;

import com.shelved.common.entity.Suspect;

/**
 * 범인 공개 정보. 종료 후 결과 응답에서만 쓴다
 */
public record RoomResultCulprit(
        Long suspectId,
        String name
) {

    public static RoomResultCulprit from(Suspect suspect) {
        return new RoomResultCulprit(suspect.getId(), suspect.getName());
    }

    // 정답이 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "RoomResultCulprit[]";
    }
}
