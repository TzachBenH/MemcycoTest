import { useMemo } from 'react';
import { getEditorId } from '../api/editorId';

/** This browser tab's editor id (sent as X-Editor-Id with every request). */
export function useEditorId(): string {
  return useMemo(() => getEditorId(), []);
}
