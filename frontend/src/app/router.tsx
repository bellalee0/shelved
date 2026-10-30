import { createBrowserRouter } from 'react-router';
import { routes } from './routes';

/** 브라우저 주소와 연결한 라우터. 화면 목록은 routes.tsx에서 관리한다 */
export const router = createBrowserRouter(routes);
