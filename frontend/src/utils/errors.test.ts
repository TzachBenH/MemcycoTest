import { AxiosError, AxiosHeaders, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { describe, expect, it } from 'vitest';
import { ApiError, capitalize, describeFieldError, errorMessage, toApiError } from './errors';

const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig;

function axiosError(status: number, data: unknown): AxiosError {
  const response = { status, statusText: '', headers: {}, config, data } as AxiosResponse;
  return new AxiosError('Request failed', 'ERR_BAD_RESPONSE', config, {}, response);
}

describe('toApiError', () => {
  it('reads the problem detail of a validation error', () => {
    const error = toApiError(
      axiosError(400, {
        title: 'Invalid request',
        status: 400,
        detail: 'The request has 2 invalid field(s)',
        code: 'VALIDATION_FAILED',
        errors: { name: 'is required', 'parameters.to': 'must be a valid email address' },
      }),
    );
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.message).toBe('The request has 2 invalid field(s)');
    expect(error.fieldErrors).toEqual({ name: 'is required', 'parameters.to': 'must be a valid email address' });
  });

  it('exposes the conflicting scheduling of a duplicate', () => {
    const error = toApiError(
      axiosError(409, {
        detail: '"Nightly" is already scheduled',
        code: 'DUPLICATE_SCHEDULING',
        conflictingScheduling: { id: 'abc', name: 'Nightly' },
      }),
    );
    expect(error.code).toBe('DUPLICATE_SCHEDULING');
    expect(error.conflictingScheduling).toEqual({ id: 'abc', name: 'Nightly' });
    expect(error.lockedBy).toBeNull();
  });

  it('exposes who holds an edit lock', () => {
    const error = toApiError(
      axiosError(409, { detail: 'Being edited', code: 'EDIT_LOCKED', lockedBy: 'alice', since: '2026-11-26T10:00:00Z' }),
    );
    expect(error.lockedBy).toBe('alice');
    expect(error.lockedSince).toBe('2026-11-26T10:00:00Z');
    expect(error.conflictingScheduling).toBeNull();
  });

  it('handles responses that are not problem details', () => {
    const error = toApiError(axiosError(502, '<html>Bad gateway</html>'));
    expect(error.status).toBe(502);
    expect(error.code).toBe('UNKNOWN_ERROR');
    expect(error.message).toBe('Request failed (502)');
  });

  it('uses the title when there is no detail, and the status when there is neither', () => {
    expect(toApiError(axiosError(404, { title: 'Not found', code: 'NOT_FOUND' })).message).toBe('Not found');
    expect(toApiError(axiosError(500, { code: 'INTERNAL_ERROR' })).message).toBe('Request failed (500)');
  });

  it('reports network errors', () => {
    const error = toApiError(new AxiosError('Network Error', 'ERR_NETWORK', config, {}));
    expect(error.status).toBe(0);
    expect(error.code).toBe('NETWORK_ERROR');
    expect(error.message).toMatch(/Cannot reach the server/);
  });

  it('wraps other errors and passes ApiErrors through', () => {
    expect(toApiError(new Error('boom')).message).toBe('boom');
    expect(toApiError('weird').message).toBe('Something went wrong.');
    const original = new ApiError({ status: 409, code: 'EDIT_LOCK_LOST', message: 'lost' });
    expect(toApiError(original)).toBe(original);
  });
});

describe('message helpers', () => {
  it('capitalizes field messages', () => {
    expect(capitalize('is required')).toBe('Is required');
    expect(capitalize('')).toBe('');
  });

  it('describes errors of fields that are not on screen', () => {
    expect(describeFieldError('version', 'is required')).toBe('Version: is required');
    expect(describeFieldError('parameters.to', 'is required')).toBe('Parameters › to: is required');
  });

  it('returns a user-facing message for anything thrown', () => {
    expect(errorMessage(new Error('Oops'))).toBe('Oops');
  });
});
