import type { ParameterDefinition, Task } from '../types/api';

/** Form value of one parameter: text inputs hold strings (numbers too), switches hold booleans. */
export type ParamFormValue = string | boolean;
export type ParamFormValues = Record<string, ParamFormValue>;

// Same limits as the backend's ParameterValidator.
export const MAX_STRING_LENGTH = 500;
export const MAX_TEXT_LENGTH = 5000;
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

export const PARAMETER_PREFIX = 'parameters.';

function toFormValue(definition: ParameterDefinition, value: unknown): ParamFormValue {
  if (definition.type === 'BOOLEAN') {
    return value === true || value === 'true';
  }
  return value == null ? '' : String(value);
}

/** Initial values for a task's parameters: the schema defaults. */
export function defaultParamValues(task: Task | undefined): ParamFormValues {
  const values: ParamFormValues = {};
  task?.parameters.forEach((p) => {
    values[p.name] = toFormValue(p, p.defaultValue);
  });
  return values;
}

/** Form values for existing parameters (missing ones get their default). */
export function paramValuesFrom(task: Task | undefined, existing: Record<string, unknown>): ParamFormValues {
  const values: ParamFormValues = {};
  task?.parameters.forEach((p) => {
    values[p.name] = toFormValue(p, p.name in existing ? existing[p.name] : p.defaultValue);
  });
  return values;
}

function formatBound(value: number): string {
  return Number.isInteger(value) ? String(value) : String(value);
}

/** Validates one value; the same rules (and messages) as the backend. Returns null when valid. */
export function validateParam(definition: ParameterDefinition, value: ParamFormValue | undefined): string | null {
  if (definition.type === 'BOOLEAN') {
    return null;
  }
  const text = typeof value === 'string' ? value.trim() : value == null ? '' : String(value);
  if (text === '') {
    // A default value satisfies "required": the backend uses it when nothing is sent.
    return definition.required && definition.defaultValue == null ? 'is required' : null;
  }
  switch (definition.type) {
    case 'STRING':
      return text.length > MAX_STRING_LENGTH ? `must be at most ${MAX_STRING_LENGTH} characters` : null;
    case 'TEXT':
      return text.length > MAX_TEXT_LENGTH ? `must be at most ${MAX_TEXT_LENGTH} characters` : null;
    case 'EMAIL':
      return text.length > MAX_STRING_LENGTH || !EMAIL_PATTERN.test(text) ? 'must be a valid email address' : null;
    case 'NUMBER': {
      const number = Number(text);
      if (!Number.isFinite(number)) {
        return 'must be a number';
      }
      if (definition.min != null && number < definition.min) {
        return `must be at least ${formatBound(definition.min)}`;
      }
      if (definition.max != null && number > definition.max) {
        return `must be at most ${formatBound(definition.max)}`;
      }
      return null;
    }
    case 'ENUM': {
      const allowed = definition.allowedValues ?? [];
      return allowed.includes(text) ? null : `must be one of: ${allowed.join(', ')}`;
    }
    default:
      return null;
  }
}

/** All parameter errors, keyed like the backend: "parameters.<name>". */
export function validateParams(task: Task, values: ParamFormValues): Record<string, string> {
  const errors: Record<string, string> = {};
  task.parameters.forEach((p) => {
    const error = validateParam(p, values[p.name]);
    if (error) {
      errors[PARAMETER_PREFIX + p.name] = error;
    }
  });
  return errors;
}

/**
 * Request body values: only the task's own parameters, typed (numbers as numbers, booleans as booleans);
 * blank text is left out so the backend applies the default.
 */
export function toRequestParameters(task: Task, values: ParamFormValues): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  task.parameters.forEach((p) => {
    const value = values[p.name];
    if (p.type === 'BOOLEAN') {
      result[p.name] = value === true;
      return;
    }
    const text = typeof value === 'string' ? value.trim() : '';
    if (text === '') {
      return;
    }
    result[p.name] = p.type === 'NUMBER' ? Number(text) : text;
  });
  return result;
}

function formatValue(value: unknown): string {
  if (value == null) {
    return '—';
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

/** Compact one-line view: "message: Hello · level: INFO" (long values shortened). */
export function formatParameters(parameters: Record<string, unknown>, maxValueLength = 40): string {
  const entries = Object.entries(parameters ?? {});
  if (entries.length === 0) {
    return '—';
  }
  return entries
    .map(([key, value]) => {
      const text = formatValue(value);
      return `${key}: ${text.length > maxValueLength ? `${text.slice(0, maxValueLength - 1)}…` : text}`;
    })
    .join(' · ');
}
