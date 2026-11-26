import type {
  ExecutionStatus,
  RetryCancelledReason,
  SchedulingStatus,
  TriggerType,
} from '../types/api';

export type ChipColor = 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning';

export const SCHEDULING_STATUSES: readonly SchedulingStatus[] = ['ACTIVE', 'PAUSED', 'COMPLETED'];
export const EXECUTION_STATUSES: readonly ExecutionStatus[] = ['RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED', 'STUCK'];

export const SCHEDULING_STATUS_LABEL: Record<SchedulingStatus, string> = {
  ACTIVE: 'Active',
  PAUSED: 'Paused',
  COMPLETED: 'Completed',
};

export const SCHEDULING_STATUS_COLOR: Record<SchedulingStatus, ChipColor> = {
  ACTIVE: 'success',
  PAUSED: 'default',
  COMPLETED: 'info',
};

export const EXECUTION_STATUS_LABEL: Record<ExecutionStatus, string> = {
  RUNNING: 'Running',
  SUCCESS: 'Success',
  FAILED: 'Failed',
  SKIPPED: 'Skipped',
  STUCK: 'Stuck',
};

export const EXECUTION_STATUS_COLOR: Record<ExecutionStatus, ChipColor> = {
  RUNNING: 'info',
  SUCCESS: 'success',
  FAILED: 'error',
  SKIPPED: 'default',
  STUCK: 'warning',
};

export const TRIGGER_TYPE_LABEL: Record<TriggerType, string> = {
  SCHEDULED: 'Scheduled',
  MANUAL: 'Manual',
  RETRY: 'Retry',
};

export const RETRY_CANCELLED_LABEL: Record<RetryCancelledReason, string> = {
  SUPERSEDED: 'superseded by a newer run',
  PAUSED: 'scheduling paused',
  EDITED: 'scheduling edited',
  DELETED: 'scheduling deleted',
};

/** A retry is still waiting to run: the next attempt is scheduled and was not cancelled. */
export function isRetryPending(execution: {
  nextRetryAt: string | null;
  retryCancelledReason: RetryCancelledReason | null;
}): boolean {
  return !!execution.nextRetryAt && !execution.retryCancelledReason;
}
