import RefreshIcon from '@mui/icons-material/Refresh';
import {
  Alert,
  AlertTitle,
  Box,
  Button,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableRow,
  Typography,
} from '@mui/material';
import type { ReactNode } from 'react';

export function ErrorState({ title = 'Could not load data', message, onRetry }: {
  title?: string;
  message: string;
  onRetry?: () => void;
}) {
  return (
    <Alert
      severity="error"
      action={
        onRetry ? (
          <Button color="inherit" size="small" startIcon={<RefreshIcon />} onClick={onRetry}>
            Retry
          </Button>
        ) : undefined
      }
    >
      <AlertTitle>{title}</AlertTitle>
      {message}
    </Alert>
  );
}

export function EmptyState({ title, description, action }: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <Box sx={{ py: 8, px: 2, textAlign: 'center' }}>
      <Stack spacing={1.5} alignItems="center">
        <Typography variant="h6" component="p">
          {title}
        </Typography>
        {description && (
          <Typography variant="body2" color="text.secondary">
            {description}
          </Typography>
        )}
        {action}
      </Stack>
    </Box>
  );
}

/** Placeholder rows while a table loads. */
export function TableSkeleton({ rows = 5, columns = 6 }: { rows?: number; columns?: number }) {
  return (
    <Table aria-label="Loading" aria-busy="true">
      <TableBody>
        {Array.from({ length: rows }, (_, row) => (
          <TableRow key={row}>
            {Array.from({ length: columns }, (__, column) => (
              <TableCell key={column}>
                <Skeleton variant="text" />
              </TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
