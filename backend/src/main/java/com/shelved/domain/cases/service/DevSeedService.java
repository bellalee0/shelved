package com.shelved.domain.cases.service;

import com.shelved.common.utils.SafeExceptionLog;
import com.shelved.domain.cases.dto.DevSeedFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 개발용 시드 폴더의 *.json을 읽어 사건으로 반영한다. local 프로필의 DevSeedLoader가 기동 시 1회 호출한다.
 * 로그에는 파일 이름·사건 ID·건수·실패 사유만 남긴다. 시드 내용과 예외 메시지(시드 내용이 들어갈 수 있음)는 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevSeedService {

    // 오타로 필드가 조용히 무시되지 않도록 모르는 필드·원시 타입 null을 거부한다. 요청 본문 trim 등 웹 설정은 적용하지 않는다
    private static final JsonMapper SEED_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private final ResourcePatternResolver resourcePatternResolver;
    private final DevSeedImporter devSeedImporter;

    /**
     * 반영 결과 건수
     */
    public record DevSeedLoadResult(
            int imported,
            int alreadyPublished,
            int failed
    ) {
    }

    /**
     * @param path 시드 폴더 (Spring 리소스 경로, 예: classpath:dev-data/, file:/tmp/seeds/)
     */
    public DevSeedLoadResult loadAll(String path) {

        List<Resource> seedFileList = findSeedFiles(path);
        if (seedFileList.isEmpty()) {
            log.info("개발용 시드 없음: path={}", path);
            return new DevSeedLoadResult(0, 0, 0);
        }

        int imported = 0;
        int alreadyPublished = 0;
        int failed = 0;
        for (Resource resource : seedFileList) {
            switch (loadOne(resource)) {
                case IMPORTED -> imported++;
                case ALREADY_PUBLISHED -> alreadyPublished++;
                case FAILED -> failed++;
            }
        }
        log.info("개발용 시드 반영 완료: imported={}, alreadyPublished={}, failed={}", imported, alreadyPublished, failed);

        return new DevSeedLoadResult(imported, alreadyPublished, failed);
    }

    private enum Outcome { IMPORTED, ALREADY_PUBLISHED, FAILED }

    private Outcome loadOne(Resource resource) {

        String fileName = resource.getFilename();

        DevSeedFile seed;
        try (InputStream inputStream = resource.getInputStream()) {
            seed = SEED_MAPPER.readValue(inputStream, DevSeedFile.class);
        } catch (IOException | JacksonException e) {
            log.warn("개발용 시드 건너뜀: file={}, reason=JSON을 읽을 수 없음 ({})", fileName, SafeExceptionLog.causeChain(e));
            return Outcome.FAILED;
        }

        List<String> reasons = DevSeedValidator.validate(seed);
        if (!reasons.isEmpty()) {
            log.warn("개발용 시드 건너뜀: file={}, reasons={}", fileName, reasons);
            return Outcome.FAILED;
        }

        try {
            Optional<Long> caseId = devSeedImporter.importSeed(seed);
            if (caseId.isEmpty()) {
                log.info("개발용 시드 건너뜀: file={}, reason=같은 표시명의 공개 사건이 이미 있음", fileName);
                return Outcome.ALREADY_PUBLISHED;
            }
            log.info("개발용 시드 반영: file={}, caseId={}, suspects={}, evidences={}, trickElements={}, hotspots={}",
                    fileName, caseId.get(), seed.suspectList().size(), seed.evidenceList().size(),
                    seed.trickElementList().size(), seed.hotspotList().size());
            return Outcome.IMPORTED;
        } catch (RuntimeException e) {
            log.warn("개발용 시드 건너뜀: file={}, reason=저장 실패 ({}){}",
                    fileName, SafeExceptionLog.causeChain(e), SafeExceptionLog.rootCauseFrames(e));
            return Outcome.FAILED;
        }
    }

    private List<Resource> findSeedFiles(String path) {
        String folder = path.endsWith("/") ? path : path + "/";
        try {
            return Arrays.stream(resourcePatternResolver.getResources(folder + "*.json"))
                    .filter(Resource::isReadable)
                    .sorted(Comparator.comparing(Resource::getFilename, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        } catch (IOException e) {
            // 폴더가 없으면 시드 없이 기동한다
            return List.of();
        }
    }
}
