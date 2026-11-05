import { useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '../features/auth/hooks/useAuth';
import { createAuthRedirectState } from '../features/auth/redirect';
import { UserMenu } from '../features/auth/components/UserMenu';
import { CaseCardSection } from '../features/cases/components/CaseCardSection';
import { useCases } from '../features/cases/hooks/useCases';
import {
  createCaseSelectionLocation,
  parseSelectedCaseId,
  SELECTED_CASE_PARAM,
} from '../features/cases/selection';
import { ActiveRoomSection } from '../features/room/components/ActiveRoomSection';
import { EndedRoomSection } from '../features/room/components/EndedRoomSection';
import { StartInvestigationDialog } from '../features/room/components/StartInvestigationDialog';
import { useRooms } from '../features/room/hooks/useRooms';
import { formatTimeLimit } from '../shared/utils/formatTime';

/**
 * 메인 화면. 사건 카드는 비로그인도 볼 수 있고, 로그인하면 진행 중인 수사와 지난 수사도 보여준다.
 * 카드를 고르면(?case=<caseId>) 로그인한 사용자는 시작 확인 창이 뜨고, 비로그인 사용자는 로그인 화면으로 간다.
 * 로그인(닉네임 설정)을 마치면 같은 주소로 돌아와 시작 확인 창이 다시 뜬다. 자동으로 세션을 만들지 않는다.
 */
export function CaseListPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { user, isLoggedIn } = useAuth();
  const userId = user?.userId ?? null;

  const casesQuery = useCases();
  const activeRoomsQuery = useRooms('ACTIVE', userId);
  const endedRoomsQuery = useRooms('ENDED', userId);

  const activeRooms = activeRoomsQuery.data?.content;
  const activeCaseIds = useMemo(
    () => new Set((activeRooms ?? []).map((room) => room.caseId)),
    [activeRooms],
  );

  const selectedCaseId = parseSelectedCaseId(searchParams);
  const selectedCase = casesQuery.data?.find((caseCard) => caseCard.caseId === selectedCaseId);

  function handleStart(caseId: number) {
    if (!isLoggedIn) {
      // 로그인·닉네임 설정이 끝나면 고른 사건이 선택된 이 화면으로 돌아온다
      navigate('/login', {
        state: createAuthRedirectState(createCaseSelectionLocation(caseId)),
      });
      return;
    }
    setSearchParams({ [SELECTED_CASE_PARAM]: String(caseId) });
  }

  function handleCloseDialog() {
    setSearchParams({}, { replace: true });
  }

  return (
    <div className="mx-auto max-w-3xl px-6 pb-16">
      <header className="flex items-center justify-between py-4">
        <span className="text-lg font-bold">Shelved</span>
        <UserMenu />
      </header>
      <main className="flex flex-col gap-10 py-8">
        {isLoggedIn && (
          <ActiveRoomSection
            rooms={activeRooms}
            isLoading={activeRoomsQuery.isPending}
            errorMessage={activeRoomsQuery.error?.message ?? null}
            onRetry={() => void activeRoomsQuery.refetch()}
          />
        )}
        <CaseCardSection
          caseCards={casesQuery.data}
          isLoading={casesQuery.isPending}
          errorMessage={casesQuery.error?.message ?? null}
          onRetry={() => void casesQuery.refetch()}
          activeCaseIds={activeCaseIds}
          onStart={handleStart}
        />
        {isLoggedIn && (
          <EndedRoomSection
            rooms={endedRoomsQuery.data?.content}
            isLoading={endedRoomsQuery.isPending}
            errorMessage={endedRoomsQuery.error?.message ?? null}
            onRetry={() => void endedRoomsQuery.refetch()}
          />
        )}
      </main>
      {isLoggedIn && selectedCase && (
        <StartInvestigationDialog
          key={selectedCase.caseId}
          caseId={selectedCase.caseId}
          displayTitle={selectedCase.displayTitle}
          rulesSummary={`제한시간 ${formatTimeLimit(selectedCase.timeLimitSec)} · 힌트 ${selectedCase.hintLimit}회 · 지목 ${selectedCase.accusationLimit}회`}
          onClose={handleCloseDialog}
        />
      )}
    </div>
  );
}
