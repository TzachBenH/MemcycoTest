import type { Execution, ExecutionStatus, Page } from '../types/api';
import { apiClient } from './client';

export interface ExecutionQuery {
  status?: ExecutionStatus | '';
  /** 0-based */
  page: number;
  size: number;
}

/** Execution history of a scheduling, newest first. */
export async function fetchExecutions(schedulingId: string, query: ExecutionQuery): Promise<Page<Execution>> {
  const { data } = await apiClient.get<Page<Execution>>(`/schedulings/${schedulingId}/executions`, {
    params: { status: query.status || undefined, page: query.page, size: query.size },
  });
  return data;
}
