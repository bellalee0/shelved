import { Link } from 'react-router';

/** 관리자 화면(/admin) 자리. 실제 화면은 관리자 기능 작업(5주차~)에서 만든다 */
export function AdminHomePage() {
  return (
    <main className="mx-auto max-w-3xl px-6 py-16">
      <h1 className="text-2xl font-bold">관리자 화면</h1>
      <p className="mt-4 text-muted">관리자 기능은 준비 중입니다.</p>
      <Link to="/" className="mt-8 inline-block text-accent underline">
        사건 목록으로 돌아가기
      </Link>
    </main>
  );
}
