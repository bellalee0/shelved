import type { RequestHandler } from 'msw';
import { authHandlers } from './auth';

/**
 * 모든 도메인 목 핸들러를 모으는 배열. 브라우저(개발 서버)와 테스트(Node)가 같은 목록을 사용한다.
 * 도메인 핸들러는 handlers/{도메인}.ts에 만들고 여기에 펼쳐 넣는다.
 */
export const handlers: RequestHandler[] = [...authHandlers];
