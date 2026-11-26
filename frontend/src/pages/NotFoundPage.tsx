import { Button } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { EmptyState } from '../components/common/States';

export function NotFoundPage() {
  return (
    <EmptyState
      title="Page not found"
      action={
        <Button component={RouterLink} to="/" variant="contained">
          Back to schedulings
        </Button>
      }
    />
  );
}
