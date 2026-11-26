import {
  Alert,
  AlertTitle,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useCallback, useRef, useState } from 'react';
import { Controller, useForm, useWatch, type FieldErrors, type FieldPath, type Resolver } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { queryKeys } from '../../api/queryKeys';
import { createScheduling, fetchScheduling, updateScheduling } from '../../api/schedulings';
import { useEditLock } from '../../hooks/useEditLock';
import type { Scheduling, SchedulingRequest, Task } from '../../types/api';
import { browserTimezone } from '../../utils/dates';
import { capitalize, describeFieldError, errorMessage, toApiError, type ApiError } from '../../utils/errors';
import { defaultParamValues } from '../../utils/params';
import {
  emptySchedulingForm,
  MAX_NAME_LENGTH,
  schedulingToForm,
  toSchedulingRequest,
  validateSchedulingForm,
  visibleFieldPaths,
  type SchedulingFormValues,
} from '../../utils/schedulingForm';
import { ScheduleTimingEditor } from '../ScheduleTimingEditor/ScheduleTimingEditor';
import { TaskParamsFields } from '../TaskParamsFields/TaskParamsFields';

export interface SchedulingFormDialogProps {
  tasks: Task[];
  /** Edit mode: the scheduling as just loaded … */
  scheduling?: Scheduling;
  /** … and the token of the edit lock acquired for it. */
  lockToken?: string;
  onClose: () => void;
  onSaved: (saved: Scheduling, mode: 'create' | 'edit') => void;
}

type Banner =
  | { kind: 'duplicate'; message: string; conflict: { id: string; name: string } | null }
  | { kind: 'concurrent'; message: string }
  | { kind: 'error'; message: string; details: string[] };

interface ValidationContext {
  task: Task | undefined;
  originalRunAt: string | null;
}

/** Flat "a.b" → message map to the nested shape react-hook-form expects. */
export function toFieldErrors(flat: Record<string, string>): FieldErrors<SchedulingFormValues> {
  const result: Record<string, unknown> = {};
  Object.entries(flat).forEach(([path, message]) => {
    const parts = path.split('.');
    let node = result;
    parts.forEach((part, index) => {
      if (index === parts.length - 1) {
        node[part] = { type: 'validate', message };
      } else {
        node[part] = (node[part] as Record<string, unknown> | undefined) ?? {};
        node = node[part] as Record<string, unknown>;
      }
    });
  });
  return result as FieldErrors<SchedulingFormValues>;
}

/** Create / edit form. In edit mode the edit lock is renewed while open and released on close. */
export function SchedulingFormDialog({ tasks, scheduling, lockToken, onClose, onSaved }: SchedulingFormDialogProps) {
  const isEdit = scheduling !== undefined;
  const [loaded, setLoaded] = useState<Scheduling | undefined>(scheduling);
  const [banner, setBanner] = useState<Banner | null>(null);
  const [reloading, setReloading] = useState(false);
  const lock = useEditLock(isEdit ? (loaded?.id ?? null) : null, isEdit ? (lockToken ?? null) : null);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const findTask = useCallback((key: string) => tasks.find((t) => t.key === key), [tasks]);

  const validation = useRef<ValidationContext>({ task: undefined, originalRunAt: null });
  const resolver = useCallback<Resolver<SchedulingFormValues>>(async (values) => {
    const errors = validateSchedulingForm(values, validation.current.task, {
      originalRunAt: validation.current.originalRunAt,
    });
    return Object.keys(errors).length === 0 ? { values, errors: {} } : { values: {}, errors: toFieldErrors(errors) };
  }, []);

  const [initialValues] = useState<SchedulingFormValues>(() =>
    scheduling ? schedulingToForm(scheduling, findTask(scheduling.task.key)) : emptySchedulingForm(browserTimezone()),
  );
  const form = useForm<SchedulingFormValues>({ defaultValues: initialValues, resolver });
  const taskKey = useWatch({ control: form.control, name: 'taskKey' });
  const task = findTask(taskKey);
  validation.current = {
    task,
    originalRunAt: loaded?.timing.type === 'ONE_TIME' ? loaded.timing.runAt : null,
  };

  const handleServerError = (error: ApiError) => {
    switch (error.code) {
      case 'VALIDATION_FAILED': {
        const visible = visibleFieldPaths(form.getValues(), task);
        const details: string[] = [];
        Object.entries(error.fieldErrors).forEach(([path, message]) => {
          if (visible.has(path)) {
            form.setError(path as FieldPath<SchedulingFormValues>, { type: 'server', message });
          } else {
            details.push(describeFieldError(path, message));
          }
        });
        setBanner({
          kind: 'error',
          message: details.length > 0 ? 'Please fix the following:' : 'Please fix the highlighted fields.',
          details,
        });
        break;
      }
      case 'DUPLICATE_SCHEDULING':
        setBanner({ kind: 'duplicate', message: error.message, conflict: error.conflictingScheduling });
        break;
      case 'CONCURRENT_MODIFICATION':
        setBanner({ kind: 'concurrent', message: error.message });
        break;
      case 'EDIT_LOCK_LOST':
        lock.markLost();
        setBanner(null);
        break;
      case 'NOT_FOUND':
        setBanner({ kind: 'error', message: 'This scheduling no longer exists (it may have been deleted).', details: [] });
        break;
      default:
        setBanner({ kind: 'error', message: error.message, details: [] });
    }
  };

  const save = useMutation({
    mutationFn: (request: SchedulingRequest) =>
      isEdit && loaded && lockToken ? updateScheduling(loaded.id, request, lockToken) : createScheduling(request),
    onSuccess: (saved) => {
      if (isEdit) {
        lock.markReleased(); // the backend releases the lock when the save succeeds
      }
      void queryClient.invalidateQueries({ queryKey: queryKeys.schedulings });
      onSaved(saved, isEdit ? 'edit' : 'create');
    },
    onError: (error) => handleServerError(toApiError(error)),
  });

  const onSubmit = form.handleSubmit((values) => {
    const selected = findTask(values.taskKey);
    if (!selected) {
      return;
    }
    setBanner(null);
    save.mutate(toSchedulingRequest(values, selected, isEdit ? loaded?.version : undefined));
  });

  const reload = async () => {
    if (!loaded) {
      return;
    }
    setReloading(true);
    try {
      const fresh = await fetchScheduling(loaded.id);
      setLoaded(fresh);
      form.reset(schedulingToForm(fresh, findTask(fresh.task.key)));
      setBanner(null);
    } catch (error) {
      setBanner({ kind: 'error', message: errorMessage(error), details: [] });
    } finally {
      setReloading(false);
    }
  };

  const showConflict = (name: string) => {
    onClose();
    navigate(`/?search=${encodeURIComponent(name)}`);
  };

  const busy = save.isPending || reloading;
  const unknownTaskKey = loaded && !loaded.task.known ? loaded.task.key : null;

  return (
    <Dialog
      open
      onClose={busy ? undefined : onClose}
      fullWidth
      maxWidth="md"
      aria-labelledby="scheduling-form-title"
    >
      <Box
        component="form"
        noValidate
        onSubmit={onSubmit}
        sx={{ display: 'flex', flexDirection: 'column', minHeight: 0, flex: '1 1 auto' }}
      >
        <DialogTitle id="scheduling-form-title">{isEdit ? 'Edit scheduling' : 'New scheduling'}</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.5}>
            {lock.lost && (
              <Alert severity="error">
                <AlertTitle>Edit lock lost</AlertTitle>
                Your edit lock expired or another editor took over, so these changes can&apos;t be saved. Close this
                dialog and open the scheduling again.
              </Alert>
            )}
            {banner?.kind === 'duplicate' && (
              <Alert
                severity="warning"
                action={
                  banner.conflict ? (
                    <Button color="inherit" size="small" onClick={() => showConflict(banner.conflict!.name)}>
                      Show it
                    </Button>
                  ) : undefined
                }
              >
                <AlertTitle>Duplicate scheduling</AlertTitle>
                {banner.message}
              </Alert>
            )}
            {banner?.kind === 'concurrent' && (
              <Alert
                severity="warning"
                action={
                  <Button color="inherit" size="small" onClick={reload} disabled={reloading}>
                    Reload
                  </Button>
                }
              >
                <AlertTitle>Changed by someone else</AlertTitle>
                {banner.message}
              </Alert>
            )}
            {banner?.kind === 'error' && (
              <Alert severity="error">
                <AlertTitle>Could not save</AlertTitle>
                {banner.message}
                {banner.details.length > 0 && (
                  <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
                    {banner.details.map((detail) => (
                      <li key={detail}>{detail}</li>
                    ))}
                  </Box>
                )}
              </Alert>
            )}
            {loaded?.warnings.map((warning) => (
              <Alert severity="info" key={warning.code + (warning.schedulingId ?? '')}>
                {warning.message}
              </Alert>
            ))}

            <Controller
              name="name"
              control={form.control}
              render={({ field, fieldState }) => (
                <TextField
                  label="Name"
                  required
                  autoFocus={!isEdit}
                  name={field.name}
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  inputRef={field.ref}
                  error={!!fieldState.error}
                  helperText={fieldState.error ? capitalize(fieldState.error.message ?? '') : undefined}
                  slotProps={{ htmlInput: { maxLength: MAX_NAME_LENGTH } }}
                  fullWidth
                />
              )}
            />

            <Controller
              name="taskKey"
              control={form.control}
              render={({ field, fieldState }) => (
                <TextField
                  select
                  label="Task"
                  required
                  name={field.name}
                  value={field.value}
                  onChange={(event) => {
                    const key = event.target.value;
                    field.onChange(key);
                    form.setValue('parameters', defaultParamValues(findTask(key)));
                    form.clearErrors('parameters');
                  }}
                  onBlur={field.onBlur}
                  error={!!fieldState.error}
                  helperText={fieldState.error ? capitalize(fieldState.error.message ?? '') : task?.description}
                  fullWidth
                >
                  {tasks.map((t) => (
                    <MenuItem key={t.key} value={t.key}>
                      {t.name}
                    </MenuItem>
                  ))}
                  {unknownTaskKey && (
                    <MenuItem value={unknownTaskKey} disabled>
                      {unknownTaskKey} (unknown task)
                    </MenuItem>
                  )}
                </TextField>
              )}
            />

            <Divider textAlign="left">
              <Typography variant="overline">Parameters</Typography>
            </Divider>
            <TaskParamsFields task={task} control={form.control} disabled={busy} />

            <Divider textAlign="left">
              <Typography variant="overline">When</Typography>
            </Divider>
            <ScheduleTimingEditor control={form.control} disabled={busy} />

            <Controller
              name="enabled"
              control={form.control}
              render={({ field }) => (
                <FormControlLabel
                  control={
                    <Switch checked={field.value} onChange={(event) => field.onChange(event.target.checked)} />
                  }
                  label="Active"
                />
              )}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={busy || lock.lost}>
            {save.isPending ? 'Saving…' : isEdit ? 'Save' : 'Create'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
