# Shelved

소설을 바탕으로 AI가 구성한 사건을 플레이어가 수사하는 추리 웹 게임입니다. 프로젝트 소개는 [README.md](README.md)를 참고합니다.

## 작업 전에 반드시 읽을 문서

- 공통 규칙 (모든 작업): [docs/conventions/common.md](docs/conventions/common.md)
- 백엔드 작업 시: [docs/conventions/backend.md](docs/conventions/backend.md)
- 프론트엔드 작업 시: [docs/conventions/frontend.md](docs/conventions/frontend.md)

이 파일에는 규칙을 직접 작성하지 않습니다. 규칙의 추가·수정은 `docs/conventions/`에서 합니다.

## 기획 문서

세부 기획서 `project-spec.md`는 정답·원작 정보를 포함하므로 `.gitignore`로 제외되어 저장소에는 없고, 저장소 소유자의 로컬에만 있습니다.

- 로컬에 파일이 있으면 작업 전에 관련 절을 읽습니다.
- 파일이 없는데 기획 내용이 필요하면 추측하지 않고 저장소 소유자에게 묻습니다.

## 저장소 소유자와 작업할 때

- 저장소 소유자는 Spring Boot 백엔드 경험이 있고, 프론트엔드(React/Phaser/TypeScript)는 익숙하지 않습니다. 프론트엔드 작업은 설명을 더 자세히 덧붙입니다.
- 파일을 수정한 뒤에는 핵심적으로 바뀐 코드를 대화 안에 코드 블록으로 보여주고, 무엇을 왜 바꿨는지 설명합니다.
- 커밋과 푸시는 저장소 소유자가 요청할 때만 합니다. PR은 생성까지만 하고 병합하지 않습니다.
