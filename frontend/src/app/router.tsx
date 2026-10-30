import { createBrowserRouter } from 'react-router';
import { CaseListPage } from '../pages/CaseListPage';
import { NotFoundPage } from '../pages/NotFoundPage';

/** URL별 화면 목록. 새 화면은 이 배열에 경로와 페이지 컴포넌트를 추가한다 */
export const router = createBrowserRouter([
  { path: '/', element: <CaseListPage /> },
  { path: '*', element: <NotFoundPage /> },
]);
