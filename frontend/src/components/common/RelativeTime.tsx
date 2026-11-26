import { Tooltip, Typography } from '@mui/material';
import { DATE_TIME_SECONDS_FORMAT, browserTimezone, formatDateTime, formatRelative } from '../../utils/dates';

/** "in 5 minutes" with the exact time (in the scheduling's zone and the browser's) in a tooltip. */
export function RelativeTime({ value, timezone, emptyText = '—' }: {
  value: string | null | undefined;
  timezone?: string;
  emptyText?: string;
}) {
  if (!value) {
    return (
      <Typography variant="body2" color="text.secondary" component="span">
        {emptyText}
      </Typography>
    );
  }
  const local = browserTimezone();
  const exact =
    timezone && timezone !== local
      ? `${formatDateTime(value, timezone, DATE_TIME_SECONDS_FORMAT)} (${timezone}) · ${formatDateTime(value, local, DATE_TIME_SECONDS_FORMAT)} (${local})`
      : `${formatDateTime(value, local, DATE_TIME_SECONDS_FORMAT)} (${local})`;
  return (
    <Tooltip title={exact}>
      <Typography variant="body2" component="span">
        {formatRelative(value)}
      </Typography>
    </Tooltip>
  );
}
