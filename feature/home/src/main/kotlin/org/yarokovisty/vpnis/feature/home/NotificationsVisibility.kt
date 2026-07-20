package org.yarokovisty.vpnis.feature.home

/**
 * Pure predicate for the notifications-permission rationale banner (#131).
 *
 * The section is shown when ALL of the following hold:
 * 1. The system dialog has been shown at least once — [hasRequestedBefore] — so we never surface
 *    a settings deep-link before the in-app dialog has had its chance (fresh-install / cold-start
 *    guard, plan DM-1).
 * 2. The gate is still denied — [granted] is false — meaning both the app-level permission and
 *    the tunnel channel importance must be "on" for this to become false.
 * 3. The system dialog is not currently on screen — [requestInFlight] is false — to prevent
 *    the banner and dialog appearing simultaneously.
 * 4. The user has not dismissed the section this app session — [dismissed] is false.
 *
 * This function has no side effects and no dependencies beyond its parameters — it is the
 * single source of truth used in [HomeRoute] for both Connected and Disconnected placements.
 * The truth-table unit tests live in issue #132.
 */
internal fun shouldShowNotificationsSection(
    hasRequestedBefore: Boolean,
    granted: Boolean,
    requestInFlight: Boolean,
    dismissed: Boolean,
): Boolean = hasRequestedBefore && !granted && !requestInFlight && !dismissed
