import { useQuery } from '@tanstack/react-query';
import { MANIFEST_URL, parseManifest, type AssetManifest } from '../../../game/assets';

async function fetchManifest(): Promise<AssetManifest> {
  // 정적 파일(public/assets/manifest.json)이라 API용 axios 인스턴스를 거치지 않는다
  const response = await fetch(MANIFEST_URL);
  if (!response.ok) {
    throw new Error('manifest를 불러오지 못했습니다.');
  }
  const manifest = parseManifest(await response.json());
  if (manifest === null) {
    throw new Error('manifest 형식이 올바르지 않습니다.');
  }
  return manifest;
}

/**
 * 에셋 manifest(React 쪽 읽기용). 등장인물 얼굴을 그리는 데 쓴다.
 * 불러오지 못해도 화면은 이름 첫 글자 원으로 대신 그리므로 재시도하지 않는다.
 */
export function useAssetManifest(enabled: boolean) {
  return useQuery({
    queryKey: ['asset-manifest'],
    queryFn: fetchManifest,
    enabled,
    staleTime: Infinity,
    retry: false,
  });
}
