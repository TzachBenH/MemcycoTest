import { Alert, Box, Button, Paper, Stack, Typography } from '@mui/material';
import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { acquireEditLock, releaseEditLock } from '../api/editLock';
import { fetchScheduling } from '../api/schedulings';
import { ConfirmDialog } from '../components/common/ConfirmDialog';
import { useNotify } from '../components/common/NotificationProvider';
import { EmptyState, ErrorState, TableSkeleton } from '../components/common/States';
import { SchedulingFormDialog } from '../components/SchedulingForm/SchedulingFormDialog';
import { ResumeBlockedDialog, type ResumeBlocked } from '../components/SchedulingTable/ResumeBlockedDialog';
import { SchedulingTable } from '../components/SchedulingTable/SchedulingTable';
import { SchedulingToolbar } from '../components/SchedulingToolbar/SchedulingToolbar';
import { useSchedulingActions, useSchedulingList } from '../hooks/useSchedulings';
import { useTasks } from '../hooks/useTasks';
import { useUrlFilters } from '../hooks/useUrlFilters';
import type { Scheduling } from '../types/api';
import { TIME_SECONDS_FORMAT, formatDateTime } from '../utils/dates';
import { errorMessage, toApiError, type ApiError } from '../utils/errors';

type EditorState = { mode: 'create' } | { mode: 'edit'; scheduling: Scheduling; lockToken: string };

export const historyPath = (id: string) => `/schedulings/${id}/history`;

function lockedMessage(name: string, error: ApiError): string {
  const since = error.lockedSince ? ` since ${formatDateTime(error.lockedSince, undefined, TIME_SECONDS_FORMAT)}` : '';
  return `“${name}” is being edited by ${error.lockedBy ?? 'someone else'}${since}. Try again when they are done.`;
}

/** Main page: the schedulings table with search, filters, paging, and all row actions. */
export function SchedulingsPage() {
  const { filters, update, reset } = useUrlFilters();
  const tasksQuery = useTasks();
  const list = useSchedulingList(filters);
  const actions = useSchedulingActions();
  const notify = useNotify();
  const navigate = useNavigate();

  const [editor, setEditor] = useState<EditorState | null>(null);
  const [toDelete, setToDelete] = useState<Scheduling | null>(null);
  const [resumeBlocked, setResumeBlocked] = useState<ResumeBlocked | null>(null);
  const [busyIds, setBusyIds] = useState<ReadonlySet<string>>(new Set());

  const tasks = tasksQuery.data ?? [];

  const withBusy = useCallback(async (id: string, work: () => Promise<void>) => {
    setBusyIds((current) => new Set(current).add(id));
    try {
      await work();
    } finally {
      setBusyIds((current) => {
        const next = new Set(current);
        next.delete(id);
        return next;
      });
    }
  }, []);

  const reportActionError = (scheduling: Scheduling, error: unknown) => {
    const apiError = toApiError(error);
    if (apiError.code === 'EDIT_LOCKED') {
      notify.warning(lockedMessage(scheduling.name, apiError));
    } else {
      notify.error(apiError.message);
    }
  };

  const handleToggleActive = (scheduling: Scheduling, active: boolean) =>
    withBusy(scheduling.id, async () => {
      try {
        if (active) {
          await actions.resume.mutateAsync(scheduling.id);
          notify.success(`“${scheduling.name}” resumed`);
        } else {
          await actions.pause.mutateAsync(scheduling.id);
          notify.success(`“${scheduling.name}” paused`);
        }
      } catch (error) {
        const apiError = toApiError(error);
        if (active && apiError.code === 'DUPLICATE_SCHEDULING') {
          setResumeBlocked({
            schedulingName: scheduling.name,
            message: apiError.message,
            conflict: apiError.conflictingScheduling,
          });
        } else {
          reportActionError(scheduling, apiError);
        }
      }
    });

  const handleRunNow = (scheduling: Scheduling) =>
    withBusy(scheduling.id, async () => {
      try {
        await actions.runNow.mutateAsync(scheduling.id);
        notify.success(`Run of “${scheduling.name}” started`, { label: 'History', to: historyPath(scheduling.id) });
      } catch (error) {
        reportActionError(scheduling, error);
      }
    });

  /** Edit = acquire the edit lock first; the form only opens when this tab holds it. */
  const handleEdit = (scheduling: Scheduling) =>
    withBusy(scheduling.id, async () => {
      let lockToken: string;
      try {
        lockToken = (await acquireEditLock(scheduling.id)).lockToken;
      } catch (error) {
        reportActionError(scheduling, error);
        void list.refetch();
        return;
      }
      try {
        const fresh = await fetchScheduling(scheduling.id);
        setEditor({ mode: 'edit', scheduling: fresh, lockToken });
      } catch (error) {
        releaseEditLock(scheduling.id, lockToken).catch(() => undefined);
        notify.error(errorMessage(error));
      }
    });

  const confirmDelete = async () => {
    if (!toDelete) {
      return;
    }
    const scheduling = toDelete;
    try {
      await actions.remove.mutateAsync(scheduling.id);
      notify.success(`“${scheduling.name}” deleted`);
    } catch (error) {
      reportActionError(scheduling, error);
    } finally {
      setToDelete(null);
    }
  };

  const handleSaved = (saved: Scheduling, mode: 'create' | 'edit') => {
    setEditor(null);
    const done = mode === 'create' ? `“${saved.name}” created.` : `“${saved.name}” saved.`;
    if (saved.warnings.length > 0) {
      notify.info(`${done} ${saved.warnings.map((w) => w.message).join(' ')}`);
    } else {
      notify.success(done);
    }
  };

  const hasFilters = !!(filters.search || filters.taskKey || filters.status);
  const openCreate = () => setEditor({ mode: 'create' });

  let content;
  if (list.isPending) {
    content = <TableSkeleton rows={5} columns={8} />;
  } else if (!list.data) {
    content = (
      <Box sx={{ p: 2 }}>
        <ErrorState
          title="Could not load schedulings"
          message={errorMessage(list.error)}
          onRetry={() => void list.refetch()}
        />
      </Box>
    );
  } else if (list.data.content.length === 0) {
    if (list.data.totalElements > 0) {
      content = (
        <EmptyState
          title="This page is empty"
          action={<Button onClick={() => update({ page: 0 })}>Go to the first page</Button>}
        />
      );
    } else if (hasFilters) {
      content = (
        <EmptyState
          title="No schedulings match the filters"
          action={<Button onClick={reset}>Clear filters</Button>}
        />
      );
    } else {
      content = (
        <EmptyState
          title="No schedulings yet"
          description="Create one to run a task once, every few minutes or hours, weekly, or on a cron schedule."
          action={
            <Button variant="contained" onClick={openCreate} disabled={!tasksQuery.data}>
              New scheduling
            </Button>
          }
        />
      );
    }
  } else {
    content = (
      <SchedulingTable
        rows={list.data.content}
        sort={filters.sort}
        page={filters.page}
        size={filters.size}
        total={list.data.totalElements}
        busyIds={busyIds}
        onSortChange={(sort) => update({ sort })}
        onPageChange={(page) => update({ page })}
        onSizeChange={(size) => update({ size })}
        onToggleActive={handleToggleActive}
        onRunNow={handleRunNow}
        onHistory={(s) => navigate(historyPath(s.id))}
        onEdit={handleEdit}
        onDelete={setToDelete}
      />
    );
  }

  return (
    <Stack spacing={2}>
      <Typography variant="h5" component="h1">
        Schedulings
      </Typography>
      {tasksQuery.isError && (
        <ErrorState
          title="Could not load the task list"
          message={errorMessage(tasksQuery.error)}
          onRetry={() => void tasksQuery.refetch()}
        />
      )}
      <Paper sx={{ p: 2 }}>
        <SchedulingToolbar
          filters={filters}
          tasks={tasks}
          onChange={update}
          onCreate={openCreate}
          createDisabled={!tasksQuery.data}
        />
      </Paper>
      {list.isError && list.data && (
        <Alert severity="warning">Could not refresh the list ({errorMessage(list.error)}). Showing the last loaded data.</Alert>
      )}
      <Paper>{content}</Paper>

      {editor && (
        <SchedulingFormDialog
          tasks={tasks}
          scheduling={editor.mode === 'edit' ? editor.scheduling : undefined}
          lockToken={editor.mode === 'edit' ? editor.lockToken : undefined}
          onClose={() => setEditor(null)}
          onSaved={handleSaved}
        />
      )}
      <ConfirmDialog
        open={toDelete !== null}
        title="Delete scheduling?"
        message={
          <>
            “{toDelete?.name}” and its execution history will be deleted. This cannot be undone.
          </>
        }
        confirmLabel="Delete"
        destructive
        busy={actions.remove.isPending}
        onConfirm={() => void confirmDelete()}
        onCancel={() => setToDelete(null)}
      />
      <ResumeBlockedDialog
        blocked={resumeBlocked}
        onClose={() => setResumeBlocked(null)}
        onGoTo={(conflict) => {
          setResumeBlocked(null);
          navigate(`/?search=${encodeURIComponent(conflict.name)}`);
        }}
      />
    </Stack>
  );
}
