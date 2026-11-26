import { CssBaseline, ThemeProvider } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { useState, type ReactNode } from 'react';
import { NotificationProvider } from './components/common/NotificationProvider';
import { theme } from './theme';
import { toApiError } from './utils/errors';
import './utils/dayjs';

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Retry only what a retry can fix: network errors and server errors, not 4xx answers.
        retry: (failureCount, error) => {
          const status = toApiError(error).status;
          return (status === 0 || status >= 500) && failureCount < 2;
        },
        staleTime: 5_000,
      },
      mutations: { retry: false },
    },
  });
}

/** Everything the pages need around them. Must be rendered inside a router. */
export function AppProviders({ children, queryClient }: { children: ReactNode; queryClient?: QueryClient }) {
  const [client] = useState(() => queryClient ?? createQueryClient());
  return (
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <LocalizationProvider dateAdapter={AdapterDayjs}>
          <NotificationProvider>{children}</NotificationProvider>
        </LocalizationProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
