// features/note의 공개 인터페이스. 게임 화면은 이 파일만 가져다 쓴다.
export { useNote } from './hooks/useNote';
export type { NoteHandle, UseNoteParams } from './hooks/useNote';
export type { NoteFlushResult } from './autosave';
export { NoteWindow } from './components/NoteWindow';
