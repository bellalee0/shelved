import { Link, useParams } from 'react-router';

/** 결과 화면(/rooms/:roomId/result) 자리. 실제 결과(리빌·플레이 기록)는 이후 결과 화면 작업에서 만든다 */
export function ResultPage() {
  const { roomId } = useParams();

  return (
    <main className="mx-auto max-w-3xl px-6 py-12">
      <h1 className="text-2xl font-bold">수사 결과</h1>
      <p className="mt-4 text-muted">세션 {roomId}의 결과 화면은 준비 중입니다.</p>
      <Link to="/" className="mt-6 inline-block text-sm text-accent underline">
        메인으로
      </Link>
    </main>
  );
}
