import { QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { RouterProvider } from 'react-router';
import { AuthBootstrap } from './AuthBootstrap';
import { createQueryClient } from './queryClient';
import { router } from './router';

/** 앱 최상위 컴포넌트. 전역 Provider를 감싸고, 로그인 복원이 끝나면 라우터를 연결한다 */
export function App() {
  const [queryClient] = useState(createQueryClient);

  return (
    <QueryClientProvider client={queryClient}>
      <AuthBootstrap>
        <RouterProvider router={router} />
      </AuthBootstrap>
    </QueryClientProvider>
  );
}
