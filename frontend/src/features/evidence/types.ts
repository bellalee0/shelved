// 백엔드 evidence 도메인 DTO와 같은 이름·필드의 타입 (docs/api/investigation.md)

/** 물증 조사 결과. 같은 물증은 몇 번 조사해도 같은 설명이 온다 */
export type EvidenceInspectResponse = {
  evidenceId: number;
  name: string;
  description: string;
};
