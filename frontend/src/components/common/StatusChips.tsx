import { Chip } from '@mui/material';
import type { ExecutionStatus, SchedulingStatus } from '../../types/api';
import {
  EXECUTION_STATUS_COLOR,
  EXECUTION_STATUS_LABEL,
  SCHEDULING_STATUS_COLOR,
  SCHEDULING_STATUS_LABEL,
} from '../../utils/status';

export function SchedulingStatusChip({ status }: { status: SchedulingStatus }) {
  return (
    <Chip
      size="small"
      label={SCHEDULING_STATUS_LABEL[status]}
      color={SCHEDULING_STATUS_COLOR[status]}
      variant={status === 'ACTIVE' ? 'filled' : 'outlined'}
    />
  );
}

export function ExecutionStatusChip({ status }: { status: ExecutionStatus }) {
  return <Chip size="small" label={EXECUTION_STATUS_LABEL[status]} color={EXECUTION_STATUS_COLOR[status]} />;
}
