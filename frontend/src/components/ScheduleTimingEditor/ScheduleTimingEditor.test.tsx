import { screen, within } from '@testing-library/react';
import { useForm, type Resolver } from 'react-hook-form';
import { describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '../../test/render';
import type { Timing } from '../../types/api';
import { emptySchedulingForm, type SchedulingFormValues } from '../../utils/schedulingForm';
import { formToTiming, validateTiming } from '../../utils/timingForm';
import { toFieldErrors } from '../SchedulingForm/SchedulingFormDialog';
import { ScheduleTimingEditor } from './ScheduleTimingEditor';

function Harness({ onSubmit }: { onSubmit: (timing: Timing) => void }) {
  const resolver: Resolver<SchedulingFormValues> = async (values) => {
    const errors = validateTiming(values.timing);
    return Object.keys(errors).length ? { values: {}, errors: toFieldErrors(errors) } : { values, errors: {} };
  };
  const form = useForm<SchedulingFormValues>({ defaultValues: emptySchedulingForm('UTC'), resolver });
  return (
    <form noValidate onSubmit={form.handleSubmit((values) => onSubmit(formToTiming(values.timing)))}>
      <ScheduleTimingEditor control={form.control} />
      <button type="submit">Submit</button>
    </form>
  );
}

function setup() {
  const onSubmit = vi.fn<(timing: Timing) => void>();
  const result = renderWithProviders(<Harness onSubmit={onSubmit} />);
  return { ...result, onSubmit, submit: () => result.user.click(screen.getByRole('button', { name: 'Submit' })) };
}

describe('ScheduleTimingEditor', () => {
  it('starts on "Repeat" every 15 minutes, starting now, in the browser time zone', async () => {
    const { onSubmit, submit } = setup();
    expect(screen.getByRole('tab', { name: 'Repeat' })).toHaveAttribute('aria-selected', 'true');
    await submit();
    expect(onSubmit).toHaveBeenCalledWith({ type: 'INTERVAL', every: 15, unit: 'MINUTES', startAt: null, timezone: 'UTC' });
  });

  it('edits the interval and validates it', async () => {
    const { user, onSubmit, submit } = setup();
    const every = screen.getByRole('spinbutton', { name: /every/i });
    await user.clear(every);
    await user.type(every, '0');
    await submit();
    expect(await screen.findByText('Must be between 1 and 10000')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();

    await user.clear(every);
    await user.type(every, '2');
    await user.click(screen.getByRole('combobox', { name: /unit/i }));
    await user.click(within(screen.getByRole('listbox')).getByRole('option', { name: 'Hours' }));
    await submit();
    expect(onSubmit).toHaveBeenCalledWith({ type: 'INTERVAL', every: 2, unit: 'HOURS', startAt: null, timezone: 'UTC' });
  });

  it('runs once at the chosen time (defaults to the next full hour)', async () => {
    const { user, onSubmit, submit } = setup();
    await user.click(screen.getByRole('tab', { name: 'One time' }));
    expect(screen.getByRole('textbox', { name: /run at/i })).toBeInTheDocument();
    await submit();
    const timing = onSubmit.mock.calls[0][0];
    expect(timing.type).toBe('ONE_TIME');
    const runAt = timing.type === 'ONE_TIME' ? Date.parse(timing.runAt) : 0;
    expect(runAt).toBeGreaterThan(Date.now());
    expect(runAt - Date.now()).toBeLessThanOrEqual(60 * 60 * 1000);
  });

  it('runs weekly on the selected days', async () => {
    const { user, onSubmit, submit } = setup();
    await user.click(screen.getByRole('tab', { name: 'Weekly' }));
    await submit();
    expect(await screen.findByText('Select at least one day')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Wednesday' }));
    await user.click(screen.getByRole('button', { name: 'Monday' }));
    expect(screen.getByRole('button', { name: 'Monday' })).toHaveAttribute('aria-pressed', 'true');
    await submit();
    expect(onSubmit).toHaveBeenCalledWith({ type: 'WEEKLY', days: ['MON', 'WED'], time: '09:00', timezone: 'UTC' });
  });

  it('describes cron expressions while typing and offers examples', async () => {
    const { user, onSubmit, submit } = setup();
    await user.click(screen.getByRole('tab', { name: 'Cron' }));
    const input = screen.getByRole('textbox', { name: /cron expression/i });
    await user.type(input, '0 0 9 ? * MON');
    expect(screen.getByText('“At 09:00, only on Monday”')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Fridays at 18:30' }));
    expect(input).toHaveValue('0 30 18 ? * FRI');
    await submit();
    expect(onSubmit).toHaveBeenCalledWith({ type: 'CRON', expression: '0 30 18 ? * FRI', timezone: 'UTC' });
  });

  it('rejects cron expressions without seconds', async () => {
    const { user, submit } = setup();
    await user.click(screen.getByRole('tab', { name: 'Cron' }));
    await user.type(screen.getByRole('textbox', { name: /cron expression/i }), '0 9 * * MON');
    await submit();
    expect(await screen.findByText(/Must be a Quartz cron expression with 6 or 7 fields/)).toBeInTheDocument();
  });

  it('lets the user pick another time zone', async () => {
    const { user, onSubmit, submit } = setup();
    const zone = screen.getByRole('combobox', { name: /timezone/i });
    await user.clear(zone);
    await user.type(zone, 'Asia/Jerus');
    await user.click(await screen.findByRole('option', { name: 'Asia/Jerusalem' }));
    await submit();
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ timezone: 'Asia/Jerusalem' }));
  });
});
