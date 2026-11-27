/** 주소의 ID처럼 양의 정수여야 하는 문자열을 읽는다. 아니면 null */
export function parsePositiveInt(value: string | null | undefined): number | null {
  if (value == null || !/^[1-9]\d*$/.test(value)) {
    return null;
  }
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) ? parsed : null;
}
