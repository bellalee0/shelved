import { useState, type FormEvent } from 'react';
import { Button } from '../../../shared/components/Button';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { TextInput } from '../../../shared/components/TextInput';
import { useUpdateNickname } from '../hooks/useUpdateNickname';
import {
  getNicknameRuleViolation,
  NICKNAME_MAX_LENGTH,
  NICKNAME_RULE_DESCRIPTION,
} from '../nicknameRule';

type NicknameFormProps = {
  initialNickname?: string;
  onSuccess: () => void;
};

/**
 * 닉네임 입력·제출 양식. 입력 중에는 규칙 위반을 안내하지만 제출은 막지 않고,
 * 서버의 400 INVALID_INPUT·409 NICKNAME_EXIST 응답 message를 그대로 보여준다.
 */
export function NicknameForm({ initialNickname = '', onSuccess }: NicknameFormProps) {
  const [nickname, setNickname] = useState(initialNickname);
  const updateNickname = useUpdateNickname();
  const ruleViolation = nickname.length > 0 ? getNicknameRuleViolation(nickname) : null;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    updateNickname.mutate(nickname.trim(), { onSuccess });
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
      <TextInput
        label="닉네임"
        name="nickname"
        autoComplete="nickname"
        value={nickname}
        // 서버가 최종 판단하도록 maxLength로 입력을 자르지 않고, 조금 넘는 입력은 안내만 한다
        maxLength={NICKNAME_MAX_LENGTH * 2}
        hint={NICKNAME_RULE_DESCRIPTION}
        error={ruleViolation}
        onChange={(event) => {
          setNickname(event.target.value);
          updateNickname.reset();
        }}
      />
      {updateNickname.error && <ErrorMessage message={updateNickname.error.message} />}
      <Button
        type="submit"
        disabled={nickname.trim().length === 0}
        isLoading={updateNickname.isPending}
      >
        시작하기
      </Button>
    </form>
  );
}
