package org.cyphy_lab.lapd.core;

/**
 * State machine enum for our application
 */
public enum CurrentState {
    FINDING_REFERENCE_ANCHOR,
    WAITING_FOR_USER_BBOX,
    CALCULATING_IDEAL_DISTANCE,
    SCANNING_OBJECT
}
