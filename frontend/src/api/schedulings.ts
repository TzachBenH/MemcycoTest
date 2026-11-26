import type { Page, Scheduling, SchedulingRequest, SchedulingStatus } from '../types/api';
import { EDIT_LOCK_TOKEN_HEADER, apiClient } from './client';

export interface SchedulingListQuery {
  search?: string;
  taskKey?: string;
  status?: SchedulingStatus | '';
  /** 0-based */
  page: number;
  size: number;
  /** Spring Data format, e.g. "name,asc". */
  sort: string;
}

export async function fetchSchedulings(query: SchedulingListQuery): Promise<Page<Scheduling>> {
  const { data } = await apiClient.get<Page<Scheduling>>('/schedulings', {
    params: {
      search: query.search?.trim() || undefined,
      taskKey: query.taskKey || undefined,
      status: query.status || undefined,
      page: query.page,
      size: query.size,
      sort: query.sort,
    },
  });
  return data;
}

export async function fetchScheduling(id: string): Promise<Scheduling> {
  const { data } = await apiClient.get<Scheduling>(`/schedulings/${id}`);
  return data;
}

export async function createScheduling(request: SchedulingRequest): Promise<Scheduling> {
  const { data } = await apiClient.post<Scheduling>('/schedulings', request);
  return data;
}

/** Full replacement; requires the edit lock token and the version that was loaded. */
export async function updateScheduling(id: string, request: SchedulingRequest, lockToken: string): Promise<Scheduling> {
  const { data } = await apiClient.put<Scheduling>(`/schedulings/${id}`, request, {
    headers: { [EDIT_LOCK_TOKEN_HEADER]: lockToken },
  });
  return data;
}

export async function deleteScheduling(id: string): Promise<void> {
  await apiClient.delete(`/schedulings/${id}`);
}

export async function pauseScheduling(id: string): Promise<Scheduling> {
  const { data } = await apiClient.post<Scheduling>(`/schedulings/${id}/pause`);
  return data;
}

export async function resumeScheduling(id: string): Promise<Scheduling> {
  const { data } = await apiClient.post<Scheduling>(`/schedulings/${id}/resume`);
  return data;
}

/** "Run now": the run starts in the background (202); its result appears in the history. */
export async function runSchedulingNow(id: string): Promise<void> {
  await apiClient.post(`/schedulings/${id}/run`);
}
