import {
  FormControl,
  FormControlLabel,
  FormHelperText,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { Controller, type Control } from 'react-hook-form';
import type { ParameterDefinition, Task } from '../../types/api';
import { capitalize } from '../../utils/errors';
import type { SchedulingFormValues } from '../../utils/schedulingForm';

interface TaskParamsFieldsProps {
  task: Task | undefined;
  control: Control<SchedulingFormValues>;
  disabled?: boolean;
}

/** Hint under a field: its description, plus range / default when the schema has them. */
export function parameterHint(definition: ParameterDefinition): string {
  const parts: string[] = [];
  if (definition.description) {
    parts.push(definition.description);
  }
  if (definition.type === 'NUMBER' && (definition.min != null || definition.max != null)) {
    parts.push(`Range ${definition.min ?? '−∞'}–${definition.max ?? '∞'}.`);
  }
  if (definition.defaultValue != null && definition.type !== 'BOOLEAN') {
    parts.push(`Default: ${String(definition.defaultValue)}.`);
  }
  if (!definition.required && parts.length === 0) {
    parts.push('Optional.');
  }
  return parts.join(' ');
}

/** The form fields of a task's parameters, generated from the task's schema (GET /api/tasks). */
export function TaskParamsFields({ task, control, disabled }: TaskParamsFieldsProps) {
  if (!task) {
    return (
      <Typography variant="body2" color="text.secondary">
        Choose a task to see its parameters.
      </Typography>
    );
  }
  if (task.parameters.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        This task has no parameters.
      </Typography>
    );
  }
  return (
    <Stack spacing={2}>
      {task.parameters.map((definition) => (
        <ParameterField key={`${task.key}.${definition.name}`} definition={definition} control={control} disabled={disabled} />
      ))}
    </Stack>
  );
}

function ParameterField({ definition, control, disabled }: {
  definition: ParameterDefinition;
  control: Control<SchedulingFormValues>;
  disabled?: boolean;
}) {
  const id = `param-${definition.name}`;
  return (
    <Controller
      name={`parameters.${definition.name}`}
      control={control}
      render={({ field, fieldState }) => {
        const error = fieldState.error?.message;
        const helperText = error ? capitalize(error) : parameterHint(definition);

        if (definition.type === 'BOOLEAN') {
          return (
            <FormControl error={!!error} disabled={disabled}>
              <FormControlLabel
                control={
                  <Switch
                    id={id}
                    checked={field.value === true}
                    onChange={(event) => field.onChange(event.target.checked)}
                    onBlur={field.onBlur}
                  />
                }
                label={definition.label}
              />
              {helperText && <FormHelperText>{helperText}</FormHelperText>}
            </FormControl>
          );
        }

        const value = typeof field.value === 'string' ? field.value : '';
        const common = {
          id,
          name: field.name,
          label: definition.label,
          value,
          onChange: field.onChange,
          onBlur: field.onBlur,
          inputRef: field.ref,
          required: definition.required,
          error: !!error,
          helperText,
          disabled,
          fullWidth: true,
        };

        switch (definition.type) {
          case 'ENUM':
            return (
              <TextField {...common} select>
                {!definition.required && (
                  <MenuItem value="">
                    <em>None</em>
                  </MenuItem>
                )}
                {(definition.allowedValues ?? []).map((option) => (
                  <MenuItem key={option} value={option}>
                    {option}
                  </MenuItem>
                ))}
              </TextField>
            );
          case 'TEXT':
            return <TextField {...common} multiline minRows={3} maxRows={10} />;
          case 'NUMBER':
            return (
              <TextField
                {...common}
                type="number"
                slotProps={{ htmlInput: { min: definition.min, max: definition.max, step: 'any' } }}
              />
            );
          case 'EMAIL':
            return <TextField {...common} type="email" autoComplete="off" />;
          default:
            return <TextField {...common} autoComplete="off" />;
        }
      }}
    />
  );
}
