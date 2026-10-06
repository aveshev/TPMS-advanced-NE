# Alerts

Spec for the alert redesign (`feature/alert-redesign`). It replaces the single alert slot in the
persistent scanning notification with separate notifications per sensor and class, three alert
levels, snoozing and spoken alerts.

## Terms

- **Class**: what an alert is about: pressure, temperature, battery, pressure loss, sensor alarm
  or sensor removed (see [Classes and levels](#classes-and-levels)).
- **Level**: how urgent an alert is: amber, red or crimson.
- **Reading**: one stored sensor record. Sensors send each reading several times in a burst, and
  `ListenTyreWithDatabaseUseCase` stores only the first copy, so a burst counts as one reading.
- **Qualifying reading**: a reading whose value puts its class at some alert level.

## Levels

| Level | Meaning | Notification channel |
|---|---|---|
| Amber (warning) | Doesn't need immediate action, can be addressed when convenient | Its own channel |
| Red (alert) | Riding can go on with caution, address at the earliest possibility | Its own channel |
| Crimson (critical) | Must be fixed before riding on | Its own channel |

Each level has its own notification channel, so the user can set its sound, vibration and Do Not
Disturb behaviour separately. The existing alert channels (`MONITOR_SERVICE_FOR_ALERT`,
`MONITOR_SERVICE_FOR_LOW_BATTERY`, `MONITOR_SERVICE_FOR_PRESSURE_LOSS`) are deleted.

## Classes and levels

Pressures are compared after the vehicle's calibration, as today. Temperature margins are in
absolute degrees Celsius, whatever the display unit. All margins are fixed defaults for now, not
settings.

**A value exactly on a boundary belongs to the more severe side**, for every class: a tyre
exactly at its minimum pressure is red, a battery exactly at its low voltage alarm is red.

| Class | Amber | Red | Crimson |
|---|---|---|---|
| Pressure, low side | At or below 3% above the minimum | At or below the minimum | At or below 50% of the minimum |
| Pressure, high side | At or above 3% below the maximum | At or above the maximum | At or above the maximum + 20% |
| Temperature | At or above the hot threshold − 10 °C | At or above the hot threshold | At or above the hot threshold + 20 °C |
| Battery | At or below the low voltage alarm + 0.1 V (today's `LOW_SOON`) | At or below the low voltage alarm, **staying so 10 minutes** | — |

Each column applies when the more severe one doesn't.
| Pressure loss | A leak detected by the pressure loss tracker | — | — |
| Sensor alarm (Sysgration's own alarm) | Raised | — | — |
| Sensor removed | See [Sensor removed](#sensor-removed) | — | — |

- **Every level notifies from the first qualifying reading**, except battery red. A value
  hovering on a boundary is handled by the [hold](#holding-a-notification-at-its-level) instead.
- **Battery red takes two readings at red at least 10 minutes apart**, amber meanwhile: the
  voltage dips in the cold and while the sensor transmits, and recovers afterwards. A time rather
  than a count of readings, since a sensor can report once an hour.
- **Battery reaches red** because a sensor below its low voltage may stop transmitting entirely,
  leaving that tyre unmonitored with no way to alert about it later.
- **The thresholds change from today's behaviour** where they didn't already trigger at the
  boundary:
  - Pressure: today a tyre exactly at its minimum or maximum is in range. It becomes red. The
    pressure settings text ("Below the minimum or above the maximum, it blinks red") changes to
    match.
  - Temperature: the background and stats checks alert strictly above the hot threshold today, the
    tyre icon at it. All of them alert at it, as the temperature settings text already says ("From
    the hot temperature on").
  - Battery is unchanged: the default stays 2.6 V, and red comes at 2.6 V.
- **The maximum pressure is the tyre's maximum inflation pressure**, the one marked on its
  sidewall. The pressure settings suggest setting it so. Red above it, crimson above it + 20%: past
  what the tyre is rated for, not something a tyre warming up reaches. An overfill mistake (a pump
  set to the wrong unit) or a fault.
- **The default maximum becomes 50 psi (345 kPa)** for new vehicles, at the high end of what
  tyres are rated for, against 300 kPa today. Existing vehicles keep theirs. The sidewall maximum
  is a cold pressure: a tyre inflated right up to it can go above it when hot, but the recommended
  pressures are well below it, so a tyre warming up from its recommended pressure stays under it.
  SQLite can't change a column's default without rebuilding the table, so the new default is set
  where vehicles are created rather than in the schema.
- **Pressure loss is a class of its own**: a leak is a different kind of problem from a pressure
  out of range. A leaking tyre reaching its minimum gets a separate red pressure notification.
  The tracker is experimental and off by default, this class only exists while it's on.
- **Sensor alarm stays amber**: the sensor still reports pressure and temperature, which raise
  their own alerts. It's still shown as "Leaking?" in the app.
- Fast leaks don't escalate to crimson. That's left out on purpose, to avoid false positives.

### Sensor removed

Taking a valve-cap sensor off to pump the tyre must not raise a crimson pressure alert. A reading
below `OFF_VALVE` (10 kPa, already used by `PressureLoss.Tracker`) counts as the sensor being
removed when:

- the previous reading from the same sensor was **not** at crimson pressure, and
- that previous reading is recent: within **10 minutes**, the tracker's ride gap.

A removal is the pressure jumping straight from a tyre's pressure to near zero. A real deflation
goes through readings in between, since a sensor transmits more often while its pressure keeps
changing, and the last of them before the open air is crimson. The previous reading can be amber
or red: a tyre that's already low is the most common one to be pumped up. The recency check keeps a tyre that went flat overnight from being misread as a removal:
nothing was listening while it deflated, so its previous stored reading is yesterday's normal one.
That case is a real crimson pressure alert.

A removal raises a **sensor removed** alert at amber level:

- It notifies on the **first** reading: a sensor reading open air has no pressure change left to
  report, and may never transmit again.
- It clears on the first reading **above `OFF_VALVE`**, not on any non-zero reading: open air can
  read a few kPa of noise. That reading is evaluated normally, so a tyre pumped up too little goes
  straight to red.
- The removal reading itself raises no pressure alert.

Motion detection (`ActivityRecognitionUseCase`, `SignificantMotionUseCase`) could be added later as
a second check, it isn't needed for this.

## Notifications

### One notification per sensor and class

- Every alert is a notification of its own, keyed by **vehicle, sensor and class**. A tyre with a
  pressure alert that then also gets hot shows a second notification, so the new alert is seen as
  new rather than as an update of the first.
- Alert notifications are separate from the persistent scanning notification, which only shows the
  scanning status (active, suspended, idle) on its low importance channel.
- They're posted whatever the app's state, including while the main screen is open: the user may
  not be looking at it.
- Each notification names the vehicle (as its subtext) and the tyre position, and gives the
  reading and the threshold it crossed. The title is the condition alone ("Pressure critically
  low"), short enough not to be cut off beside the vehicle's name, the tyre position starting the
  text ("Rear left wheel: 5.8 psi, minimum 14.5 psi").
- `setWhen` is the reading's own timestamp, so a notification left over from earlier looks its
  age.
- Tapping it opens the app on its vehicle, as today.

### When a notification sounds

| Event | Effect |
|---|---|
| A class goes to a higher level | Notification updated on the new level's channel, **sounds** |
| A new red or crimson qualifying reading at the same level | **Sounds again**, at every reading, with no minimum gap |
| A new amber qualifying reading at the same level | Silent update |
| A class goes down to a lower level, still alerting | Silent update on the lower level's channel, once the [hold](#holding-a-notification-at-its-level) ends |
| A reading below every level | Notification **cancelled**, once the hold ends |

Re-sounding on every red or crimson reading is intended: a tyre losing pressure fast enough to
transmit every few seconds is worth hearing about that often. Bursts are already collapsed into
one reading, so a single reading sounds once.

### Holding a notification at its level

Every reading which posts a notification holds it at its level for **3 minutes**. A reading which
would lower or clear it meanwhile is queued, the latest one replacing the previous: it only goes
through once the hold ends, unless a reading at that level or above comes first, which discards it
and starts the hold over.

While riding, the sensors report about once a minute, in steps of 0.4 to 0.5 psi: a value
hovering on a boundary would otherwise clear and post its notification again, with a sound, every
minute or two. A sensor reporting once an hour isn't affected, its lowering going through right
away. The hold is kept in memory: after the process died, a notification keeps its level until its
next reading.

The hold only concerns the notification. The speech follows each reading right away: a reading
lowering a crimson alert stops its loop at once, and a red one is said as red.

### Only new readings alert

The evaluator only alerts on readings stored **after it started listening**. The latest stored
reading, replayed when listening starts, never alerts. Background scanning becoming active doesn't
re-raise alerts from readings taken days ago.

What this costs:

- A tyre left below its minimum at the end of a ride doesn't alert at the next ride's start, only
  at its first reading during it. That's usually within minutes: a tyre starting to move or warm
  up transmits.
- Changing a threshold doesn't alert about readings already stored. The main screen shows the new
  level at once.
- Readings stored while nothing was evaluating them never alert.
- Force-stopping the app clears its notifications. An ongoing alert then stays silent until the
  next reading.

## Snoozing

### Actions

| Level | Actions |
|---|---|
| Amber | "Dismiss 1 day", "Dismiss 1 week" |
| Red, crimson | "Dismiss 10 min", "Dismiss 1 day" |

Swiping a notification away counts as its **shorter** period. "Clear all" does the same to every
alert notification.

### Rules

- **Key: sensor and class.** Binding a replacement sensor escapes the snooze: the cause of the
  alert has most likely been dealt with at the same time.
- A snooze on a level silences that class's readings **at that level or lower** for that sensor,
  until it ends. A **higher** level gets through, and notifies.
- Snoozing a level also snoozes **the lower levels** of the same sensor and class for the same
  period, unless they're already snoozed until later.
- **A snooze survives the condition clearing.** A reading going back and forth across a threshold
  doesn't get around it.
- **A snooze ending doesn't alert by itself.** Only the next qualifying reading does.
- **Snoozes are stored on disk**: the service gets killed and restarted, and an in-memory snooze
  would re-raise everything at each restart.
- A snooze only silences notifications and speech. The main screen keeps showing every reading's
  level.

## Spoken alerts

| Level | What's said | When | Audio usage |
|---|---|---|---|
| Amber | Nothing | — | — |
| Red | The class twice: "TYRE PRESSURE", "TYRE HOT", "SENSOR BATTERY" | Each time its notification sounds | `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` |
| Crimson | "TYRE PRESSURE CRITICAL" or "TYRE HOT CRITICAL", twice | Every 20 s, see below | `USAGE_ALARM` |

- The phrases stay this short on purpose: they say what to look for, the screen says the rest.
  They don't name the tyre position.
- Navigation guidance reaches helmet intercoms and Android Auto like navigation prompts do, and
  ducks the music. The alarm usage plays even with the media volume muted, and Android plays it
  through the phone's speaker as well as a connected headset.
- **One speech queue.** Several crimson alerts share one loop ("TYRE PRESSURE CRITICAL, TYRE HOT
  CRITICAL"), and red announcements wait their turn instead of talking over it.
- Speech waits for a notification's sound to play out rather than speaking over it: Android plays
  it about half a second after it's posted, so the speech waits up to 1.5 s for one to start, then
  until no notification sound plays (10 s at most). A silent or vibrating channel doesn't delay
  it. It takes the audio focus (ducking what plays) only while it speaks.
- **Spoken alerts can be turned off**, in a new "Alerts" group of the app settings. They're on by
  default.

### Crimson loop

- Says its phrase twice every 20 s, counted from the start of each.
- Runs for **10 minutes**. Each new qualifying reading of the alert restarts the 10 minutes.
- Stops when the alert is snoozed or dismissed, when a reading clears it, or when scanning is
  suspended or stopped (no reading could clear it any more).
- After scanning resumes, only a new qualifying reading starts it again.

## In the app

- **One evaluator** decides every tyre's level, for the notifications, the speech, the main screen
  and Android Auto. Today `TyreIconStateFlow`, `TyreStatsStateFlow` and `VehicleAlertUseCase` each
  have their own rules, and already disagree (`>` vs `≥` on the temperature).
- Every reading updates the main screen, whether or not it notifies.
- A new **"Alerts" group in the app settings** holds the spoken alerts switch.

### Visuals

Red and crimson are told apart by blink speed and a label rather than by colour alone, since the
two look alike in sunlight and to colour-blind users. The tyre icon follows the tyre's own
condition, its pressure and temperature: a low battery blinks its voltage, not the tyre.

| Level | Tyre icon | Tyre stats |
|---|---|---|
| Amber | Unchanged (no blinking) | The offending value in the theme's warning orange, as the battery's "getting low" is today |
| Red | Blinks red, in 400 ms phases | The offending value in red, as today |
| Crimson | Blinks red, in 200 ms phases | The offending value in red, alternating with "CRITICAL" in 400 ms phases |

The tyre blinks in 300 ms phases today. Halving that would make about 3.3 flashes a second, above
the usual photosensitivity limit of 3 (WCAG): red slows down to 400 ms instead, and crimson stays
under the limit at 2.5 flashes a second.

The normal tyre colour already fades from green to red as the temperature nears the hot threshold,
so a tyre at amber temperature is already reddish: the orange value in the stats is what marks it
as amber. Android Auto follows the same levels with its own icons.

## Architecture

- **The evaluator runs at app scope**, not in `MonitorService`, so alerts work the same while the
  app is open without background monitoring.
- **It observes the stored readings rather than the scan.** Subscribing to the tyre flows is what
  starts the BLE scan (they're shared `WhileSubscribed`), so an app-scope subscriber would scan
  forever. Every reading, from the main screen's scan or the service's, is stored by
  `ListenTyreWithDatabaseUseCase`. Watching the stored rows:
  - never starts a scan of its own: it sees whatever is scanning,
  - makes "readings stored after it started listening" the definition of a new reading,
  - gets bursts already collapsed,
  - matches `TyrePressureLossStateFlow`, which already rebuilds from stored readings.
- It covers every vehicle, not only the current one, as the service does today.
- Starting the service while the app is open was considered and rejected: in non-persistent mode it
  would show the monitoring notification and turn the bell and the manual button to "running" just
  because the app is open.
- The notification ids are stable per vehicle, sensor and class.
- `ServiceNotifier` keeps only the scanning status. A scan failure moves off the alert channel, onto
  the scanning status notification.

## Out of scope

- **Non-persistent ("legacy") mode as a special case of persistent mode**: activate conditions set
  to always, suspended on phone idle only unless turned off. It's a scan policy change, alerts
  already behave the same in both modes. Later, on its own branch.
- **Scan failure recovery.** A scan starting while Bluetooth is off fails with
  `Failure.ScannerIsNull`, and the service's pipeline ends for good on any failure: it says "TPMS
  Advanced must be restarted" and doesn't resume when Bluetooth comes back. Bluetooth being off
  should be a scanning status that resumes on its own, and transient `onScanFailed` errors
  retried. On its own branch.
- **Full-screen intent for crimson alerts.** Later. Since Android 14, Play only allows
  `USE_FULL_SCREEN_INTENT` for calling and alarm apps, so it may not be possible.
- **`FLAG_INSISTENT`**: repeating sounds are handled by the crimson loop instead.
- **Alerting about a sensor going silent**: tyres on the non-driven axle can stay silent for days,
  even during rides.
- **Crimson for fast leaks**: left out to avoid false positives.

## Open questions

None at the moment.
