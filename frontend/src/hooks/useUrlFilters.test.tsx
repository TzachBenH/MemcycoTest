import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { DEFAULT_FILTERS, parseFilters, serializeFilters, useUrlFilters } from './useUrlFilters';

describe('parseFilters / serializeFilters', () => {
  it('reads the URL (page is 1-based there)', () => {
    expect(parseFilters(new URLSearchParams('search=log&taskKey=LOG&status=PAUSED&page=3&size=50&sort=taskKey,desc'))).toEqual({
      search: 'log',
      taskKey: 'LOG',
      status: 'PAUSED',
      page: 2,
      size: 50,
      sort: 'taskKey,desc',
    });
  });

  it('ignores invalid values', () => {
    expect(parseFilters(new URLSearchParams('status=WHATEVER&page=-1&size=7&sort=fingerprint,asc'))).toEqual(
      DEFAULT_FILTERS,
    );
  });

  it('writes only values that differ from the defaults', () => {
    expect(serializeFilters(DEFAULT_FILTERS).toString()).toBe('');
    expect(serializeFilters({ ...DEFAULT_FILTERS, status: 'ACTIVE', page: 1 }).toString()).toBe('status=ACTIVE&page=2');
  });
});

describe('useUrlFilters', () => {
  const wrapper = (route: string) =>
    function Wrapper({ children }: { children: ReactNode }) {
      return <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>;
    };

  it('goes back to the first page when a filter changes', () => {
    const { result } = renderHook(() => useUrlFilters(), { wrapper: wrapper('/?page=3') });
    expect(result.current.filters.page).toBe(2);
    act(() => result.current.update({ taskKey: 'LOG' }));
    expect(result.current.filters).toMatchObject({ taskKey: 'LOG', page: 0 });
  });

  it('changes pages without touching the filters, and resets everything', () => {
    const { result } = renderHook(() => useUrlFilters(), { wrapper: wrapper('/?status=ACTIVE') });
    act(() => result.current.update({ page: 4 }));
    expect(result.current.filters).toMatchObject({ status: 'ACTIVE', page: 4 });
    act(() => result.current.reset());
    expect(result.current.filters).toEqual(DEFAULT_FILTERS);
  });
});
