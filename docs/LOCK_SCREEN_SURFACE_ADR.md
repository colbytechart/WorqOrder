# Running-Timer Lock-Screen Surface ADR

Status: **approved, implemented, and verified in Milestone 28**
Research completed: **2026-08-08**  
Owner approved all four documented tradeoffs: **2026-08-08**  
Applies to: **WorqOrder 0.2.0, Milestones 27 and 28**

## 1. Question

WorqOrder needs an optional Android system surface that is present only while Room contains an
open interval. It should show WorqOrder identity, the active task description, and an advancing
elapsed total; a user dismissal must hide the surface for that interval without stopping timing.
The design must support API 26 through target API 36 without a foreground stopwatch service,
exact alarm, wake lock, WorkManager tick, or persisted display ticks.

Android has no cross-version API that lets an application automatically install a temporary,
lock-screen-only widget. The implementable product is therefore a normal notification that is
eligible to appear on the lock screen and also appears in the notification shade. The user,
notification-channel settings, secure-lock-screen privacy policy, and OEM System UI retain final
control over whether and how it is shown.

## 2. Official findings

### 2.1 Standard notification and system chronometer

`Notification.Builder.setUsesChronometer(true)` makes Android render and automatically update the
notification's `when` value as elapsed minutes and seconds. It has existed since API 16, so it
covers the complete supported range without an application tick loop. `SystemClock.elapsedRealtime`
includes device sleep and is the correct process-live elapsed timebase. Android also defines the
`stopwatch` notification category.

The AOSP DeskClock timer uses an ongoing notification with a system `Chronometer` inside custom
`RemoteViews`; it reserves a foreground notification/service for the expired/ringing state. That
is useful precedent for system-rendered timer text, but WorqOrder does not have an expiring alarm
and does not need DeskClock's service, exact alarm, or custom layout machinery.

Sources:

- [Notification.Builder `setUsesChronometer`](https://developer.android.com/reference/android/app/Notification.Builder#setUsesChronometer(boolean))
- [`SystemClock.elapsedRealtime`](https://developer.android.com/reference/android/os/SystemClock#elapsedRealtime())
- [`CATEGORY_STOPWATCH`](https://developer.android.com/reference/android/app/Notification#CATEGORY_STOPWATCH)
- [AOSP DeskClock `TimerNotificationBuilder`](https://android.googlesource.com/platform/packages/apps/DeskClock/+/6ac43d8368e8b09c4d683d6d993e1862b4f09464/src/com/android/deskclock/data/TimerNotificationBuilder.java)
- [AOSP DeskClock `TimerService`](https://android.googlesource.com/platform/packages/apps/DeskClock/+/6ac43d8368e8b09c4d683d6d993e1862b4f09464/src/com/android/deskclock/timer/TimerService.java)

### 2.2 Permission, channel, and privacy control

API 33 and later require the runtime `POST_NOTIFICATIONS` permission for this ordinary
notification. Earlier supported APIs do not. API 26 and later require a notification channel,
whose visibility and importance the user may change after creation. App and channel disablement
can suppress the surface completely.

Lock-screen visibility is not guaranteed. `VISIBILITY_PRIVATE` lets Android conceal sensitive
content on a secure lock screen or during screen sharing, while a supplied public version can show
redacted content. The user can still hide all lock-screen notifications or this channel. Using
`VISIBILITY_PUBLIC` would expose the task description in full on secure lock screens and while
screen sharing, so it is not recommended for potentially sensitive work data.

Sources:

- [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Notification channels](https://developer.android.com/develop/ui/compose/notifications/channels)
- [Lock-screen notification visibility](https://developer.android.com/develop/ui/compose/notifications/create-notification#set-lockscreen-visibility)
- [`Notification.VISIBILITY_PRIVATE`](https://developer.android.com/reference/android/app/Notification#VISIBILITY_PRIVATE)

### 2.3 Dismissal

A notification `deleteIntent` runs when the user swipes the notification away or uses Clear All,
so it can record that the current active interval's surface was dismissed. The notification must
not be marked ongoing: older Android releases make ongoing notifications non-dismissible, and
Android 14 still prevents dismissing an ongoing notification while the phone is locked. A plain,
non-ongoing notification matches the requested swipe behavior.

Sources:

- [`Notification.deleteIntent`](https://developer.android.com/reference/android/app/Notification#deleteIntent)
- [Android 14 ongoing-notification dismissal behavior](https://developer.android.com/about/versions/14/behavior-changes-all#non-dismissable-notifications)

### 2.4 Process death, reboot, and force-stop

Android System UI owns a posted notification, so ordinary cached-process death does not require an
application ticker. Room still owns the active interval. If WorqOrder later starts again, it must
reconcile the posted surface with Room and the persisted per-interval dismissal marker.

This process-death statement is an engineering inference from Android's system-managed notification
contract and the AOSP DeskClock design, not a promise that every OEM preserves every notification
under every resource policy. Room recovery remains the correctness path if System UI removes it.

A reboot clears transient system presentation. A one-shot manifest `BOOT_COMPLETED` receiver can,
after the user first unlocks the device, read credential-protected Room state and repost an eligible
surface. This requires the normal `RECEIVE_BOOT_COMPLETED` manifest permission, not a runtime
dialog, foreground service, alarm, or continuous process. WorqOrder should deliberately avoid
`LOCKED_BOOT_COMPLETED` and device-protected duplication of the task description; therefore no
surface is promised between reboot and first unlock.

Force-stop is different from process death. Android places the package in a stopped state and does
not let it self-start until a later user action. The surface can be recovered only when the user
launches WorqOrder again. This platform behavior cannot be bypassed and must not stop or delete the
Room interval.

Sources:

- [`ACTION_BOOT_COMPLETED`](https://developer.android.com/reference/android/content/Intent#ACTION_BOOT_COMPLETED)
- [Implicit-broadcast exceptions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)
- [Android 15 package stopped-state behavior](https://developer.android.com/about/versions/15/behavior-changes-all#changes-package-stopped-state)

### 2.5 AppWidget, full-screen, foreground-service, and Live Update alternatives

- **AppWidget:** rejected. App widgets are host/user placed; a keyguard category only advertises
  eligibility and does not make a compatible lock-screen host exist or let WorqOrder add/remove a
  widget automatically. AppWidget updating also does not provide the requested portable
  second-by-second surface without extra machinery.
- **Custom DeskClock-style `RemoteViews`:** rejected. It adds a custom XML layout, gains no needed
  timer authority, and is subject to modern custom-notification template restrictions. The standard
  notification chronometer is sufficient and remains consistent with the Compose-only repository.
- **Foreground service:** rejected. WorqOrder performs no continuous user-visible operation that
  requires a live process; Room plus the system chronometer already reconstruct elapsed time. A
  foreground service would add lifecycle, permission, dismissal, and user-stop consequences without
  improving timer correctness.
- **Full-screen intent/alarm UI:** rejected. Android reserves this intrusive behavior for urgent
  calls and ringing alarms. A work stopwatch is not such an event.
- **Android 16 Live Update/promoted ongoing notification:** rejected as the cross-version baseline.
  It is API-36-era optional promotion, requires another manifest permission and user/OEM approval,
  and promoted treatment is not guaranteed. Its ongoing requirement also conflicts with reliable
  lock-screen swipe dismissal. It can be reconsidered as a future enhancement, not as the API 26
  contract.

Sources:

- [App widgets overview](https://developer.android.com/develop/ui/views/appwidgets/overview)
- [`WIDGET_CATEGORY_KEYGUARD`](https://developer.android.com/reference/android/appwidget/AppWidgetProviderInfo#WIDGET_CATEGORY_KEYGUARD)
- [Custom-notification restrictions](https://developer.android.com/about/versions/12/behavior-changes-12#custom-notifications)
- [Full-screen notification guidance](https://developer.android.com/develop/ui/compose/notifications/create-notification#urgent-message)
- [Live Update requirements](https://developer.android.com/develop/ui/views/notifications/live-update)

## 3. Recommended Milestone 28 contract

The owner-approved Milestone 28 contract is:

1. A dedicated, silent **Running Timer** channel at low importance, with sound, vibration, and
   launcher badge disabled.
2. One standard `NotificationCompat` notification categorized as `CATEGORY_STOPWATCH`; do not use
   custom `RemoteViews`, `setOngoing(true)`, a foreground service, or periodic updates.
3. Full private content containing WorqOrder identity, active **Client** plus task **Description**
   on the supporting line, and a system-rendered chronometer for the selected task's accumulated
   completed-plus-active duration.
4. `VISIBILITY_PRIVATE` plus a redacted public version containing only WorqOrder identity and the
   system chronometer. Its supporting line is blank, so neither Client nor Description appears
   when Android hides sensitive notification content.
5. A direct Activity `PendingIntent` that opens WorqOrder Main. Do not use a notification
   trampoline and do not add Stop or edit actions.
6. A `deleteIntent` receiver that durably records only the dismissed active interval ID in typed
   Preferences DataStore. Dismissal never changes Room. Stop cancels the notification and clears
   obsolete dismissal state; a different interval ID from a later Start is eligible to appear.
7. Timer Start always succeeds or fails solely by existing timer-domain rules. On API 33+, request
   notification permission contextually after the first successful Start when appropriate. Denial
   or a disabled channel suppresses only this surface and produces concise, actionable in-app
   guidance; it never blocks timing.
8. Application Start/resume reconciles the surface against Room. A one-shot `BOOT_COMPLETED`
   receiver, using `RECEIVE_BOOT_COMPLETED`, does the same after first unlock. It performs no tick
   loop and posts nothing when there is no active interval or the interval was dismissed.
9. Use one calculated zero point when posting so Android advances the accumulated task total. The
   platform owns its exact typography and compact formatting; WorqOrder must not promise the exact
   Main-card layout on every OEM lock screen.

The notification cannot discover a local midnight while WorqOrder has no executing component. If
an interval crosses midnight while the process is absent, its system chronometer continues until
the next existing recovery trigger. Reconciliation then performs the authoritative Room split and
reposts for the new daily task total, which can produce a visible reset. Avoiding that delayed reset
would require a midnight wake/scheduled component or continuously running service and is not part
of the recommended surface.

## 4. Required acceptance boundaries

- Notification state is presentation only. Room's singleton active timer remains authoritative.
- No notification post/update writes a changing duration to Room or DataStore.
- The full task description is potentially visible outside the app when the user permits sensitive
  lock-screen content; privacy documentation must say so.
- Notification permission, channel disablement, lock-screen privacy, Do Not Disturb, OEM System UI,
  force-stop, and pre-unlock reboot state can suppress presentation.
- Dismissal is scoped to the active interval ID, including across process death and reboot.
- A midnight continuation has a new interval ID and is therefore a new eligible surface after timer
  normalization, consistent with the existing per-interval dismissal rule.
- A background/process-dead midnight can leave the pre-normalization chronometer visible until the
  next recovery trigger; after the Room split, the surface may reset to the new daily task total.
- API 26 and the current target require automated and physical/emulator coverage, including denial,
  swipe, Stop, process kill, reboot/first unlock, force-stop/relaunch, clock change, and CPU/battery
  checks.

## 5. Approval record

No lock-screen code, manifest receiver, channel, DataStore key, or permission behavior was added in
Milestone 27. On 2026-08-08, the owner approved the notification-shade presence,
private/redacted lock-screen behavior, contextual permission request, and post-unlock boot receiver.
That approval selects the design but does not itself start Milestone 28; implementation begins only
after the owner explicitly requests the next milestone.

## 6. Milestone 28 implementation record

The owner explicitly started Milestone 28 on 2026-08-08. The implementation follows this ADR:

- `RunningTimerNotificationCoordinator` reconstructs presentation from Room, the current clock,
  and the process-local monotonic timer anchor. It never stores an elapsed display value.
- `AndroidRunningTimerNotificationGateway` owns the silent low-importance channel, private/public
  standard notifications, direct Main tap, and system chronometer. Android owns the compact
  chronometer position. The main title is **WorqOrder**; the private supporting line is
  **Client · Description**, while the public supporting line is completely blank.
- `PreferencesRunningTimerNotificationPreferences` stores only the dismissed active interval ID.
- Start posts or requests API-33+ permission without blocking timing; Stop cancels and clears stale
  dismissal state. Application startup, Activity resume, date normalization, and the post-unlock
  boot receiver reconcile against Room. Regaining Activity window focus after Android Settings
  also reconciles an already-running timer when the user grants permission; it does not poll or
  keep the process alive.
- The manifest adds only `RECEIVE_BOOT_COMPLETED` and two non-exported receivers. No service,
  alarm, wake lock, widget host, custom notification layout, or tick worker was added.

The final project-local gate passed 208 JVM tests and 102 connected tests with zero failures,
errors, or skips, plus debug lint and debug/release assembly. Manual checks passed notification
permission denial/recovery, channel and privacy behavior, private/public content, accumulated
chronometer, direct navigation, swipe dismissal, Stop cleanup, new-interval reappearance,
background/lock/Recents, process/reboot/force-stop recovery, and a short resource check.

## 7. `0.3.0` midnight-policy addendum

Sections 3 and 4 above preserve the released `0.2.0` notification behavior. Current `0.3.0`
midnight handling closes the sole interval exactly at its first pinned-ZoneId boundary, clears the
active-timer row, and creates no continuation task or interval. Reconciliation therefore cancels
the running notification rather than reposting it for a continuation. If Android has no executing
WorqOrder component at the boundary, the old chronometer may remain visible until the next
legitimate worker, resume, boot-recovery, or launch callback performs the authoritative close and
notification reconciliation. No exact alarm, wake lock, or foreground stopwatch service is added.
