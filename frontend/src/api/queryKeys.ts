import type { ExecutionQuery } from './executions';
import type { SchedulingListQuery } from './schedulings';

/** TanStack Query keys. Everything about schedulings starts with "schedulings", so one invalidation refreshes all. */
export const queryKeys = {
  tasks: ['tasks'] as const,
  schedulings: ['schedulings'] as const,
  schedulingList: (query: SchedulingListQuery) => ['schedulings', 'list', query] as const,
  scheduling: (id: string) => ['schedulings', 'detail', id] as const,
  executions: (id: string, query: ExecutionQuery) => ['schedulings', 'detail', id, 'executions', query] as const,
};
