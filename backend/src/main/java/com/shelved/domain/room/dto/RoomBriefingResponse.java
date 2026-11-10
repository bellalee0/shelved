package com.shelved.domain.room.dto;

import com.shelved.common.config.GameProperties;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.Suspect;
import com.shelved.common.enums.Difficulty;
import java.util.List;

/**
 * 사전 정보. 원작·범인·트릭·용어집은 포함하지 않는다
 */
public record RoomBriefingResponse(
        String displayTitle,
        Difficulty difficulty,
        String introduction,
        String investigationGoal,
        String backgroundKnowledge,
        int timeLimitSec,
        int hintLimit,
        int accusationLimit,
        List<BriefingSuspect> suspects
) {

    public static RoomBriefingResponse from(Room room, GameProperties gameProperties, List<Suspect> suspects) {
        MysteryCase mysteryCase = room.getMysteryCase();
        Difficulty difficulty = mysteryCase.getDifficulty();

        return new RoomBriefingResponse(
                mysteryCase.getDisplayTitle(),
                difficulty,
                mysteryCase.getIntroduction(),
                mysteryCase.getInvestigationGoal(),
                mysteryCase.getBackgroundKnowledge(),
                room.getTimeLimitSec(),
                gameProperties.hintLimitOf(difficulty),
                gameProperties.accusationLimit(),
                suspects.stream().map(BriefingSuspect::from).toList()
        );
    }

    /**
     * 등장인물. 범인 여부·role·persona는 포함하지 않는다
     */
    public record BriefingSuspect(Long suspectId, String name, String spriteKey, String publicIntro) {

        public static BriefingSuspect from(Suspect suspect) {
            return new BriefingSuspect(suspect.getId(), suspect.getName(), suspect.getSpriteKey(), suspect.getPublicIntro());
        }
    }
}
