import { Link, useParams } from 'react-router';

/** 게임 화면(/rooms/:roomId) 자리. 실제 화면(맵·HUD·창)은 이후 게임 화면 작업에서 만든다 */
export function GamePage() {
  const { roomId } = useParams();

  return (
    <main className="mx-auto max-w-3xl px-6 py-12">
      <h1 className="text-2xl font-bold">수사 화면</h1>
      <p className="mt-4 text-muted">세션 {roomId}의 게임 화면은 준비 중입니다.</p>
      <Link to="/" className="mt-6 inline-block text-sm text-accent underline">
        메인으로
      </Link>
    </main>
  );
}
