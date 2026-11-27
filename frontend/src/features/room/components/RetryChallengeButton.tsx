import { Button } from '../../../shared/components/Button';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { useStartInvestigation } from '../hooks/useStartInvestigation';
import { ExistingRoomDialog } from './ExistingRoomDialog';

/**
 * 결과 화면의 "다시 도전": 같은 사건의 새 세션을 만들고 게임 화면으로 이동한다.
 * 사건 카드와 같은 생성 흐름(useStartInvestigation)을 쓴다.
 */
export function RetryChallengeButton({ caseId }: { caseId: number }) {
  const { start, isStarting, errorMessage, existingRoomId, clearExistingRoom } =
    useStartInvestigation();
  return (
    <>
      <Button isLoading={isStarting} onClick={() => start(caseId)}>
        다시 도전
      </Button>
      {errorMessage && (
        <div className="w-full">
          <ErrorMessage message={errorMessage} />
        </div>
      )}
      {existingRoomId !== null && (
        <ExistingRoomDialog roomId={existingRoomId} onClose={clearExistingRoom} />
      )}
    </>
  );
}
