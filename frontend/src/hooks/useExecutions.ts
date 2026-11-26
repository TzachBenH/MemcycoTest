import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { fetchExecutions, type ExecutionQuery } from '../api/executions';
import { queryKeys } from '../api/queryKeys';
import { POLL_INTERVAL_MS } from './useSchedulings';

export function useExecutions(schedulingId: string, query: ExecutionQuery) {
  return useQuery({
    queryKey: queryKeys.executions(schedulingId, query),
    queryFn: () => fetchExecutions(schedulingId, query),
    refetchInterval: POLL_INTERVAL_MS,
    placeholderData: keepPreviousData,
  });
}
