import { useQuery } from '@tanstack/react-query';
import { queryKeys } from '../api/queryKeys';
import { fetchTasks } from '../api/tasks';

/** Tasks are defined in the backend code and never change at runtime: loaded once. */
export function useTasks() {
  return useQuery({ queryKey: queryKeys.tasks, queryFn: fetchTasks, staleTime: Infinity });
}
