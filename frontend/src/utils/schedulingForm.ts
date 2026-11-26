import type { Scheduling, SchedulingRequest, Task } from '../types/api';
import type { Dayjs } from './dayjs';
import {
  PARAMETER_PREFIX,
  paramValuesFrom,
  toRequestParameters,
  validateParams,
  type ParamFormValues,
} from './params';
import {
  defaultTimingValues,
  formToTiming,
  timingFieldsOf,
  timingToForm,
  validateTiming,
  type TimingFormValues,
} from './timingForm';

/** Values of the create/edit form. Field paths match the API's error paths ("parameters.to", "timing.runAt"). */
export interface SchedulingFormValues {
  name: string;
  taskKey: string;
  parameters: ParamFormValues;
  timing: TimingFormValues;
  enabled: boolean;
}

export const MAX_NAME_LENGTH = 100;

export function emptySchedulingForm(timezone: string, now?: Dayjs): SchedulingFormValues {
  return {
    name: '',
    taskKey: '',
    parameters: {},
    timing: defaultTimingValues(timezone, now),
    enabled: true,
  };
}

export function schedulingToForm(scheduling: Scheduling, task: Task | undefined, now?: Dayjs): SchedulingFormValues {
  return {
    name: scheduling.name,
    taskKey: scheduling.task.key,
    parameters: task ? paramValuesFrom(task, scheduling.parameters) : {},
    timing: timingToForm(scheduling.timing, now),
    enabled: scheduling.enabled,
  };
}

export interface FormValidationOptions {
  now?: Dayjs;
  originalRunAt?: string | null;
}

/** All client-side errors at once (same rules and messages as the backend), keyed by field path. */
export function validateSchedulingForm(
  values: SchedulingFormValues,
  task: Task | undefined,
  options: FormValidationOptions = {},
): Record<string, string> {
  const errors: Record<string, string> = {};
  const name = values.name.trim();
  if (!name) {
    errors.name = 'is required';
  } else if (name.length > MAX_NAME_LENGTH) {
    errors.name = `must be at most ${MAX_NAME_LENGTH} characters`;
  }
  if (!values.taskKey) {
    errors.taskKey = 'is required';
  } else if (!task) {
    errors.taskKey = 'is not a known task';
  } else {
    Object.assign(errors, validateParams(task, values.parameters));
  }
  Object.assign(errors, validateTiming(values.timing, options));
  return errors;
}

export function toSchedulingRequest(values: SchedulingFormValues, task: Task, version?: number): SchedulingRequest {
  const request: SchedulingRequest = {
    name: values.name.trim(),
    taskKey: task.key,
    parameters: toRequestParameters(task, values.parameters),
    timing: formToTiming(values.timing),
    enabled: values.enabled,
  };
  if (version !== undefined) {
    request.version = version;
  }
  return request;
}

/** Paths of the fields currently on screen: server errors for these are shown under the field. */
export function visibleFieldPaths(values: SchedulingFormValues, task: Task | undefined): Set<string> {
  return new Set<string>([
    'name',
    'taskKey',
    ...(task?.parameters.map((p) => PARAMETER_PREFIX + p.name) ?? []),
    ...timingFieldsOf(values.timing.type),
  ]);
}

