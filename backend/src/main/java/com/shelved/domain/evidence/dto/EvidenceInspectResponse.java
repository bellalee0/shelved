package com.shelved.domain.evidence.dto;

import com.shelved.common.entity.Evidence;

public record EvidenceInspectResponse(
        Long evidenceId,
        String name,
        String description
) {

    public static EvidenceInspectResponse from(Evidence evidence) {
        return new EvidenceInspectResponse(
                evidence.getId(),
                evidence.getName(),
                evidence.getDescription()
        );
    }
}
