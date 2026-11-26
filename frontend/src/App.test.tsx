import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderApp } from './test/render';

describe('App routes', () => {
  it('shows the schedulings page at /', async () => {
    renderApp('/');
    expect(screen.getByRole('heading', { name: 'Schedulings' })).toBeInTheDocument();
    expect(await screen.findByText('Default row')).toBeInTheDocument();
  });

  it('shows the history page of a scheduling', async () => {
    renderApp('/schedulings/abc/history');
    expect(await screen.findByRole('heading', { name: /Scheduling \d+/ })).toBeInTheDocument();
    expect(await screen.findByText('backend-1')).toBeInTheDocument();
  });

  it('shows a not-found page for unknown URLs', () => {
    renderApp('/nope');
    expect(screen.getByText('Page not found')).toBeInTheDocument();
  });
});
