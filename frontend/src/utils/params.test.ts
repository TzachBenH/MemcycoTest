import { describe, expect, it } from 'vitest';
import { tasksFixture } from '../test/fixtures';
import type { ParameterDefinition, Task } from '../types/api';
import {
  defaultParamValues,
  formatParameters,
  paramValuesFrom,
  toRequestParameters,
  validateParam,
  validateParams,
} from './params';

const [logTask, emailTask, dbTask] = tasksFixture;

const flagTask: Task = {
  key: 'FLAGS',
  name: 'Flags',
  description: '',
  parameters: [{ name: 'dryRun', label: 'Dry run', type: 'BOOLEAN', required: false, defaultValue: true }],
};

const def = (overrides: Partial<ParameterDefinition>): ParameterDefinition => ({
  name: 'p',
  label: 'P',
  type: 'STRING',
  required: false,
  ...overrides,
});

describe('initial values', () => {
  it('uses schema defaults', () => {
    expect(defaultParamValues(logTask)).toEqual({ message: '', level: 'INFO' });
    expect(defaultParamValues(dbTask)).toEqual({ report: '', lastHours: '24' });
    expect(defaultParamValues(flagTask)).toEqual({ dryRun: true });
    expect(defaultParamValues(undefined)).toEqual({});
  });

  it('fills existing values and defaults for missing ones', () => {
    expect(paramValuesFrom(dbTask, { report: 'SCHEDULINGS_BY_TASK', lastHours: 12 })).toEqual({
      report: 'SCHEDULINGS_BY_TASK',
      lastHours: '12',
    });
    expect(paramValuesFrom(logTask, { message: 'Hi' })).toEqual({ message: 'Hi', level: 'INFO' });
    expect(paramValuesFrom(flagTask, { dryRun: false })).toEqual({ dryRun: false });
  });
});

describe('validateParam (same rules as the backend)', () => {
  it('requires values unless a default exists', () => {
    expect(validateParam(def({ required: true }), '  ')).toBe('is required');
    expect(validateParam(def({ required: true, defaultValue: 'x' }), '')).toBeNull();
    expect(validateParam(def({}), '')).toBeNull();
  });

  it('limits text length', () => {
    expect(validateParam(def({ type: 'STRING' }), 'a'.repeat(501))).toBe('must be at most 500 characters');
    expect(validateParam(def({ type: 'TEXT' }), 'a'.repeat(5001))).toBe('must be at most 5000 characters');
    expect(validateParam(def({ type: 'TEXT' }), 'a'.repeat(5000))).toBeNull();
  });

  it('checks email addresses', () => {
    expect(validateParam(def({ type: 'EMAIL' }), 'not-an-email')).toBe('must be a valid email address');
    expect(validateParam(def({ type: 'EMAIL' }), 'dev@example.com')).toBeNull();
  });

  it('checks numbers and their range', () => {
    const hours = dbTask.parameters[1];
    expect(validateParam(hours, 'abc')).toBe('must be a number');
    expect(validateParam(hours, '0')).toBe('must be at least 1');
    expect(validateParam(hours, '721')).toBe('must be at most 720');
    expect(validateParam(hours, '1.5')).toBeNull();
  });

  it('checks enum values', () => {
    expect(validateParam(logTask.parameters[1], 'DEBUG')).toBe('must be one of: INFO, WARN, ERROR');
    expect(validateParam(logTask.parameters[1], 'WARN')).toBeNull();
  });

  it('accepts any boolean', () => {
    expect(validateParam(flagTask.parameters[0], false)).toBeNull();
  });
});

describe('validateParams', () => {
  it('keys errors like the API ("parameters.<name>")', () => {
    expect(validateParams(emailTask, { to: 'bad', subject: '', body: '' })).toEqual({
      'parameters.to': 'must be a valid email address',
      'parameters.subject': 'is required',
    });
  });
});

describe('toRequestParameters', () => {
  it('sends typed values of the task only, without blanks', () => {
    expect(toRequestParameters(dbTask, { report: 'EXECUTIONS_BY_STATUS', lastHours: ' 48 ', other: 'x' })).toEqual({
      report: 'EXECUTIONS_BY_STATUS',
      lastHours: 48,
    });
    expect(toRequestParameters(emailTask, { to: ' dev@example.com ', subject: 'Hi', body: '   ' })).toEqual({
      to: 'dev@example.com',
      subject: 'Hi',
    });
    expect(toRequestParameters(flagTask, { dryRun: false })).toEqual({ dryRun: false });
  });
});

describe('formatParameters', () => {
  it('renders a compact line', () => {
    expect(formatParameters({ message: 'Hello', level: 'INFO' })).toBe('message: Hello · level: INFO');
    expect(formatParameters({})).toBe('—');
    expect(formatParameters({ nested: { a: 1 }, none: null })).toBe('nested: {"a":1} · none: —');
  });

  it('shortens long values', () => {
    const text = formatParameters({ body: 'x'.repeat(100) }, 10);
    expect(text).toBe(`body: ${'x'.repeat(9)}…`);
  });
});
