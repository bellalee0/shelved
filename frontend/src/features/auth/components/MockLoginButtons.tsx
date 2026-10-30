import { createMockGoogleIdToken, MOCK_ADMIN_GOOGLE_SUB } from '../../../mocks/mockGoogleIdToken';
import { Button } from '../../../shared/components/Button';

type MockLoginButtonsProps = {
  onIdToken: (idToken: string) => void;
  disabled?: boolean;
};

/**
 * 목 모드(VITE_USE_MOCK=true + 개발 서버) 전용 로그인 버튼. 실제 Google 없이 가짜 ID 토큰으로 로그인한다.
 * LoginPage가 목 모드에서만 동적 import로 불러오므로 배포 빌드에는 들어가지 않는다.
 */
export function MockLoginButtons({ onIdToken, disabled }: MockLoginButtonsProps) {
  return (
    <section className="flex w-full flex-col gap-2 rounded-md border border-dashed border-line p-4">
      <p className="text-sm text-muted">개발용 목 로그인 (MSW 목 모드에서만 표시)</p>
      <Button
        variant="secondary"
        disabled={disabled}
        // 누를 때마다 새 사용자로 로그인해 닉네임 설정 화면부터 확인할 수 있다
        onClick={() => onIdToken(createMockGoogleIdToken(`mock-player-${crypto.randomUUID()}`))}
      >
        목 로그인 · 새 사용자
      </Button>
      <Button
        variant="secondary"
        disabled={disabled}
        onClick={() => onIdToken(createMockGoogleIdToken(MOCK_ADMIN_GOOGLE_SUB))}
      >
        목 로그인 · 관리자
      </Button>
    </section>
  );
}
