# CLAUDE.md

광고 클릭 이벤트 수집·집계 개인 프로젝트. 원칙은 `.specify/memory/constitution.md`를 따른다.

## Git

- 이 저장소는 개인 계정(Jake-SHJ)을 쓴다. 이름·이메일은 저장소 로컬 설정에 있고,
  원격은 `git@github-personal:Jake-SHJ/ad-click-event.git`이다. global 설정은 바꾸지 않는다.

## 커밋 규칙

- **prefix**: Conventional Commits 타입을 영어로 쓴다.
  `feat` · `fix` · `docs` · `test` · `refactor` · `perf` · `build` · `ci` · `chore` · `style`
  - 범위가 분명하면 scope를 붙인다. 예: `feat(collector): ...`
- **메시지**: prefix 뒤 제목과 본문은 한글로 쓴다.
  - 제목은 무엇을 바꿨는지 한 줄로, 마침표 없이 쓴다.
  - 본문이 필요하면 한 줄 띄우고 왜 바꿨는지 적는다.
  - 예: `feat(collector): 클릭 이벤트 수신 API 추가`
- **단위**: 한 커밋은 한 기능을 넘지 않는다.
  - 서로 다른 기능, 기능 변경과 무관한 리팩터링, 포맷 변경은 커밋을 나눈다.
  - 한 기능이 크면 테스트 → 구현처럼 더 작은 커밋으로 나눠도 된다.
    단, 각 커밋은 빌드와 테스트가 통과하는 상태여야 한다.
