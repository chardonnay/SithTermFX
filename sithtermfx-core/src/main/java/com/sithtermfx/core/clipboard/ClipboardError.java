package com.sithtermfx.core.clipboard;

/**
 * The error statuses of OSC 5522, as sent back to the program.
 */
public enum ClipboardError {
    /** The user or the system did not allow the access. */
    EPERM,
    /** Another access is in progress; the program may try again. */
    EBUSY,
    /** This clipboard is not available, such as a primary selection where there is none. */
    ENOSYS,
    /** The clipboard could not be read or written. */
    EIO,
    /** The request is invalid, such as bad base64 or a data packet without a MIME type. */
    EINVAL,
    /** The data is more than the terminal accepts. */
    EFBIG
}
