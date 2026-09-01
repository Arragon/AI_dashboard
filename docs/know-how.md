# Project Know-How

## 1. Common Problems

Calendar arithmetic that repeatedly adds a month to the previous result drifts after short months. For example, January 31 can become February 28 and then March 28.

## 2. Proven Solutions

Keep recurrence anchors explicit. Monthly recurrence derives every occurrence from the original anchor day and clamps only the target month. Yearly recurrence likewise keeps the original month/day, making February 29 occur on February 28 in non-leap years and return to February 29 in leap years.

## 3. Development Notes

- Use `LocalDate` for date-only subscription and recurrence rules.
- Keep Room annotations out of domain models; add persistence DTO/entity mappers in a future data package.
- Convenience recurrence factories are aliases for interval-based rules, including quarterly as three months.
- Cancellation with service access remaining is represented as `CANCELLED_PENDING_EXPIRY`; expiry processing later changes it to `EXPIRED`.

## 4. Testing Notes

- Run `gradlew.bat :app:testDebugUnitTest` with `JAVA_HOME=C:\Users\Lamires\.jdks\jbr-21.0.11` and `ANDROID_HOME=C:\Users\Lamires\AppData\Local\Android\Sdk`.
- Run `gradlew.bat :app:assembleDebug` with the same environment. The native path helper library may emit a harmless unable-to-strip packaging notice.
- Presentation coroutine tests use a `StandardTestDispatcher` as both Main and injected IO dispatcher, then `advanceUntilIdle()` after each ViewModel action.
- The V0.1 JVM suite currently contains 64 tests. Run `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` before delivery.
- Recurrence tests must cover month-end clamping and recovery, leap-day yearly behavior, inclusive/exclusive boundaries, year boundaries, custom intervals, one-time exhaustion, and date-only behavior across time zones.
- Lifecycle tests must cover immediate cancellation, pending cancellation, the expiry boundary, and unrelated statuses.

## 5. UI/UX Notes

The Compose core uses the Quiet Ledger system documented in root `DESIGN.md`: warm paper/charcoal canvases, separately calibrated light and dark semantic palettes, one juniper accent, 20dp page gutters, thin dividers, restrained surfaces, and monospace aligned amounts. Avoid turning every section into a card. Keep Android activity-result APIs in `MainActivity`, not in presentation state.

## 6. Debugging Notes

The Android SDK is at `C:\Users\Lamires\AppData\Local\Android\Sdk`. Stable platform 34 and build-tools 34.0.0 are installed. A JDK 21 runtime is available under the user's `.jdks` directory. When `Application` implements WorkManager `Configuration.Provider`, remove `androidx.work.WorkManagerInitializer` through a manifest merge rule; otherwise lint reports conflicting initialization. Robolectric also depends on the provider for on-demand initialization.

If a Gradle build crashes the JVM (hs_err_pid*.log / replay_pid*.log appear in the project root, typically `Out of Memory Error` / native `Chunk::new`), the daemon leaves file locks on `app/build/intermediates/.../R.jar`. Recovery: `gradlew.bat --stop`, force-stop any `java`/`gradle` processes, delete `app\build` and the root `build` directory, then re-run. Do not delete the crash logs while diagnosing, but they are not part of the source tree and should not be committed.

## 7. Do Not Do

- Do not calculate recurring month/year dates by chaining from a clamped prior occurrence.
- Do not mix notification time-zone conversion into the date-only recurrence engine.
- Do not schedule from a stale event date directly; advance from the persisted anchor through `RecurrenceEngine`, and reconcile again after each delivered recurring reminder.
- Do not add destructive Room migration fallbacks.
- Do not expose Room entities or DAOs to Compose/presentation.
- Do not restore an imported backup until `BackupCodec` validation has succeeded and the user explicitly confirms Replace semantics.
- Do not combine amounts across currencies or imply currency conversion.

## 8. Localization

- V0.2 adds English (`values`) and Simplified Chinese (`values-zh-rCN`) string resources.
- In-app language switching is implemented via `AppLanguageManager` using `SharedPreferences` and `Context.createConfigurationContext` in `Application.attachBaseContext` and `MainActivity.attachBaseContext`.
- `CoreViewModel` validation and error strings remain in English for V0.2 because they flow through `Throwable.message` and are not yet mapped to string resources.
- `semantics { contentDescription = ... }` is not a Composable context, so `stringResource` values must be captured into local variables before being assigned.
- `@Composable` label helpers (`statusLabel`, `eventTypeLabel`, `unitLabel`, `recurrenceDisplay`) must not be invoked inside non-Composing lambdas such as `associateBy`; use explicit `for` loops in the Composable body instead.

## 9. Visual System

- `DESIGN.md` is the durable visual source of truth; `ui/theme/Theme.kt` is its semantic Compose implementation.
- Keep theme switching system-driven through `isSystemInDarkTheme()` unless a persisted user override is explicitly added later.
- Core text/background pairs have automated WCAG AA contrast tests in `ThemeTest`.
- Keep motion bounded to short color/transform feedback. Avoid perpetual animation, layout-property animation, blur-heavy surfaces, and per-frame state updates.
