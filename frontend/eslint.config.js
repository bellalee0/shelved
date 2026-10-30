import js from '@eslint/js';
import prettierConfig from 'eslint-config-prettier/flat';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import { defineConfig, globalIgnores } from 'eslint/config';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default defineConfig([
  globalIgnores(['dist', 'coverage', 'public/mockServiceWorker.js']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
    rules: {
      // frontend.md: any 사용 금지
      '@typescript-eslint/no-explicit-any': 'error',
    },
  },
  // 코드 모양 규칙은 Prettier가 담당하므로 충돌하는 ESLint 규칙을 끈다 (항상 마지막에 둔다)
  prettierConfig,
]);
