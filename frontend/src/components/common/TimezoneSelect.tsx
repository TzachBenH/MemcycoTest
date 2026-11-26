import { Autocomplete, TextField } from '@mui/material';
import { useMemo } from 'react';
import { browserTimezone } from '../../utils/dates';

let cachedZones: string[] | null = null;

/** All IANA time zones the browser knows, plus UTC and the browser's own zone. */
export function timezoneOptions(): string[] {
  if (!cachedZones) {
    let zones: string[] = [];
    try {
      zones = Intl.supportedValuesOf('timeZone');
    } catch {
      zones = [];
    }
    cachedZones = Array.from(new Set(['UTC', browserTimezone(), ...zones])).sort((a, b) => a.localeCompare(b));
  }
  return cachedZones;
}

interface TimezoneSelectProps {
  value: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
  error?: boolean;
  helperText?: string;
  disabled?: boolean;
}

export function TimezoneSelect({ value, onChange, onBlur, error, helperText, disabled }: TimezoneSelectProps) {
  const options = useMemo(() => {
    const zones = timezoneOptions();
    return value && !zones.includes(value) ? [value, ...zones] : zones;
  }, [value]);

  return (
    <Autocomplete
      options={options}
      value={value || null}
      onChange={(_, zone) => onChange(zone ?? '')}
      onBlur={onBlur}
      disabled={disabled}
      autoHighlight
      renderInput={(params) => (
        <TextField {...params} label="Timezone" required error={error} helperText={helperText} />
      )}
    />
  );
}
