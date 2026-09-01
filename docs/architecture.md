# Project Architecture

## 1. Overview

Subscription Tracker is an Android-first, local-first subscription ledger. The application now integrates the pure domain layer with Room persistence, WorkManager reminders, validated SAF backup/replace restore, and a minimal usable Compose workflow for overview, subscriptions, insights, details, and settings.

## 2. Tech Stack

- Kotlin 2.0.21 on JDK 21
- Android Gradle Plugin 8.7.3 and Gradle 8.9
- compileSdk 34 and minSdk 26
- Jetpack Compose with the Compose BOM
- Declared dependencies for Room, WorkManager, and kotlinx.serialization
- `java.time` for domain dates
- JUnit 5 for local JVM tests

## 3. Directory Structure

- `app/src/main/java/com/subscriptiontracker/domain/model`: Android-independent domain entities and value objects
- `app/src/main/java/com/subscriptiontracker/domain/repository`: persistence-agnostic repository contracts
- `app/src/main/java/com/subscriptiontracker/domain/service`: pure recurrence and lifecycle rules
- `app/src/main/java/com/subscriptiontracker/data`: Room repositories/store and validated backup codec/replace service
- `app/src/main/java/com/subscriptiontracker/platform`: Android notification permission and WorkManager scheduling adapters
- `app/src/main/java/com/subscriptiontracker/presentation`: lifecycle ViewModel, immutable screen state, validated inputs, and narrow backup/schedule ports
- `app/src/main/java/com/subscriptiontracker/ui`: Compose workflow and Precision Finance theme
- `app/src/test`: local JVM domain, persistence, backup, reminder, and presentation tests
- `docs`: architecture and reusable implementation notes

## 4. Core Modules

The current single Android application module enforces package-level boundaries. Domain models do not use Room annotations or Android APIs. `RecurrenceEngine` calculates anchored occurrences. `SubscriptionLifecycleService` applies cancellation and persists expiry transitions during refresh. Repository interfaces isolate Room and leave a future remote/sync implementation possible. Reminder policy is pure domain logic; WorkManager, permission checks, receivers, notifications, and the private schedule registry are Android adapters.

## 5. Frontend Architecture

`MainActivity` owns Android activity-result launchers for notification permission and SAF documents, constructs `CoreViewModel` from the application container, and hosts `CoreApp`. Compose reads domain-backed presentation state only. The UI provides Overview, Subscriptions, Insights, Settings, detail, subscription/event/quota forms, confirmations, and responsive width constraints. The root `DESIGN.md` defines the Quiet Ledger visual system; `ui/theme/Theme.kt` maps its semantic roles to separately calibrated light and dark Material color schemes selected from the system theme, while Compose primitives standardize typography, shape, touch targets, navigation selection, and transform/color-only feedback.

## 6. Backend Architecture

There is no network backend. Room entities/DAOs and mappers remain in `data.database`; repository implementations expose domain interfaces. `SubscriptionTrackerApplication` is the manual container for repository interfaces, `RoomRecordStore`, backup services, reminder reconciliation, and notification status/settings routing. The schema is version 1 and exports Room schema metadata.

## 7. Data Flow

Compose sends user intents to `CoreViewModel`. The ViewModel validates form text into domain models, uses only repository interfaces/pure services/narrow backup and schedule ports, reloads state after mutations, applies due lifecycle transitions, and reconciles reminder schedules. Room adapters map persistence records to domain models. Import is decoded and validated before an explicit Replace confirmation; confirmed restore performs one atomic replacement and then reloads/reconciles. Reminder reconciliation derives the next future occurrence for each offset, cancels stale unique work, and uses WorkManager one-time requests; each delivered recurring reminder enqueues reconciliation for its following occurrence.

## 8. Testing Strategy

Local tests are in `app/src/test` and run with `gradlew.bat :app:testDebugUnitTest`. The suite covers recurrence, lifecycle, spend, querying, reminder policy/reconciliation, backup validation/restore gating, presentation actions, Room behavior under Robolectric, theme selection, and WCAG AA contrast for core semantic color pairs. `gradlew.bat :app:lintDebug` and `gradlew.bat :app:assembleDebug` are release checks. WorkManager delivery, reboot broadcasts, notification permission UX, and SAF interaction still require a physical-device or emulator acceptance pass.

## 9. Development Conventions

- Domain code must remain independent of Android and Room.
- Date-only business rules use `LocalDate`; notification instants and time zones belong to future scheduling adapters.
- Recurrence APIs state whether the query boundary is inclusive or exclusive.
- Database schema changes must use explicit non-destructive migrations once persistence exists.

## 10. Known Constraints

The default currency/reminder values shown in Settings are suggestions and are not persisted. Insights do not convert currencies. Subscription creation does not synthesize a billing event; projected window spend begins after the user adds a BILLING event in detail. Navigation is intentionally local Compose state rather than a deep-link/navigation framework. WorkManager is intentionally used instead of exact alarms because V0.1 reminders are day/time utility reminders and do not justify exact-alarm permission; Android may defer execution under battery restrictions. `compileSdk` remains pinned to Android 34.
