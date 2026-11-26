import axios from 'axios';
import type { ErrorCode, ProblemDetail } from '../types/api';

export type ApiErrorCode = ErrorCode | 'NETWORK_ERROR' | 'UNKNOWN_ERROR';

interface ApiErrorInit {
  status: number;
  code: ApiErrorCode;
  message: string;
  fieldErrors?: Record<string, string>;
  problem?: ProblemDetail | null;
}

/**
 * Every failed API call is turned into an ApiError (see the axios client): the backend's RFC 7807 problem detail
 * with its machine-readable code, per-field validation messages and extra properties.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: ApiErrorCode;
  /** Validation messages keyed by field path ("name", "parameters.to", "timing.runAt"). */
  readonly fieldErrors: Record<string, string>;
  readonly problem: ProblemDetail | null;

  constructor({ status, code, message, fieldErrors = {}, problem = null }: ApiErrorInit) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.problem = problem;
  }

  /** DUPLICATE_SCHEDULING: the active scheduling that already has the same task, parameters and timing. */
  get conflictingScheduling(): { id: string; name: string } | null {
    return this.problem?.conflictingScheduling ?? null;
  }

  /** EDIT_LOCKED: who holds the edit lock, and since when. */
  get lockedBy(): string | null {
    return this.problem?.lockedBy ?? null;
  }

  get lockedSince(): string | null {
    return this.problem?.since ?? null;
  }
}

function isProblemDetail(data: unknown): data is ProblemDetail {
  return typeof data === 'object' && data !== null && ('code' in data || 'detail' in data || 'title' in data);
}

/** Converts anything thrown by an API call into an ApiError. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  if (axios.isAxiosError(error)) {
    const response = error.response;
    if (!response) {
      return new ApiError({
        status: 0,
        code: 'NETWORK_ERROR',
        message: 'Cannot reach the server. Check your connection and try again.',
      });
    }
    const data: unknown = response.data;
    if (isProblemDetail(data)) {
      return new ApiError({
        status: response.status,
        code: data.code ?? 'UNKNOWN_ERROR',
        message: data.detail || data.title || `Request failed (${response.status})`,
        fieldErrors: data.errors ?? {},
        problem: data,
      });
    }
    return new ApiError({
      status: response.status,
      code: 'UNKNOWN_ERROR',
      message: `Request failed (${response.status})`,
    });
  }
  return new ApiError({
    status: 0,
    code: 'UNKNOWN_ERROR',
    message: error instanceof Error && error.message ? error.message : 'Something went wrong.',
  });
}

/** A message that can be shown to the user for any error. */
export function errorMessage(error: unknown): string {
  return toApiError(error).message;
}

/** Backend field messages are fragments ("is required"); shown under a field they read better capitalized. */
export function capitalize(text: string): string {
  return text ? text.charAt(0).toUpperCase() + text.slice(1) : text;
}

/** "parameters.to" + "is required" → "Parameters › to: is required" (for errors not shown under a field). */
export function describeFieldError(path: string, message: string): string {
  const readablePath = path
    .split('.')
    .map((part, index) => (index === 0 ? capitalize(part) : part))
    .join(' › ');
  return `${readablePath}: ${message}`;
}
