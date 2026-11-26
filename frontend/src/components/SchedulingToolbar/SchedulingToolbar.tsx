import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import { Box, Button, InputAdornment, MenuItem, Stack, TextField } from '@mui/material';
import { useEffect, useState } from 'react';
import type { SchedulingFilters } from '../../hooks/useUrlFilters';
import type { SchedulingStatus, Task } from '../../types/api';
import { SCHEDULING_STATUSES, SCHEDULING_STATUS_LABEL } from '../../utils/status';

export const SEARCH_DEBOUNCE_MS = 300;

interface SchedulingToolbarProps {
  filters: SchedulingFilters;
  tasks: Task[];
  onChange: (changes: Partial<SchedulingFilters>) => void;
  onCreate: () => void;
  createDisabled?: boolean;
}

export function SchedulingToolbar({ filters, tasks, onChange, onCreate, createDisabled }: SchedulingToolbarProps) {
  // The search box updates the URL (and the request) once typing pauses.
  const [search, setSearch] = useState(filters.search);
  useEffect(() => setSearch(filters.search), [filters.search]);
  useEffect(() => {
    if (search === filters.search) {
      return undefined;
    }
    const timer = window.setTimeout(() => onChange({ search }), SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [search, filters.search, onChange]);

  return (
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ md: 'center' }}>
      <TextField
        label="Search by name"
        size="small"
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        slotProps={{
          input: {
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          },
        }}
        sx={{ minWidth: { md: 260 } }}
      />
      <TextField
        select
        label="Task"
        size="small"
        value={filters.taskKey}
        onChange={(event) => onChange({ taskKey: event.target.value })}
        sx={{ minWidth: { md: 200 } }}
      >
        <MenuItem value="">All tasks</MenuItem>
        {tasks.map((task) => (
          <MenuItem key={task.key} value={task.key}>
            {task.name}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        select
        label="Status"
        size="small"
        value={filters.status}
        onChange={(event) => onChange({ status: event.target.value as SchedulingStatus | '' })}
        sx={{ minWidth: { md: 160 } }}
      >
        <MenuItem value="">All statuses</MenuItem>
        {SCHEDULING_STATUSES.map((status) => (
          <MenuItem key={status} value={status}>
            {SCHEDULING_STATUS_LABEL[status]}
          </MenuItem>
        ))}
      </TextField>
      <Box sx={{ flexGrow: 1 }} />
      <Button variant="contained" startIcon={<AddIcon />} onClick={onCreate} disabled={createDisabled}>
        New scheduling
      </Button>
    </Stack>
  );
}
