/**
 * Types of the backend REST API (Spring Boot, /api). Dates are ISO-8601 strings (UTC instants).
 * Mirrors the backend DTOs in com.memcyco.scheduler.dto.
 */

export type ErrorCode =
  | 'VALIDATION_FAILED'
  | 'NOT_FOUND'
  | 'DUPLICATE_SCHEDULING'
  | 'EDIT_LOCKED'
  | 'EDIT_LOCK_LOST'
  | 'CONCURRENT_MODIFICATION'
  | 'INTERNAL_ERROR';

// ---- Tasks -------------------------------------------------------------------------------------------------------

export type ParameterType = 'STRING' | 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'ENUM' | 'EMAIL';

/** Schema of one task parameter: drives the form field, client validation and backend validation. */
export interface ParameterDefinition {
  name: string;
  label: string;
  type: ParameterType;
  required: boolean;
  defaultValue?: string | number | boolean;
  allowedValues?: string[];
  min?: number;
  max?: number;
  description?: string;
}

export interface Task {
  key: string;
  name: string;
  description: string;
  parameters: ParameterDefinition[];
}

// ---- Timing ------------------------------------------------------------------------------------------------------

export type TimingType = 'ONE_TIME' | 'INTERVAL' | 'WEEKLY' | 'CRON';
export type IntervalUnit = 'MINUTES' | 'HOURS';
export type DayCode = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN';

export interface OneTimeTiming {
  type: 'ONE_TIME';
  runAt: string;
  timezone: string;
}

export interface IntervalTiming {
  type: 'INTERVAL';
  every: number;
  unit: IntervalUnit;
  /** Optional on input (null = start now); always set in responses. */
  startAt: string | null;
  timezone: string;
}

export interface WeeklyTiming {
  type: 'WEEKLY';
  days: DayCode[];
  /** "HH:mm" */
  time: string;
  timezone: string;
}

export interface CronTiming {
  type: 'CRON';
  /** Quartz syntax: seconds first, 6 or 7 fields. */
  expression: string;
  timezone: string;
}

export type Timing = OneTimeTiming | IntervalTiming | WeeklyTiming | CronTiming;

// ---- Schedulings -------------------------------------------------------------------------------------------------

export type SchedulingStatus = 'ACTIVE' | 'PAUSED' | 'COMPLETED';
export type ExecutionStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED' | 'STUCK';
export type TriggerType = 'SCHEDULED' | 'MANUAL' | 'RETRY';
export type RetryCancelledReason = 'SUPERSEDED' | 'PAUSED' | 'EDITED' | 'DELETED';

export interface TaskSummary {
  key: string;
  name: string;
  /** false when the task no longer exists in the backend code. */
  known: boolean;
}

/** Most recent execution attempt. A retry is pending when nextRetryAt is set and retryCancelledReason is not. */
export interface LastExecution {
  id: string;
  status: ExecutionStatus;
  triggerType: TriggerType;
  attempt: number;
  maxAttempts: number;
  startedAt: string;
  finishedAt: string | null;
  nextRetryAt: string | null;
  retryCancelledReason: RetryCancelledReason | null;
  errorMessage: string | null;
}

export interface EditLockInfo {
  lockedBy: string;
  since: string;
  expiresAt: string;
  /** true when this browser tab holds the lock. */
  mine: boolean;
}

export interface SchedulingWarning {
  code: 'PAUSED_DUPLICATE_EXISTS' | 'ACTIVE_DUPLICATE_EXISTS' | string;
  message: string;
  schedulingId: string | null;
  schedulingName: string | null;
}

export interface Scheduling {
  id: string;
  name: string;
  task: TaskSummary;
  parameters: Record<string, unknown>;
  timing: Timing;
  enabled: boolean;
  status: SchedulingStatus;
  nextFireTime: string | null;
  lastExecution: LastExecution | null;
  editLock: EditLockInfo | null;
  warnings: SchedulingWarning[];
  version: number;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  updatedBy: string;
}

export interface SchedulingRequest {
  name: string;
  taskKey: string;
  parameters: Record<string, unknown>;
  timing: Timing;
  enabled: boolean;
  /** Required on update (optimistic locking). */
  version?: number;
}

export interface EditLock {
  lockToken: string;
  expiresAt: string;
}

// ---- Executions --------------------------------------------------------------------------------------------------

export interface Execution {
  id: string;
  status: ExecutionStatus;
  triggerType: TriggerType;
  attempt: number;
  maxAttempts: number;
  previousAttemptId: string | null;
  nextRetryAt: string | null;
  retryCancelledReason: RetryCancelledReason | null;
  executedBy: string;
  startedAt: string;
  lastHeartbeatAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  errorMessage: string | null;
  parameters: Record<string, unknown>;
}

// ---- Common ------------------------------------------------------------------------------------------------------

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** RFC 7807 problem detail returned for every error, plus the backend's extra properties. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: ErrorCode;
  /** VALIDATION_FAILED: messages keyed by field path, e.g. "name", "parameters.to", "timing.runAt". */
  errors?: Record<string, string>;
  /** DUPLICATE_SCHEDULING */
  conflictingScheduling?: { id: string; name: string };
  /** EDIT_LOCKED */
  lockedBy?: string;
  since?: string;
}
