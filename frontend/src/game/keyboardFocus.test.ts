import { afterEach, describe, expect, it } from 'vitest';
import { shouldIgnoreGameKeys } from './keyboardFocus';

function focusNew<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  setUp: (element: HTMLElementTagNameMap[K]) => void = () => {},
) {
  const element = document.createElement(tag);
  setUp(element);
  document.body.appendChild(element);
  element.focus();
  return element;
}

afterEach(() => {
  document.body.innerHTML = '';
});

describe('shouldIgnoreGameKeys', () => {
  it('글자 입력창에 포커스가 있으면 게임 키를 무시한다', () => {
    // given
    focusNew('input', (input) => (input.type = 'text'));

    // when / then
    expect(shouldIgnoreGameKeys()).toBe(true);
  });

  it('textarea에 포커스가 있으면 무시한다', () => {
    // given
    focusNew('textarea');

    // when / then
    expect(shouldIgnoreGameKeys()).toBe(true);
  });

  it('contenteditable 요소(안쪽 요소 포함)에 포커스가 있으면 무시한다', () => {
    // given
    const editor = document.createElement('div');
    editor.setAttribute('contenteditable', 'true');
    const inner = document.createElement('span');
    editor.appendChild(inner);
    document.body.appendChild(editor);

    // when / then
    expect(shouldIgnoreGameKeys(editor)).toBe(true);
    expect(shouldIgnoreGameKeys(inner)).toBe(true);
  });

  it('버튼·체크박스·본문에 포커스가 있으면 무시하지 않는다', () => {
    // given
    const button = focusNew('button');
    const checkbox = focusNew('input', (input) => (input.type = 'checkbox'));
    const disabledEditor = document.createElement('div');
    disabledEditor.setAttribute('contenteditable', 'false');

    // when / then
    expect(shouldIgnoreGameKeys(button)).toBe(false);
    expect(shouldIgnoreGameKeys(checkbox)).toBe(false);
    expect(shouldIgnoreGameKeys(disabledEditor)).toBe(false);
    expect(shouldIgnoreGameKeys(document.body)).toBe(false);
    expect(shouldIgnoreGameKeys(null)).toBe(false);
  });
});
