import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { SchedulingStatus } from '../types/api';
import { SCHEDULING_STATUSES } from '../utils/status';

/** State of the schedulings table, kept in the URL so it survives reloads and can be shared. */
export interface SchedulingFilters {
  search: string;
  taskKey: string;
  status: SchedulingStatus | '';
  /** 0-based (the URL shows it 1-based). */
  page: number;
  size: number;
  /** Spring Data format, e.g. "name,asc". */
  sort: string;
}

export const PAGE_SIZES = [10, 20, 50, 100] as const;
export const DEFAULT_FILTERS: SchedulingFilters = {
  search: '',
  taskKey: '',
  status: '',
  page: 0,
  size: 20,
  sort: 'name,asc',
};

/** Columns the backend allows sorting on. */
export const SORTABLE_FIELDS = ['name', 'taskKey', 'enabled', 'createdAt', 'updatedAt'] as const;
const SORT_PATTERN = new RegExp(`^(${SORTABLE_FIELDS.join('|')}),(asc|desc)$`);

export function parseFilters(params: URLSearchParams): SchedulingFilters {
  const status = params.get('status') ?? '';
  const page = Number(params.get('page'));
  const size = Number(params.get('size'));
  const sort = params.get('sort') ?? '';
  return {
    search: params.get('search') ?? '',
    taskKey: params.get('taskKey') ?? '',
    status: (SCHEDULING_STATUSES as readonly string[]).includes(status) ? (status as SchedulingStatus) : '',
    page: Number.isInteger(page) && page >= 1 ? page - 1 : 0,
    size: (PAGE_SIZES as readonly number[]).includes(size) ? size : DEFAULT_FILTERS.size,
    sort: SORT_PATTERN.test(sort) ? sort : DEFAULT_FILTERS.sort,
  };
}

/** Only values that differ from the defaults are written, so the default view has a clean URL. */
export function serializeFilters(filters: SchedulingFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.search) params.set('search', filters.search);
  if (filters.taskKey) params.set('taskKey', filters.taskKey);
  if (filters.status) params.set('status', filters.status);
  if (filters.page > 0) params.set('page', String(filters.page + 1));
  if (filters.size !== DEFAULT_FILTERS.size) params.set('size', String(filters.size));
  if (filters.sort !== DEFAULT_FILTERS.sort) params.set('sort', filters.sort);
  return params;
}

export function useUrlFilters() {
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => parseFilters(params), [params]);

  /** Applies changes; any change other than the page itself goes back to the first page. */
  const update = useCallback(
    (changes: Partial<SchedulingFilters>) => {
      setParams(
        (current) => {
          const next = { ...parseFilters(current), ...changes };
          if (!('page' in changes)) {
            next.page = 0;
          }
          return serializeFilters(next);
        },
        { replace: true },
      );
    },
    [setParams],
  );

  const reset = useCallback(() => setParams(new URLSearchParams(), { replace: true }), [setParams]);

  return { filters, update, reset };
}
