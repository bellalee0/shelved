// features/npc의 공개 인터페이스. 게임 화면은 이 파일만 가져다 쓴다.
export { NpcPanel } from './components/NpcPanel';
export type { NpcPanelSuspect } from './components/NpcPanel';
export { ConversationLogPanel } from './components/ConversationLogPanel';
export { joinConversations } from './messages';
export type { ConversationListItem } from './messages';
