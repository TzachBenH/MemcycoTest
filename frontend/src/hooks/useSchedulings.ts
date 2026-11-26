import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '../api/queryKeys';
import {
  deleteScheduling,
  fetchScheduling,
  fetchSchedulings,
  pauseScheduling,
  resumeScheduling,
  runSchedulingNow,
  type SchedulingListQuery,
} from '../api/schedulings';

/** Agreed refresh rate: the table and the history page poll every 10 seconds. */
export const POLL_INTERVAL_MS = 10_000;

export function useSchedulingList(query: SchedulingListQuery) {
  return useQuery({
    queryKey: queryKeys.schedulingList(query),
    queryFn: () => fetchSchedulings(query),
    refetchInterval: POLL_INTERVAL_MS,
    placeholderData: keepPreviousData,
  });
}

export function useScheduling(id: string) {
  return useQuery({
    queryKey: queryKeys.scheduling(id),
    queryFn: () => fetchScheduling(id),
    refetchInterval: POLL_INTERVAL_MS,
  });
}

/** Row actions. Each refreshes every scheduling query when it succeeds. */
export function useSchedulingActions() {
  const queryClient = useQueryClient();
  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.schedulings });
  return {
    pause: useMutation({ mutationFn: pauseScheduling, onSuccess: refresh }),
    resume: useMutation({ mutationFn: resumeScheduling, onSuccess: refresh }),
    remove: useMutation({ mutationFn: deleteScheduling, onSuccess: refresh }),
    runNow: useMutation({ mutationFn: runSchedulingNow, onSuccess: refresh }),
  };
}
