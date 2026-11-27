package com.shelved.domain.cases.service;

import com.shelved.common.config.AssetsProperties;
import com.shelved.common.config.CasebuilderProperties;
import com.shelved.domain.cases.dto.DevSeedFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 시드가 지정한 표지 이미지 파일 검사. 정적 에셋 폴더 안의 일반 파일이며 크기 한도 이하여야 한다.
 * 문자열 형식 검사(DevSeedValidator)를 통과한 시드에 사용한다. 이미지 내용은 검사하지 않는다.
 */
@Component
@RequiredArgsConstructor
class DevSeedCoverImageChecker {

    private final AssetsProperties assetsProperties;
    private final CasebuilderProperties casebuilderProperties;

    /**
     * @return 실패 사유 목록. 비어 있으면 통과 (표지를 지정하지 않은 시드 포함)
     */
    List<String> check(DevSeedFile seed) {

        if (!seed.hasCoverImage()) {
            return List.of();
        }
        Path basePath = Path.of(assetsProperties.basePath()).toAbsolutePath().normalize();
        try {
            Path imagePath = basePath.resolve(seed.coverImagePath()).normalize();
            if (!imagePath.startsWith(basePath)) {
                return List.of("coverImagePath가 에셋 폴더 밖을 가리킴");
            }
            if (!Files.isRegularFile(imagePath)) {
                return List.of("coverImagePath의 파일이 없음");
            }
            // 심볼릭 링크를 따라간 실제 위치도 에셋 폴더 안이어야 한다
            if (!imagePath.toRealPath().startsWith(basePath.toRealPath())) {
                return List.of("coverImagePath가 에셋 폴더 밖을 가리킴");
            }
            if (Files.size(imagePath) > casebuilderProperties.coverImageMaxSize().toBytes()) {
                return List.of("coverImagePath의 파일이 크기 한도를 넘음");
            }
        } catch (IOException | IllegalArgumentException e) {
            // IllegalArgumentException: 파일 시스템이 경로로 쓸 수 없는 문자 (InvalidPathException)
            return List.of("coverImagePath의 파일을 확인할 수 없음");
        }

        return List.of();
    }
}
