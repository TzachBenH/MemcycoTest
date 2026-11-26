import {
  Box,
  Chip,
  FormControl,
  FormHelperText,
  FormLabel,
  MenuItem,
  Stack,
  Tab,
  Tabs,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import { TimePicker } from '@mui/x-date-pickers/TimePicker';
import { Controller, useWatch, type Control } from 'react-hook-form';
import type { DayCode, TimingType } from '../../types/api';
import { DAY_CODES, DAY_LONG_LABEL, DAY_SHORT_LABEL, sortDays } from '../../utils/days';
import { capitalize } from '../../utils/errors';
import type { SchedulingFormValues } from '../../utils/schedulingForm';
import { CRON_EXAMPLES, MAX_CRON_LENGTH, MAX_INTERVAL } from '../../utils/timingForm';
import { describeCron } from '../../utils/timingText';
import { TimezoneSelect } from '../common/TimezoneSelect';

interface ScheduleTimingEditorProps {
  control: Control<SchedulingFormValues>;
  disabled?: boolean;
}

const TIMING_TABS: ReadonlyArray<{ value: TimingType; label: string }> = [
  { value: 'ONE_TIME', label: 'One time' },
  { value: 'INTERVAL', label: 'Repeat' },
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'CRON', label: 'Cron' },
];

const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm';

const message = (error: string | undefined) => (error ? capitalize(error) : undefined);

/** "When" part of the form: one time / every N minutes or hours / weekly / Quartz cron, plus the time zone. */
export function ScheduleTimingEditor({ control, disabled }: ScheduleTimingEditorProps) {
  const type = useWatch({ control, name: 'timing.type' });
  const timezone = useWatch({ control, name: 'timing.timezone' });
  const zoneText = timezone ? ` (${timezone})` : '';

  return (
    <Stack spacing={2}>
      <Controller
        name="timing.type"
        control={control}
        render={({ field }) => (
          <Tabs
            value={field.value}
            onChange={(_, value: TimingType) => field.onChange(value)}
            variant="scrollable"
            allowScrollButtonsMobile
            aria-label="Timing type"
          >
            {TIMING_TABS.map((tab) => (
              <Tab key={tab.value} value={tab.value} label={tab.label} disabled={disabled} />
            ))}
          </Tabs>
        )}
      />

      {type === 'ONE_TIME' && (
        <Controller
          name="timing.runAt"
          control={control}
          render={({ field, fieldState }) => (
            <DateTimePicker
              label="Run at"
              value={field.value}
              onChange={(value) => field.onChange(value)}
              ampm={false}
              format={DATE_TIME_FORMAT}
              disabled={disabled}
              slotProps={{
                textField: {
                  required: true,
                  fullWidth: true,
                  name: field.name,
                  onBlur: field.onBlur,
                  error: !!fieldState.error,
                  helperText: message(fieldState.error?.message) ?? `Runs once at this time${zoneText}.`,
                },
              }}
            />
          )}
        />
      )}

      {type === 'INTERVAL' && (
        <Stack spacing={2}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <Controller
              name="timing.every"
              control={control}
              render={({ field, fieldState }) => (
                <TextField
                  label="Every"
                  type="number"
                  required
                  name={field.name}
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  inputRef={field.ref}
                  disabled={disabled}
                  error={!!fieldState.error}
                  helperText={message(fieldState.error?.message)}
                  slotProps={{ htmlInput: { min: 1, max: MAX_INTERVAL, step: 1 } }}
                  sx={{ minWidth: 140 }}
                />
              )}
            />
            <Controller
              name="timing.unit"
              control={control}
              render={({ field, fieldState }) => (
                <TextField
                  select
                  label="Unit"
                  required
                  name={field.name}
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  disabled={disabled}
                  error={!!fieldState.error}
                  helperText={message(fieldState.error?.message)}
                  sx={{ minWidth: 160 }}
                >
                  <MenuItem value="MINUTES">Minutes</MenuItem>
                  <MenuItem value="HOURS">Hours</MenuItem>
                </TextField>
              )}
            />
          </Stack>
          <Controller
            name="timing.startAt"
            control={control}
            render={({ field, fieldState }) => (
              <DateTimePicker
                label="Start at (optional)"
                value={field.value}
                onChange={(value) => field.onChange(value)}
                ampm={false}
                format={DATE_TIME_FORMAT}
                disabled={disabled}
                slotProps={{
                  field: { clearable: true },
                  textField: {
                    fullWidth: true,
                    name: field.name,
                    onBlur: field.onBlur,
                    error: !!fieldState.error,
                    helperText:
                      message(fieldState.error?.message) ??
                      `Empty = starts now (the first run is right away). Runs are counted from the start time${zoneText}.`,
                  },
                }}
              />
            )}
          />
        </Stack>
      )}

      {type === 'WEEKLY' && (
        <Stack spacing={2}>
          <Controller
            name="timing.days"
            control={control}
            render={({ field, fieldState }) => (
              <FormControl error={!!fieldState.error} required disabled={disabled}>
                <FormLabel id="weekly-days-label" sx={{ mb: 1 }}>
                  Days
                </FormLabel>
                <ToggleButtonGroup
                  value={field.value}
                  onChange={(_, days: DayCode[]) => field.onChange(sortDays(days ?? []))}
                  aria-labelledby="weekly-days-label"
                  size="small"
                  color="primary"
                  disabled={disabled}
                  sx={{ flexWrap: 'wrap' }}
                >
                  {DAY_CODES.map((code) => (
                    <ToggleButton key={code} value={code} aria-label={DAY_LONG_LABEL[code]} sx={{ px: 2 }}>
                      {DAY_SHORT_LABEL[code]}
                    </ToggleButton>
                  ))}
                </ToggleButtonGroup>
                {fieldState.error && <FormHelperText>{message(fieldState.error.message)}</FormHelperText>}
              </FormControl>
            )}
          />
          <Controller
            name="timing.time"
            control={control}
            render={({ field, fieldState }) => (
              <TimePicker
                label="Time"
                value={field.value}
                onChange={(value) => field.onChange(value)}
                ampm={false}
                format="HH:mm"
                disabled={disabled}
                slotProps={{
                  textField: {
                    required: true,
                    name: field.name,
                    onBlur: field.onBlur,
                    error: !!fieldState.error,
                    helperText: message(fieldState.error?.message) ?? `Wall-clock time${zoneText}.`,
                    sx: { maxWidth: 220 },
                  },
                }}
              />
            )}
          />
        </Stack>
      )}

      {type === 'CRON' && (
        <Controller
          name="timing.expression"
          control={control}
          render={({ field, fieldState }) => {
            const description = describeCron(field.value);
            const hint = description
              ? `“${description}”`
              : field.value.trim()
                ? 'Not recognized yet — the server checks the exact Quartz syntax on save.'
                : 'Quartz syntax, seconds first: second minute hour day-of-month month day-of-week [year]. Use ? for "no specific value".';
            return (
              <Stack spacing={1.5}>
                <TextField
                  label="Cron expression (Quartz)"
                  required
                  name={field.name}
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  inputRef={field.ref}
                  disabled={disabled}
                  placeholder="0 0 9 ? * MON-FRI"
                  error={!!fieldState.error}
                  helperText={message(fieldState.error?.message) ?? hint}
                  slotProps={{
                    htmlInput: { maxLength: MAX_CRON_LENGTH, spellCheck: false, style: { fontFamily: 'monospace' } },
                  }}
                  fullWidth
                />
                <Box>
                  <Typography variant="caption" color="text.secondary" component="p" sx={{ mb: 0.5 }}>
                    Examples
                  </Typography>
                  <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                    {CRON_EXAMPLES.map((example) => (
                      <Chip
                        key={example.expression}
                        label={example.label}
                        title={example.expression}
                        size="small"
                        variant="outlined"
                        disabled={disabled}
                        onClick={() => field.onChange(example.expression)}
                      />
                    ))}
                  </Stack>
                </Box>
              </Stack>
            );
          }}
        />
      )}

      <Controller
        name="timing.timezone"
        control={control}
        render={({ field, fieldState }) => (
          <TimezoneSelect
            value={field.value}
            onChange={field.onChange}
            onBlur={field.onBlur}
            disabled={disabled}
            error={!!fieldState.error}
            helperText={message(fieldState.error?.message) ?? 'Times above are in this time zone.'}
          />
        )}
      />
    </Stack>
  );
}
