import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { resetEditorIdForTests } from '../api/editorId';
import '../utils/dayjs';
import { server } from './server';

// Any request without a handler is a test bug.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(async () => {
  cleanup();
  // Unmounted editors release their edit lock on the next tick: let that happen inside this test.
  await new Promise((resolve) => setTimeout(resolve, 20));
  server.resetHandlers();
  sessionStorage.clear();
  resetEditorIdForTests();
});
afterAll(() => server.close());
