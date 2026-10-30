import type { RouteObject } from 'react-router';
import { RequireAdmin } from '../features/auth/components/RequireAdmin';
import { RequireLogin } from '../features/auth/components/RequireLogin';
import { RequireNicknameSet } from '../features/auth/components/RequireNicknameSet';
import { AdminHomePage } from '../pages/admin/AdminHomePage';
import { CaseListPage } from '../pages/CaseListPage';
import { LoginPage } from '../pages/LoginPage';
import { NicknamePage } from '../pages/NicknamePage';
import { NotFoundPage } from '../pages/NotFoundPage';

/**
 * URL별 화면 목록. 새 화면은 알맞은 보호막(element) 아래 children에 추가한다.
 * - RequireNicknameSet: 로그인했지만 닉네임이 없으면 /nickname으로 (비로그인은 통과)
 * - RequireLogin: 비로그인이면 /login으로, 로그인 후 원래 화면으로 복귀
 * - RequireAdmin: role=ADMIN이 아니면 메인으로
 */
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireLogin />,
    children: [{ path: '/nickname', element: <NicknamePage /> }],
  },
  {
    element: <RequireNicknameSet />,
    children: [
      { path: '/', element: <CaseListPage /> },
      {
        element: <RequireLogin />,
        children: [
          // 로그인이 필요한 화면(게임, 결과, 랭킹 등)은 여기에 추가한다
          {
            element: <RequireAdmin />,
            children: [{ path: '/admin', element: <AdminHomePage /> }],
          },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
