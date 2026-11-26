import { QueryClient } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement, ReactNode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AppRoutes } from '../App';
import { AppProviders } from '../AppProviders';

export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: Infinity, refetchInterval: false, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  });
}

/** Shows the current URL so tests can assert on navigation and query-string state. */
function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname + location.search}</output>;
}

export function currentLocation(): string {
  return screen.getByTestId('location').textContent ?? '';
}

interface Options {
  route?: string;
  /** Route pattern the element is mounted on (for useParams). */
  path?: string;
}

export function renderWithProviders(ui: ReactElement, { route = '/', path = '*' }: Options = {}) {
  const queryClient = createTestQueryClient();
  const user = userEvent.setup();
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[route]}>
      <AppProviders queryClient={queryClient}>
        <Routes>
          <Route path={path} element={children} />
        </Routes>
        <LocationProbe />
      </AppProviders>
    </MemoryRouter>
  );
  return { ...render(ui, { wrapper }), user, queryClient };
}

/** The whole app (all routes) at a given URL. */
export function renderApp(route = '/') {
  const queryClient = createTestQueryClient();
  const user = userEvent.setup();
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[route]}>
      <AppProviders queryClient={queryClient}>
        {children}
        <LocationProbe />
      </AppProviders>
    </MemoryRouter>
  );
  return { ...render(<AppRoutes />, { wrapper }), user, queryClient };
}
