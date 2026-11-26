import { renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { getEditorId } from '../api/editorId';
import { useEditorId } from './useEditorId';

describe('useEditorId', () => {
  it("returns this tab's stable editor id", () => {
    const { result, rerender } = renderHook(() => useEditorId());
    const first = result.current;
    rerender();
    expect(result.current).toBe(first);
    expect(first).toBe(getEditorId());
  });
});
