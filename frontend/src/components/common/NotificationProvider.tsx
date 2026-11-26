import { Alert, Button, Snackbar, type AlertColor } from '@mui/material';
import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';

export interface NotificationAction {
  label: string;
  /** Route to open when the action is clicked. */
  to: string;
}

export interface Notify {
  success: (message: string, action?: NotificationAction) => void;
  info: (message: string, action?: NotificationAction) => void;
  warning: (message: string, action?: NotificationAction) => void;
  error: (message: string, action?: NotificationAction) => void;
}

interface Notification {
  id: number;
  message: string;
  severity: AlertColor;
  action?: NotificationAction;
}

const NotificationContext = createContext<Notify | null>(null);

/** Snackbar messages ("Run started", errors of row actions), shown one at a time. */
export function NotificationProvider({ children }: { children: ReactNode }) {
  const [queue, setQueue] = useState<Notification[]>([]);
  const nextId = useRef(1);
  const navigate = useNavigate();

  const push = useCallback((severity: AlertColor, message: string, action?: NotificationAction) => {
    const id = nextId.current++;
    setQueue((current) => [...current, { id, message, severity, action }]);
  }, []);

  const notify = useMemo<Notify>(
    () => ({
      success: (message, action) => push('success', message, action),
      info: (message, action) => push('info', message, action),
      warning: (message, action) => push('warning', message, action),
      error: (message, action) => push('error', message, action),
    }),
    [push],
  );

  const current = queue[0];
  const close = () => setQueue((items) => items.slice(1));

  return (
    <NotificationContext.Provider value={notify}>
      {children}
      {current && (
        <Snackbar
          key={current.id}
          open
          autoHideDuration={current.severity === 'error' ? 8000 : 5000}
          onClose={(_, reason) => {
            if (reason !== 'clickaway') {
              close();
            }
          }}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        >
          <Alert
            severity={current.severity}
            variant="filled"
            onClose={close}
            sx={{ width: '100%', alignItems: 'center' }}
            action={
              current.action ? (
                <Button
                  color="inherit"
                  size="small"
                  onClick={() => {
                    const { to } = current.action!;
                    close();
                    navigate(to);
                  }}
                >
                  {current.action.label}
                </Button>
              ) : undefined
            }
          >
            {current.message}
          </Alert>
        </Snackbar>
      )}
    </NotificationContext.Provider>
  );
}

export function useNotify(): Notify {
  const notify = useContext(NotificationContext);
  if (!notify) {
    throw new Error('useNotify must be used inside <NotificationProvider>');
  }
  return notify;
}
