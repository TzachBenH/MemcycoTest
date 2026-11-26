import { screen, within } from '@testing-library/react';
import { useForm, type Resolver } from 'react-hook-form';
import { describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/render';
import { tasksFixture } from '../../test/fixtures';
import type { Task } from '../../types/api';
import { defaultParamValues, validateParams } from '../../utils/params';
import { emptySchedulingForm, type SchedulingFormValues } from '../../utils/schedulingForm';
import { toFieldErrors } from '../SchedulingForm/SchedulingFormDialog';
import { TaskParamsFields, parameterHint } from './TaskParamsFields';

const [logTask, emailTask, dbTask] = tasksFixture;

const everyTypeTask: Task = {
  key: 'ALL_TYPES',
  name: 'All types',
  description: '',
  parameters: [
    { name: 'title', label: 'Title', type: 'STRING', required: true },
    { name: 'notes', label: 'Notes', type: 'TEXT', required: false },
    { name: 'count', label: 'Count', type: 'NUMBER', required: false, min: 1, max: 5 },
    { name: 'dryRun', label: 'Dry run', type: 'BOOLEAN', required: false, defaultValue: false },
    { name: 'mode', label: 'Mode', type: 'ENUM', required: false, allowedValues: ['FAST', 'SAFE'] },
    { name: 'owner', label: 'Owner', type: 'EMAIL', required: false },
  ],
};

function Harness({ task, onSubmit }: { task: Task | undefined; onSubmit: (values: SchedulingFormValues) => void }) {
  const resolver: Resolver<SchedulingFormValues> = async (values) => {
    const errors = task ? validateParams(task, values.parameters) : {};
    return Object.keys(errors).length ? { values: {}, errors: toFieldErrors(errors) } : { values, errors: {} };
  };
  const form = useForm<SchedulingFormValues>({
    defaultValues: { ...emptySchedulingForm('UTC'), taskKey: task?.key ?? '', parameters: defaultParamValues(task) },
    resolver,
  });
  return (
    <form noValidate onSubmit={form.handleSubmit(onSubmit)}>
      <TaskParamsFields task={task} control={form.control} />
      <button type="submit">Submit</button>
    </form>
  );
}

describe('TaskParamsFields', () => {
  it('asks for a task first', () => {
    renderWithProviders(<Harness task={undefined} onSubmit={vi.fn()} />);
    expect(screen.getByText('Choose a task to see its parameters.')).toBeInTheDocument();
  });

  it('says when a task has no parameters', () => {
    renderWithProviders(<Harness task={{ ...logTask, parameters: [] }} onSubmit={vi.fn()} />);
    expect(screen.getByText('This task has no parameters.')).toBeInTheDocument();
  });

  it('renders one field per schema parameter with defaults and hints', () => {
    renderWithProviders(<Harness task={dbTask} onSubmit={vi.fn()} />);
    expect(screen.getByRole('combobox', { name: /report/i })).toBeInTheDocument();
    const hours = screen.getByRole('spinbutton', { name: /last hours/i });
    expect(hours).toHaveValue(24);
    expect(screen.getByText(/Range 1–720\. Default: 24\./)).toBeInTheDocument();
  });

  it('renders an input for every parameter type and submits the edited values', async () => {
    const onSubmit = vi.fn();
    const { user } = renderWithProviders(<Harness task={everyTypeTask} onSubmit={onSubmit} />);

    await user.type(screen.getByRole('textbox', { name: /title/i }), 'Report');
    await user.type(screen.getByRole('textbox', { name: /notes/i }), 'line 1{enter}line 2');
    await user.type(screen.getByRole('spinbutton', { name: /count/i }), '3');
    await user.click(screen.getByLabelText('Dry run'));
    await user.click(screen.getByRole('combobox', { name: /mode/i }));
    await user.click(within(screen.getByRole('listbox')).getByRole('option', { name: 'SAFE' }));
    await user.type(screen.getByRole('textbox', { name: /owner/i }), 'ops@example.com');
    await user.click(screen.getByRole('button', { name: 'Submit' }));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit.mock.calls[0][0].parameters).toEqual({
      title: 'Report',
      notes: 'line 1\nline 2',
      count: '3',
      dryRun: true,
      mode: 'SAFE',
      owner: 'ops@example.com',
    });
  });

  it('shows validation messages under the fields', async () => {
    const onSubmit = vi.fn();
    const { user } = renderWithProviders(<Harness task={emailTask} onSubmit={onSubmit} />);
    await user.type(screen.getByRole('textbox', { name: /to/i }), 'not-an-email');
    await user.click(screen.getByRole('button', { name: 'Submit' }));

    expect(await screen.findByText('Must be a valid email address')).toBeInTheDocument();
    expect(screen.getByText('Is required')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('builds hints from the schema', () => {
    expect(parameterHint({ name: 'x', label: 'X', type: 'STRING', required: false })).toBe('Optional.');
    expect(parameterHint(logTask.parameters[1])).toBe('Log level of the message. Default: INFO.');
  });
});
