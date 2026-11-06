// 텍스트 입력 중에는 Phaser가 키를 무시해야 한다 (frontend.md "조작·창 관리 규칙").
// 노트·대화 입력창에 W·A·S·D·E·방향키가 그대로 입력되고, 캐릭터는 움직이지 않게 한다.

const TEXT_INPUT_TYPES = new Set([
  'text',
  'search',
  'email',
  'password',
  'tel',
  'url',
  'number',
  'date',
  'datetime-local',
  'month',
  'time',
  'week',
]);

/** 포커스가 글자를 입력받는 요소(input·textarea·select·contenteditable)에 있으면 true */
export function isTextEntryElement(element: Element | null): boolean {
  if (element === null) {
    return false;
  }
  if (element instanceof HTMLTextAreaElement || element instanceof HTMLSelectElement) {
    return true;
  }
  if (element instanceof HTMLInputElement) {
    return TEXT_INPUT_TYPES.has(element.type);
  }
  // isContentEditable을 지원하지 않는 환경(jsdom)도 있어 속성으로도 확인한다
  return (
    element instanceof HTMLElement &&
    (element.isContentEditable ||
      element.closest('[contenteditable]:not([contenteditable="false"])') !== null)
  );
}

/** 게임이 키보드 입력을 무시해야 하는지. 기본값은 현재 문서의 포커스 요소 */
export function shouldIgnoreGameKeys(activeElement: Element | null = document.activeElement) {
  return isTextEntryElement(activeElement);
}
