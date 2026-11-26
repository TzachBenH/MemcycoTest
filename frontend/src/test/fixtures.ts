import type { Execution, Page, Scheduling, Task } from '../types/api';

/** The three predefined tasks, exactly as GET /api/tasks returns them. */
export const tasksFixture: Task[] = [
  {
    key: 'LOG',
    name: 'Log Message',
    description: 'Writes a message to the application log.',
    parameters: [
      {
        name: 'message',
        label: 'Log message',
        type: 'STRING',
        required: true,
        description: 'The text to write to the log.',
      },
      {
        name: 'level',
        label: 'Level',
        type: 'ENUM',
        required: false,
        defaultValue: 'INFO',
        allowedValues: ['INFO', 'WARN', 'ERROR'],
        description: 'Log level of the message.',
      },
    ],
  },
  {
    key: 'DUMMY_EMAIL',
    name: 'Send Email (simulated)',
    description: 'Simulates sending an email and logs it. No real email is sent.',
    parameters: [
      { name: 'to', label: 'To', type: 'EMAIL', required: true, description: 'Recipient email address.' },
      { name: 'subject', label: 'Subject', type: 'STRING', required: true, description: '' },
      { name: 'body', label: 'Body', type: 'TEXT', required: false, description: 'Optional message body.' },
    ],
  },
  {
    key: 'DB_QUERY',
    name: 'Database Report',
    description: 'Runs a predefined read-only report on the database and writes the result to the log.',
    parameters: [
      {
        name: 'report',
        label: 'Report',
        type: 'ENUM',
        required: true,
        allowedValues: ['SCHEDULINGS_BY_STATUS', 'SCHEDULINGS_BY_TASK', 'EXECUTIONS_BY_STATUS'],
        description: 'Which predefined report to run.',
      },
      {
        name: 'lastHours',
        label: 'Last hours',
        type: 'NUMBER',
        required: false,
        defaultValue: 24,
        min: 1,
        max: 720,
        description: 'Time window, used by EXECUTIONS_BY_STATUS only.',
      },
    ],
  },
];

let sequence = 0;

export function makeScheduling(overrides: Partial<Scheduling> = {}): Scheduling {
  sequence += 1;
  return {
    id: `00000000-0000-4000-8000-${String(sequence).padStart(12, '0')}`,
    name: `Scheduling ${sequence}`,
    task: { key: 'LOG', name: 'Log Message', known: true },
    parameters: { level: 'INFO', message: 'Hello' },
    timing: { type: 'INTERVAL', every: 15, unit: 'MINUTES', startAt: '2026-11-20T08:00:00Z', timezone: 'UTC' },
    enabled: true,
    status: 'ACTIVE',
    nextFireTime: '2030-01-01T10:15:00Z',
    lastExecution: null,
    editLock: null,
    warnings: [],
    version: 0,
    createdAt: '2026-11-20T08:00:00Z',
    updatedAt: '2026-11-20T08:00:00Z',
    createdBy: 'anonymous',
    updatedBy: 'anonymous',
    ...overrides,
  };
}

export function makeExecution(overrides: Partial<Execution> = {}): Execution {
  sequence += 1;
  return {
    id: `10000000-0000-4000-8000-${String(sequence).padStart(12, '0')}`,
    status: 'SUCCESS',
    triggerType: 'SCHEDULED',
    attempt: 1,
    maxAttempts: 3,
    previousAttemptId: null,
    nextRetryAt: null,
    retryCancelledReason: null,
    executedBy: 'backend-1',
    startedAt: '2026-11-26T10:00:00Z',
    lastHeartbeatAt: '2026-11-26T10:00:00Z',
    finishedAt: '2026-11-26T10:00:01.250Z',
    durationMs: 1250,
    errorMessage: null,
    parameters: { level: 'INFO', message: 'Hello' },
    ...overrides,
  };
}

export function pageOf<T>(content: T[], page = 0, size = 20, totalElements = content.length): Page<T> {
  return { content, page, size, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / size)) };
}
