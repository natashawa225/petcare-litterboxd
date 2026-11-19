package com.litterboxd.app.health;

/**
 * The health evaluation state of a single signal for a single calendar day.
 */
public enum DayState {
    NORMAL,
    OFF,
    CONCERN,
    NOT_LOGGED
}
