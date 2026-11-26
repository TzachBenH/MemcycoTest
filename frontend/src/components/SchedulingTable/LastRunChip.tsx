import { Chip, Stack, Tooltip, Typography } from '@mui/material';
import type { LastExecution } from '../../types/api';
import { TIME_SECONDS_FORMAT, formatDateTime, formatRelative, DATE_TIME_SECONDS_FORMAT } from '../../utils/dates';
import {
  EXECUTION_STATUS_COLOR,
  EXECUTION_STATUS_LABEL,
  RETRY_CANCELLED_LABEL,
  TRIGGER_TYPE_LABEL,
  isRetryPending,
} from '../../utils/status';

/**
 * "Last run" column: status chip, and under it either "↻ 2/3 at 10:05:12" (a retry is pending), why a retry was
 * cancelled, or how long ago it ran. Details (trigger, attempt, error) in the tooltip.
 */
export function LastRunChip({ execution }: { execution: LastExecution | null }) {
  if (!execution) {
    return (
      <Typography variant="body2" color="text.secondary" component="span">
        Never
      </Typography>
    );
  }
  const pending = isRetryPending(execution);
  const nextAttempt = execution.attempt + 1;
  const details = [
    `${TRIGGER_TYPE_LABEL[execution.triggerType]} run, attempt ${execution.attempt}/${execution.maxAttempts}`,
    `Started ${formatDateTime(execution.startedAt, undefined, DATE_TIME_SECONDS_FORMAT)}`,
    execution.errorMessage ? `Error: ${execution.errorMessage}` : null,
    pending
      ? `Retry ${nextAttempt}/${execution.maxAttempts} at ${formatDateTime(execution.nextRetryAt, undefined, TIME_SECONDS_FORMAT)}`
      : null,
    execution.retryCancelledReason ? `Retry cancelled: ${RETRY_CANCELLED_LABEL[execution.retryCancelledReason]}` : null,
  ].filter((line): line is string => line !== null);

  let caption: string;
  if (pending) {
    caption = `↻ ${nextAttempt}/${execution.maxAttempts} at ${formatDateTime(execution.nextRetryAt, undefined, TIME_SECONDS_FORMAT)}`;
  } else if (execution.retryCancelledReason) {
    caption = `Retry cancelled (${RETRY_CANCELLED_LABEL[execution.retryCancelledReason]})`;
  } else {
    caption = formatRelative(execution.startedAt);
  }

  return (
    <Tooltip
      title={
        <>
          {details.map((line) => (
            <div key={line}>{line}</div>
          ))}
        </>
      }
    >
      <Stack spacing={0.25} alignItems="flex-start">
        <Chip
          size="small"
          label={
            execution.attempt > 1
              ? `${EXECUTION_STATUS_LABEL[execution.status]} ${execution.attempt}/${execution.maxAttempts}`
              : EXECUTION_STATUS_LABEL[execution.status]
          }
          color={EXECUTION_STATUS_COLOR[execution.status]}
        />
        <Typography variant="caption" color={pending ? 'warning.main' : 'text.secondary'}>
          {caption}
        </Typography>
      </Stack>
    </Tooltip>
  );
}
