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

The Compose core uses the Precision Finance palette (`#F5F6F7` background, white surfaces, `#16181C` text, `#35675D` accent), 20dp page gutters, thin dividers, compact rows, small radii, and monospace aligned amounts. Avoid turning every section into a card. Keep Android activity-result APIs in `MainActivity`, not in presentation state.

## 6. Debugging Notes

The Android SDK is at `C:\Users\Lamires\AppData\Local\Android\Sdk`. Stable platform 34 and build-tools 34.0.0 are installed. A JDK 21 runtime is available under the user's `.jdks` directory. When `Application` implements WorkManager `Configuration.Provider`, remove `androidx.work.WorkManagerInitializer` through a manifest merge rule; otherwise lint reports conflicting initialization. Robolectric also depends on the provider for on-demand initialization.

## 7. Do Not Do

- Do not calculate recurring month/year dates by chaining from a clamped prior occurrence.
- Do not mix notification time-zone conversion into the date-only recurrence engine.
- Do not schedule from a stale event date directly; advance from the persisted anchor through `RecurrenceEngine`, and reconcile again after each delivered recurring reminder.
- Do not add destructive Room migration fallbacks.
- Do not expose Room entities or DAOs to Compose/presentation.
- Do not restore an imported backup until `BackupCodec` validation has succeeded and the user explicitly confirms Replace semantics.
- Do not combine amounts across currencies or imply currency conversion.
