package com.memcyco.scheduler.exception;

/** The caller does not (or no longer) hold the edit lock it claims, e.g. it expired and was taken over. */
public class EditLockLostException extends ApiException {

    public EditLockLostException() {
        super(ErrorCode.EDIT_LOCK_LOST,
                "You no longer hold the edit lock for this scheduling. Reload to see the latest version.");
    }
}
