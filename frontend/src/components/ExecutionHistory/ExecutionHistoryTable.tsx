import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import { useNow } from '../../hooks/useNow';
import type { Execution } from '../../types/api';
import { DATE_TIME_SECONDS_FORMAT, TIME_SECONDS_FORMAT, formatDateTime, formatDuration } from '../../utils/dates';
import { RETRY_CANCELLED_LABEL, TRIGGER_TYPE_LABEL } from '../../utils/status';
import { ExecutionStatusChip } from '../common/StatusChips';

interface ExecutionHistoryTableProps {
  rows: Execution[];
  page: number;
  size: number;
  total: number;
  onPageChange: (page: number) => void;
}

function retryText(execution: Execution): string {
  if (execution.retryCancelledReason) {
    return `Cancelled: ${RETRY_CANCELLED_LABEL[execution.retryCancelledReason]}`;
  }
  if (execution.nextRetryAt) {
    return `Retry at ${formatDateTime(execution.nextRetryAt, undefined, TIME_SECONDS_FORMAT)}`;
  }
  return '—';
}

/** One row per attempt, newest first. Running attempts show a live duration. */
export function ExecutionHistoryTable({ rows, page, size, total, onPageChange }: ExecutionHistoryTableProps) {
  const anyRunning = rows.some((row) => row.status === 'RUNNING');
  const now = useNow(1000, anyRunning);

  return (
    <>
      <TableContainer>
        <Table size="small" aria-label="Execution history">
          <TableHead>
            <TableRow>
              <TableCell>Status</TableCell>
              <TableCell>Trigger</TableCell>
              <TableCell>Attempt</TableCell>
              <TableCell>Instance</TableCell>
              <TableCell>Started</TableCell>
              <TableCell>Duration</TableCell>
              <TableCell>Error</TableCell>
              <TableCell>Retry</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((execution) => {
              const duration =
                execution.status === 'RUNNING'
                  ? Math.max(0, now - new Date(execution.startedAt).getTime())
                  : execution.durationMs;
              return (
                <TableRow key={execution.id} hover>
                  <TableCell>
                    <ExecutionStatusChip status={execution.status} />
                  </TableCell>
                  <TableCell>{TRIGGER_TYPE_LABEL[execution.triggerType]}</TableCell>
                  <TableCell>
                    {execution.attempt}/{execution.maxAttempts}
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                      {execution.executedBy}
                    </Typography>
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {formatDateTime(execution.startedAt, undefined, DATE_TIME_SECONDS_FORMAT)}
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {formatDuration(duration)}
                    {execution.status === 'RUNNING' && ' …'}
                  </TableCell>
                  <TableCell sx={{ maxWidth: 320 }}>
                    {execution.errorMessage ? (
                      <Tooltip title={execution.errorMessage}>
                        <Typography variant="body2" color="error" noWrap>
                          {execution.errorMessage}
                        </Typography>
                      </Tooltip>
                    ) : (
                      '—'
                    )}
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{retryText(execution)}</TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
      <TablePagination
        component="div"
        count={total}
        page={page}
        rowsPerPage={size}
        rowsPerPageOptions={[size]}
        onPageChange={(_, next) => onPageChange(next)}
      />
    </>
  );
}
