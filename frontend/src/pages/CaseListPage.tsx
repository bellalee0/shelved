import { UserMenu } from '../features/auth/components/UserMenu';

/** 메인 화면(사건 카드 목록) 자리. 비로그인도 볼 수 있다. 실제 화면은 2주차 메인 화면 작업에서 만든다 */
export function CaseListPage() {
  return (
    <div className="mx-auto max-w-3xl px-6">
      <header className="flex items-center justify-between py-4">
        <span className="text-lg font-bold">Shelved</span>
        <UserMenu />
      </header>
      <main className="py-12">
        <h1 className="text-3xl font-bold">Shelved</h1>
        <p className="mt-4 text-muted">사건 목록 화면은 준비 중입니다.</p>
      </main>
    </div>
  );
}
