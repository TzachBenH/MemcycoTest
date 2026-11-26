import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import HistoryIcon from '@mui/icons-material/History';
import LockIcon from '@mui/icons-material/Lock';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import {
  Chip,
  IconButton,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TableSortLabel,
  Tooltip,
  Typography,
} from '@mui/material';
import type { ReactNode } from 'react';
import { PAGE_SIZES } from '../../hooks/useUrlFilters';
import type { Scheduling } from '../../types/api';
import { TIME_SECONDS_FORMAT, formatDateTime } from '../../utils/dates';
import { formatParameters } from '../../utils/params';
import { describeTiming } from '../../utils/timingText';
import { RelativeTime } from '../common/RelativeTime';
import { SchedulingStatusChip } from '../common/StatusChips';
import { LastRunChip } from './LastRunChip';

export interface SchedulingTableProps {
  rows: Scheduling[];
  sort: string;
  page: number;
  size: number;
  total: number;
  /** Ids with a pause/resume/delete/run request in flight. */
  busyIds?: ReadonlySet<string>;
  onSortChange: (sort: string) => void;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
  onToggleActive: (scheduling: Scheduling, active: boolean) => void;
  onRunNow: (scheduling: Scheduling) => void;
  onHistory: (scheduling: Scheduling) => void;
  onEdit: (scheduling: Scheduling) => void;
  onDelete: (scheduling: Scheduling) => void;
}

type SortField = 'name' | 'taskKey' | 'enabled';

function SortableHeader({ field, label, sort, onSortChange }: {
  field: SortField;
  label: string;
  sort: string;
  onSortChange: (sort: string) => void;
}) {
  const [currentField, currentDirection] = sort.split(',');
  const active = currentField === field;
  const direction = active && currentDirection === 'desc' ? 'desc' : 'asc';
  return (
    <TableCell sortDirection={active ? direction : false}>
      <TableSortLabel
        active={active}
        direction={direction}
        onClick={() => onSortChange(`${field},${active && direction === 'asc' ? 'desc' : 'asc'}`)}
      >
        {label}
      </TableSortLabel>
    </TableCell>
  );
}

/** Disabled buttons do not fire mouse events, so their tooltip needs a wrapper. */
function ActionButton({ label, title, disabled, onClick, children }: {
  label: string;
  title: string;
  disabled?: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <Tooltip title={title}>
      <span>
        <IconButton aria-label={label} size="small" disabled={disabled} onClick={onClick}>
          {children}
        </IconButton>
      </span>
    </Tooltip>
  );
}

export function SchedulingTable({
  rows,
  sort,
  page,
  size,
  total,
  busyIds,
  onSortChange,
  onPageChange,
  onSizeChange,
  onToggleActive,
  onRunNow,
  onHistory,
  onEdit,
  onDelete,
}: SchedulingTableProps) {
  return (
    <>
      <TableContainer>
        <Table size="small" aria-label="Schedulings">
          <TableHead>
            <TableRow>
              <SortableHeader field="name" label="Name" sort={sort} onSortChange={onSortChange} />
              <SortableHeader field="taskKey" label="Task" sort={sort} onSortChange={onSortChange} />
              <TableCell>Timing</TableCell>
              <TableCell>Parameters</TableCell>
              <TableCell>Next run</TableCell>
              <TableCell>Last run</TableCell>
              <SortableHeader field="enabled" label="Active" sort={sort} onSortChange={onSortChange} />
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((s) => {
              const lockedByOther = !!s.editLock && !s.editLock.mine;
              const busy = busyIds?.has(s.id) ?? false;
              const lockText = s.editLock
                ? `Being edited by ${s.editLock.lockedBy} since ${formatDateTime(s.editLock.since, undefined, TIME_SECONDS_FORMAT)}`
                : '';
              const blocked = lockedByOther ? `${lockText} — try again when they are done` : '';
              return (
                <TableRow key={s.id} hover>
                  <TableCell sx={{ minWidth: 160 }}>
                    <Stack direction="row" spacing={0.75} alignItems="center">
                      <Typography variant="body2" fontWeight={500}>
                        {s.name}
                      </Typography>
                      {lockedByOther && (
                        <Tooltip title={lockText}>
                          <LockIcon fontSize="small" color="warning" titleAccess={lockText} />
                        </Tooltip>
                      )}
                    </Stack>
                  </TableCell>
                  <TableCell sx={{ minWidth: 140 }}>
                    <Stack spacing={0.25} alignItems="flex-start">
                      <Typography variant="body2">{s.task.name}</Typography>
                      {s.task.known ? (
                        <Typography variant="caption" color="text.secondary">
                          {s.task.key}
                        </Typography>
                      ) : (
                        <Tooltip title="This task no longer exists in the backend: its runs fail until the scheduling is changed.">
                          <Chip size="small" color="warning" icon={<WarningAmberIcon />} label="Unknown task" />
                        </Tooltip>
                      )}
                    </Stack>
                  </TableCell>
                  <TableCell sx={{ minWidth: 200 }}>
                    <Typography variant="body2">{describeTiming(s.timing)}</Typography>
                    {s.timing.type === 'CRON' && (
                      <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                        {s.timing.expression}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell sx={{ maxWidth: 260 }}>
                    <Tooltip title={<pre style={{ margin: 0 }}>{JSON.stringify(s.parameters, null, 2)}</pre>}>
                      <Typography variant="body2" noWrap>
                        {formatParameters(s.parameters)}
                      </Typography>
                    </Tooltip>
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    <RelativeTime value={s.nextFireTime} timezone={s.timing.timezone} />
                  </TableCell>
                  <TableCell>
                    <LastRunChip execution={s.lastExecution} />
                  </TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={0.5} alignItems="center">
                      <Tooltip title={blocked || (s.enabled ? 'Pause' : 'Resume')}>
                        <span>
                          <Switch
                            size="small"
                            checked={s.enabled}
                            disabled={lockedByOther || busy}
                            onChange={(event) => onToggleActive(s, event.target.checked)}
                            slotProps={{ input: { 'aria-label': `Active: ${s.name}` } }}
                          />
                        </span>
                      </Tooltip>
                      {s.status !== 'ACTIVE' && <SchedulingStatusChip status={s.status} />}
                    </Stack>
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <ActionButton label="Run now" title="Run now" disabled={busy} onClick={() => onRunNow(s)}>
                      <PlayArrowIcon fontSize="small" />
                    </ActionButton>
                    <ActionButton label="History" title="Execution history" onClick={() => onHistory(s)}>
                      <HistoryIcon fontSize="small" />
                    </ActionButton>
                    <ActionButton label="Edit" title={blocked || 'Edit'} disabled={lockedByOther || busy} onClick={() => onEdit(s)}>
                      <EditIcon fontSize="small" />
                    </ActionButton>
                    <ActionButton
                      label="Delete"
                      title={blocked || 'Delete'}
                      disabled={lockedByOther || busy}
                      onClick={() => onDelete(s)}
                    >
                      <DeleteIcon fontSize="small" />
                    </ActionButton>
                  </TableCell>
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
        rowsPerPageOptions={[...PAGE_SIZES]}
        onPageChange={(_, next) => onPageChange(next)}
        onRowsPerPageChange={(event) => onSizeChange(Number(event.target.value))}
      />
    </>
  );
}
