import { Link } from 'react-router';

/** 없는 경로로 들어왔을 때 보여주는 화면 */
export function NotFoundPage() {
  return (
    <main className="mx-auto max-w-3xl px-6 py-16">
      <h1 className="text-2xl font-bold">페이지를 찾을 수 없습니다</h1>
      <p className="mt-4 text-muted">주소가 바뀌었거나 없는 페이지입니다.</p>
      <Link to="/" className="mt-8 inline-block text-accent underline">
        사건 목록으로 돌아가기
      </Link>
    </main>
  );
}
