import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';

export interface ResumeBlocked {
  schedulingName: string;
  message: string;
  conflict: { id: string; name: string } | null;
}

/** Resuming was refused because an identical scheduling is active (409 DUPLICATE_SCHEDULING). */
export function ResumeBlockedDialog({ blocked, onClose, onGoTo }: {
  blocked: ResumeBlocked | null;
  onClose: () => void;
  onGoTo: (conflict: { id: string; name: string }) => void;
}) {
  return (
    <Dialog open={blocked !== null} onClose={onClose} aria-labelledby="resume-blocked-title" maxWidth="sm">
      <DialogTitle id="resume-blocked-title">Can&apos;t resume “{blocked?.schedulingName}”</DialogTitle>
      <DialogContent>
        <DialogContentText component="div">
          {blocked?.message}
          <p>
            Only one active scheduling may have the same task, parameters and timing. Pause the other one first, or
            keep this one paused.
          </p>
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        {blocked?.conflict && (
          <Button variant="contained" onClick={() => onGoTo(blocked.conflict!)}>
            Go to “{blocked.conflict.name}”
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
