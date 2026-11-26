import type { Task } from '../types/api';
import { apiClient } from './client';

/** The predefined (read-only) tasks with their parameter schemas. */
export async function fetchTasks(): Promise<Task[]> {
  const { data } = await apiClient.get<Task[]>('/tasks');
  return data;
}
