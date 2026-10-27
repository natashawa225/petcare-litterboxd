package com.litterboxd.app.checkin;

/** How many answers a question accepts. */
public enum SelectionMode {
    /** Exactly one answer at most; choosing another replaces it. */
    SINGLE,
    /** Any number of answers; a "normal" answer excludes the others. */
    MULTI
}
