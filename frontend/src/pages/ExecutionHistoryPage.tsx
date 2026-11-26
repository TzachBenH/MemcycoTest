import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import { Alert, Box, Button, MenuItem, Paper, Skeleton, Stack, TextField, Typography } from '@mui/material';
import { Link as RouterLink, useParams, useSearchParams } from 'react-router-dom';
import { useNotify } from '../components/common/NotificationProvider';
import { RelativeTime } from '../components/common/RelativeTime';
import { EmptyState, ErrorState, TableSkeleton } from '../components/common/States';
import { SchedulingStatusChip } from '../components/common/StatusChips';
import { ExecutionHistoryTable } from '../components/ExecutionHistory/ExecutionHistoryTable';
import { useExecutions } from '../hooks/useExecutions';
import { useScheduling, useSchedulingActions } from '../hooks/useSchedulings';
import type { ExecutionStatus } from '../types/api';
import { errorMessage, toApiError } from '../utils/errors';
import { EXECUTION_STATUSES, EXECUTION_STATUS_LABEL } from '../utils/status';
import { describeTiming } from '../utils/timingText';

export const HISTORY_PAGE_SIZE = 20;

function parseStatus(value: string | null): ExecutionStatus | '' {
  return value && (EXECUTION_STATUSES as readonly string[]).includes(value) ? (value as ExecutionStatus) : '';
}

function parsePage(value: string | null): number {
  const page = Number(value);
  return Number.isInteger(page) && page >= 1 ? page - 1 : 0;
}

/** Separate page with one scheduling's runs (agreed: not a dialog), newest first, refreshed every 10 seconds. */
export function ExecutionHistoryPage() {
  const { id = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const status = parseStatus(params.get('status'));
  const page = parsePage(params.get('page'));

  const scheduling = useScheduling(id);
  const executions = useExecutions(id, { status, page, size: HISTORY_PAGE_SIZE });
  const actions = useSchedulingActions();
  const notify = useNotify();

  const setStatus = (next: ExecutionStatus | '') =>
    setParams(
      (current) => {
        const result = new URLSearchParams(current);
        if (next) {
          result.set('status', next);
        } else {
          result.delete('status');
        }
        result.delete('page');
        return result;
      },
      { replace: true },
    );

  const setPage = (next: number) =>
    setParams(
      (current) => {
        const result = new URLSearchParams(current);
        if (next > 0) {
          result.set('page', String(next + 1));
        } else {
          result.delete('page');
        }
        return result;
      },
      { replace: true },
    );

  const runNow = async () => {
    try {
      await actions.runNow.mutateAsync(id);
      notify.success('Run started. It appears below within a few seconds.');
    } catch (error) {
      notify.error(errorMessage(error));
    }
  };

  const back = (
    <Box>
      <Button component={RouterLink} to="/" startIcon={<ArrowBackIcon />}>
        Schedulings
      </Button>
    </Box>
  );

  if (scheduling.isError && toApiError(scheduling.error).status === 404) {
    return (
      <Stack spacing={2}>
        {back}
        <Alert severity="warning">This scheduling does not exist. It may have been deleted.</Alert>
      </Stack>
    );
  }

  const s = scheduling.data;
  let runs;
  if (executions.isPending) {
    runs = <TableSkeleton rows={5} columns={8} />;
  } else if (!executions.data) {
    runs = (
      <Box sx={{ p: 2 }}>
        <ErrorState
          title="Could not load the history"
          message={errorMessage(executions.error)}
          onRetry={() => void executions.refetch()}
        />
      </Box>
    );
  } else if (executions.data.content.length === 0) {
    runs = (
      <EmptyState
        title={status ? `No ${EXECUTION_STATUS_LABEL[status].toLowerCase()} runs` : 'No runs yet'}
        description={status ? undefined : 'Runs appear here when the scheduling fires or when you click Run now.'}
      />
    );
  } else {
    runs = (
      <ExecutionHistoryTable
        rows={executions.data.content}
        page={page}
        size={HISTORY_PAGE_SIZE}
        total={executions.data.totalElements}
        onPageChange={setPage}
      />
    );
  }

  return (
    <Stack spacing={2}>
      {back}
      <Paper sx={{ p: 2 }}>
        {s ? (
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} justifyContent="space-between">
            <Stack spacing={0.5}>
              <Stack direction="row" spacing={1} alignItems="center">
                <Typography variant="h5" component="h1">
                  {s.name}
                </Typography>
                <SchedulingStatusChip status={s.status} />
              </Stack>
              <Typography variant="body2" color="text.secondary">
                {s.task.name} · {describeTiming(s.timing)}
              </Typography>
              <Typography variant="body2" color="text.secondary" component="div">
                Next run: <RelativeTime value={s.nextFireTime} timezone={s.timing.timezone} emptyText="none" />
              </Typography>
            </Stack>
            <Box>
              <Button
                variant="contained"
                startIcon={<PlayArrowIcon />}
                onClick={() => void runNow()}
                disabled={actions.runNow.isPending}
              >
                Run now
              </Button>
            </Box>
          </Stack>
        ) : scheduling.isError ? (
          <ErrorState message={errorMessage(scheduling.error)} onRetry={() => void scheduling.refetch()} />
        ) : (
          <Stack spacing={1}>
            <Skeleton variant="text" width="40%" height={36} />
            <Skeleton variant="text" width="60%" />
          </Stack>
        )}
      </Paper>
      <Paper>
        <Stack direction="row" spacing={2} alignItems="center" sx={{ p: 2 }}>
          <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>
            Runs
          </Typography>
          <TextField
            select
            size="small"
            label="Status"
            value={status}
            onChange={(event) => setStatus(event.target.value as ExecutionStatus | '')}
            sx={{ minWidth: 160 }}
          >
            <MenuItem value="">All statuses</MenuItem>
            {EXECUTION_STATUSES.map((value) => (
              <MenuItem key={value} value={value}>
                {EXECUTION_STATUS_LABEL[value]}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
        {runs}
      </Paper>
    </Stack>
  );
}
