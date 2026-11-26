import ScheduleIcon from '@mui/icons-material/Schedule';
import { AppBar, Box, Container, Toolbar, Typography } from '@mui/material';
import type { ReactNode } from 'react';
import { BrowserRouter, Link as RouterLink, Route, Routes } from 'react-router-dom';
import { AppProviders } from './AppProviders';
import { ExecutionHistoryPage } from './pages/ExecutionHistoryPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { SchedulingsPage } from './pages/SchedulingsPage';

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<SchedulingsPage />} />
      <Route path="/schedulings/:id/history" element={<ExecutionHistoryPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export function AppLayout({ children }: { children: ReactNode }) {
  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="sticky" elevation={0}>
        <Toolbar>
          <ScheduleIcon sx={{ mr: 1 }} />
          <Typography variant="h6" component={RouterLink} to="/" sx={{ color: 'inherit', textDecoration: 'none' }}>
            Scheduler
          </Typography>
        </Toolbar>
      </AppBar>
      <Container component="main" maxWidth="xl" sx={{ py: 3 }}>
        {children}
      </Container>
    </Box>
  );
}

export function App() {
  return (
    <BrowserRouter>
      <AppProviders>
        <AppLayout>
          <AppRoutes />
        </AppLayout>
      </AppProviders>
    </BrowserRouter>
  );
}
