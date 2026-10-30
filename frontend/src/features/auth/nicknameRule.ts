/** 닉네임 길이 제한 (docs/api/auth-user.md PATCH /api/users/me/nickname) */
export const NICKNAME_MIN_LENGTH = 2;
export const NICKNAME_MAX_LENGTH = 12;

const NICKNAME_PATTERN = /^[가-힣A-Za-z0-9]+$/;

export const NICKNAME_RULE_DESCRIPTION = `${NICKNAME_MIN_LENGTH}~${NICKNAME_MAX_LENGTH}자, 한글·영문·숫자만`;

/**
 * 입력 중 보여줄 규칙 위반 안내를 돌려준다. 규칙을 지켰으면 null.
 * 안내용일 뿐이며 최종 판단은 서버가 한다(400 INVALID_INPUT 메시지를 그대로 표시).
 * 서버와 같이 앞뒤 공백을 제거한 뒤 UTF-16 길이로 센다.
 */
export function getNicknameRuleViolation(nickname: string): string | null {
  const trimmed = nickname.trim();
  if (trimmed.length < NICKNAME_MIN_LENGTH) {
    return `닉네임은 ${NICKNAME_MIN_LENGTH}자 이상 입력해 주세요.`;
  }
  if (trimmed.length > NICKNAME_MAX_LENGTH) {
    return `닉네임은 ${NICKNAME_MAX_LENGTH}자까지 입력할 수 있습니다.`;
  }
  if (!NICKNAME_PATTERN.test(trimmed)) {
    return '완성형 한글·영문·숫자만 사용할 수 있습니다. (공백·특수문자·자음/모음 단독 불가)';
  }
  return null;
}
