# Subscription Tracker UI / UX Design Specification

**Version:** V0.1  
**Style Direction:** Precision Finance  
**Target:** Android-first subscription management app  
**Future Positioning:** Standalone subscription tracker + native module of AI Dashboard  
**Status:** Functional MVP baseline — must be usable as a standalone local-first app

---

## 1. Product positioning

The product is a general-purpose subscription and recurring-service management app. It should support ordinary memberships, SaaS products, AI services, cloud services, software licenses, trials, contracts, recurring payments, quota resets, and other time-based recurring events.

V0.1 is not designed as an “AI app”. The visual identity should first communicate:

> precise, reliable, calm, efficient, premium utility.

AI-related capabilities are treated as one class of structured service data, not as the visual identity of the product.

The interface should feel closer to a modern financial utility, budgeting tool, professional dashboard, or high-quality productivity app than to a typical Android Material You application.

### 1.1 V0.1 definition of a usable product

V0.1 is **not** a visual prototype. It is the first version that should be installable and usable as a user's real subscription ledger.

A user must be able to complete the following loop without any server dependency:

```text
Install app
→ add subscriptions
→ define billing / expiration / trial / quota-reset events
→ close and reopen app without losing data
→ receive reminders at the correct time
→ browse / search / filter subscriptions
→ see current-month and annualized spending
→ edit or cancel a subscription
→ export or back up local data
→ restore data on the same or another device
```

If any of the following are missing, V0.1 should not be considered functionally complete:

```text
persistent local storage
subscription CRUD
reliable date / recurrence calculation
notification scheduling
basic search / filter / sorting
basic spend calculation
manual quota / reset tracking
local export / backup / restore
error / empty-state handling
timezone-aware behavior
```

The product should remain fully usable offline after installation.

---

## 2. Design goals

### 2.1 Primary goals

1. Users should understand their current subscription state within 3–5 seconds after opening the app.
2. Amount, date, billing cycle, upcoming renewal, expiration, quota/reset state, and risk should be visually distinguishable at a glance.
3. High information density is allowed, but the screen must remain calm and readable.
4. The UI should scale from 5 subscriptions to 100+ subscriptions without changing the basic interaction model.
5. The V0.1 structure must leave stable extension points for future AI Dashboard functions.
6. V0.1 must provide a complete local-first CRUD → reminder → review → backup workflow.
7. Date, recurrence, and reminder behavior must be reliable enough for daily use rather than demo data.

### 2.2 Non-goals

V0.1 should not imitate:

- Material You's large rounded cards, oversized headings, dynamic-color-heavy surfaces, and pill-shaped controls.
- Generic Flutter template aesthetics.
- “AI style” visuals such as purple-blue gradients, neon glow, glassmorphism, sparkles, robot motifs, neural-network decorations, or excessive animated gradients.
- Crypto dashboards with excessive black backgrounds, green/red flashing values, or monospace typography everywhere.
- Banking apps that rely on corporate blue as the main identity.
- Mandatory account creation, cloud login, or server connectivity for core V0.1 usage.
- Automatic scraping of provider dashboards, bank transactions, email receipts, or Play Store subscriptions in V0.1.

---

## 3. Core visual direction — Precision Finance

The visual system should communicate precision through typography, alignment, rhythm, and data formatting rather than decorative effects.

Core characteristics:

- neutral light background;
- white or slightly lifted functional surfaces;
- dark, high-contrast typography;
- thin dividers instead of shadows;
- restrained accent color;
- tabular numerals for values and dates;
- compact radii;
- clear baseline alignment;
- consistent spacing;
- low-saturation semantic status colors;
- charts used only when they communicate useful information.

The default theme should be light. Dark mode can be added later using the same token system instead of designing a separate brand.

---

## 4. Design principles

### 4.1 Typography before cards

Information hierarchy should primarily be created through font size, weight, spacing, alignment, and dividers.

A new piece of information should not automatically become a card.

Prefer:

```text
Monthly spend                         ¥ 268.00
Forecast next month                  ¥ 302.00
────────────────────────────────────────────
Renewing in 7 days                   3
```

over three unrelated floating cards.

Cards should only be used when multiple pieces of information form a meaningful functional unit.

### 4.2 Numbers are first-class UI elements

Amounts, dates, remaining days, quotas, and percentages are core data.

All numeric displays should use tabular numerals where supported so columns remain visually stable.

Example:

```text
¥ 128.00
Sep 03
12 days
72%
```

Do not use a visibly “coding” monospace typeface for all data.

### 4.3 Use color as state, not decoration

The default UI should remain almost monochrome.

Color is mainly reserved for:

- selected controls;
- actionable emphasis;
- warning / due-soon states;
- success / completed states;
- destructive actions;
- quota health or critical status.

The accent color must not flood the page.

### 4.4 Information density should be adaptive

A user with 5 subscriptions should see comfortable spacing.

A user with 50 subscriptions should still be able to scan efficiently.

The list system therefore needs Compact and Comfortable density tokens internally even if V0.1 exposes only one mode.

### 4.5 Navigation must survive future growth

Do not create a bottom navigation item for every future function.

Global navigation represents long-lived user jobs; feature-specific information lives inside modules and service detail pages.

---

## 5. Color system

The values below are design tokens, not hard-coded per-screen colors.

### 5.1 Light theme baseline

```text
Background / Canvas        #F5F6F7
Surface Primary            #FFFFFF
Surface Secondary          #F0F2F4

Text Primary               #16181C
Text Secondary             #666C75
Text Tertiary              #90969F

Divider                    #E1E4E8
Border Strong              #CDD2D8

Accent Primary             #35675D
Accent Soft                #E6EFEC

Status Positive            #39705A
Status Warning             #A06A24
Status Critical            #A44848
Status Informative         #536A83
```

The exact accent hue can still be tuned during visual prototyping. The important constraint is: muted, professional, non-neon, and not generic corporate blue.

### 5.2 Color usage rules

Accent Primary should normally occupy less than roughly 10% of a screen.

Status colors should generally appear as:

- small indicators;
- 1–2 px bars;
- compact badges;
- icon accents;
- chart series;
- selected states.

Avoid entire red/yellow/green cards.

---

## 6. Typography

### 6.1 Recommended font stack

For Latin and numeric content:

```text
Inter Variable
```

For Simplified Chinese:

```text
Noto Sans SC / system CJK fallback
```

Use OpenType `tnum` / tabular numbers for amount and date fields wherever possible.

### 6.2 Type scale

```text
Display Amount        30–34sp / 600
Page Title            24sp / 600
Section Title         16–18sp / 600
Primary Row Text      15–16sp / 500
Body                  14–15sp / 400
Secondary             12–13sp / 400
Label / Metadata      11–12sp / 500
```

Avoid oversized 32–40sp page headings typical of some Material You layouts.

Page title hierarchy should not consume the upper third of the screen.

---

## 7. Geometry and spacing

Use a 4dp base spacing grid.

Recommended values:

```text
Screen horizontal padding     20dp
Major section gap             28–32dp
Section internal gap          12–16dp
List row vertical padding     14–16dp
Small control radius          6dp
Standard surface radius       10dp
Large grouped surface radius  12dp
Icon radius                   8–10dp
Divider                       1dp
```

Avoid defaulting to 20–28dp radii.

Pill shapes should only appear where the semantics truly fit, such as a compact filter token or status label.

Primary actions should normally be rectangular controls with moderate corner radii rather than giant floating pills.

---

## 8. Global information architecture

V0.1 uses a stable four-area structure:

```text
Overview
Subscriptions
Insights
Settings
```

Recommended Android bottom navigation:

```text
Overview     Subscriptions     Insights
```

Settings should initially be accessed through the top-right profile/settings action rather than occupying a permanent bottom-navigation slot.

This preserves one bottom-navigation slot for a future high-frequency workflow if usage data proves it is necessary.

Possible future candidates include:

```text
Activity
Calendar
Dashboard
Automation
```

They should not be reserved visually in V0.1.

---

## 9. Overview screen

The Overview screen is not a fixed collection of dashboard cards.

It is composed of modular sections.

Recommended structure:

```text
Top App Bar
↓
Spend Summary
↓
Upcoming Timeline
↓
Attention Required
↓
Optional Modules
↓
Recent / All Subscriptions Preview
```

### 9.1 Top app bar

Left:

```text
Overview
```

Right:

```text
Search
Add
Settings / Profile
```

Do not use a giant greeting such as “Good evening, Alex” as the primary visual hierarchy.

A small contextual line can be added later if it provides actual value.

### 9.2 Spend Summary

Primary amount:

```text
This month
¥ 268.00
```

Secondary values:

```text
Next 30 days        ¥ 302.00
Annualized          ¥ 3,420.00
Active              14
```

This section may use one restrained surface, but values should still be aligned in a financial-report style.

Avoid multiple colorful statistic cards.

### 9.3 Upcoming Timeline

Show the next meaningful events in chronological order.

Example:

```text
AUG 31   Cursor Pro       Quota reset
SEP 02   Netflix          ¥ 68 renewal
SEP 04   Domain           Expires
SEP 07   ChatGPT Plus     $20 renewal
```

The event type is represented by typography + a small semantic indicator.

This timeline is intentionally generic so future event types can be inserted without redesigning the screen.

V0.1 must support the following event categories:

```text
BILLING
QUOTA_RESET
EXPIRATION
TRIAL_END
CUSTOM
```

The event model should already reserve compatibility for:

```text
PRICE_CHANGE
PROMOTION_END
CONTRACT_NOTICE
```

These can remain hidden from the primary V0.1 UI until their workflows are implemented.

### 9.4 Attention Required

Only shown when necessary.

Examples:

```text
2 subscriptions renew within 3 days
1 trial ends tomorrow
1 subscription has an upcoming price change
AI quota is nearly exhausted
```

This section should use restrained status styling instead of a permanent warning banner.

### 9.5 Optional Modules

The home screen must support insertable modules.

V0.1 modules:

```text
Spend Summary
Upcoming Events
Attention Required
Subscription Preview
```

Future modules may include:

```text
Quota Summary
Provider Status
Promotion Watch
Usage Forecast
Unusual Spend
Sync Status
Team Activity
AI Service Summary
```

Implementation should therefore use a module registry / section model rather than one hard-coded monolithic screen.

---

## 10. Subscriptions screen

The Subscription screen is primarily a high-quality searchable list.

### 10.1 Header

```text
Subscriptions                           +
14 active
```

Below:

```text
Search
```

Then one compact filter row:

```text
All   Due soon   AI   Media   Software   More
```

Filters must remain compact and should not become large Material chips.

### 10.2 Subscription list row

Recommended row:

```text
[Icon]  ChatGPT Plus                   $20.00
        AI · Monthly                  Sep 07
        Next: renewal · 10 days
```

Optional state:

```text
        ● Quota resets in 3 days
```

Visual hierarchy:

1. service name;
2. price;
3. next meaningful event;
4. category / billing period;
5. secondary states.

Rows use dividers by default.

Do not wrap every subscription in a raised card.

### 10.3 Sorting

V0.1 should architecturally support:

```text
Next event
Price
Name
Recently added
Category
```

V0.1 must expose at least:

```text
Next event
Price
Name
Recently added
```

Filtering must include at minimum:

```text
All
Due soon
Category
Active / inactive
```

The internal query layer should remain extensible to additional filters.

---

## 11. Subscription detail screen

The detail screen is the most important future-proofing point.

Do not design it as a fixed set of fields.

Use a section / module architecture.

Recommended structure:

```text
Service Header
↓
Primary Status
↓
Billing
↓
Recurring Events
↓
Usage / Quota
↓
Plan / Contract
↓
Notes
↓
Integrations
```

Only sections with data are rendered.

### 11.1 Service Header

Example:

```text
ChatGPT Plus
OpenAI

Active
```

Possible actions:

```text
Edit
More
```

Provider branding should remain secondary.

Do not allow provider logos to dominate the screen.

### 11.2 Primary Status

Example:

```text
$20.00 / month

Next renewal
Sep 07, 2026
10 days
```

This is the highest-priority section.

### 11.3 Billing module

Fields may include:

```text
Price
Currency
Billing interval
Auto-renew
Payment method
Next charge
Current service period
```

### 11.4 Recurring Events module

Generic event display:

```text
Quota reset           Sep 01
Renewal               Sep 07
Promotion ends        Oct 07
Contract cancellation Nov 01
```

This must be generic enough for non-AI subscriptions.

### 11.5 Usage / Quota module

Not required for ordinary subscriptions.

Example:

```text
Weekly requests
216 / 300

Reset
Sep 01 · 3 days
```

Support multiple quota objects per subscription.

Future examples:

```text
Premium requests
Image generations
API credits
Compute hours
Storage
Team seats
```

### 11.6 Future module injection

The detail page should later be able to add:

```text
Provider Status
Price History
Promotion History
Usage History
AI Models
API Balance
Connected Account
Sync Source
Team Access
Conversation / Project Integration
```

without changing the overall page shell.

---

## 12. Add / Edit subscription flow

The creation flow should optimize manual entry rather than ask users to configure every possible field.

### 12.1 Step structure

Preferred V0.1 flow:

```text
Basic
Billing
Dates & Events
Reminder
Optional Details
```

This may be rendered as one vertically scrolling form rather than a multi-page wizard.

### 12.2 Basic

```text
Name
Provider
Category
Icon
```

### 12.3 Billing

```text
Price
Currency
Billing cycle
Auto-renew
```

### 12.4 Dates & Events

The UI should expose:

```text
Next renewal
Expiration
Trial end
Custom recurring event
```

AI-specific quota settings are optional advanced fields in V0.1 and are entered manually.

V0.1 must support at least:

```text
quota name
current usage or remaining amount
optional maximum / total
reset date
recurrence interval
reminder before reset
```

Provider templates and automatic quota retrieval are deferred.

### 12.5 Progressive disclosure

Do not show 20 fields at once.

Advanced details appear through:

```text
+ Add another event
+ Add quota
+ Add contract detail
+ Add note
```

This design pattern is essential for preserving simplicity as the data model expands.

---

## 13. Insights screen

V0.1 should remain conservative.

The purpose is to answer useful questions rather than display decorative charts.

Recommended sections:

```text
Monthly spend
Annualized spend
Category breakdown
Upcoming 30-day commitments
Price distribution
```

Potential future insights:

```text
Spend trend
Price increases
Unused subscriptions
Quota efficiency
Cost per use
Provider spend
AI spend vs general subscriptions
Team spend
Forecast
```

### 13.1 Chart style

Charts should use:

- thin axes or no unnecessary axes;
- muted series colors;
- direct labels where possible;
- no gradients;
- no 3D;
- no ornamental donut charts when a table is clearer.

A number + comparison may be preferable to a chart for small datasets.

---

## 14. Search and command behavior

Search should be treated as a global capability from the beginning.

The data layer should allow search across:

```text
service name
provider
category
notes
event type
tags
plan
```

A future command/search surface may evolve into:

```text
Search subscriptions
Jump to provider
Add subscription
Show renewals this month
Show AI services
```

The V0.1 UI does not need a command palette, but its navigation should not block one.

---

## 15. Component system

V0.1 should define custom product components instead of directly exposing default Material components.

Suggested component families:

```text
AppTopBar
BottomNav
MetricGroup
MetricRow
SubscriptionRow
EventRow
StatusIndicator
CompactTag
SectionHeader
InlineAction
PrimaryButton
SecondaryButton
TextField
SelectField
DateField
AmountField
FilterStrip
ProgressMetric
EmptyState
InlineNotice
ChartContainer
```

Jetpack Compose may still be used as the implementation framework, but Material components should be treated as low-level primitives rather than the visible design language.

---

## 16. Interaction and motion

Motion should be restrained and functional.

Recommended:

```text
screen transition       160–220 ms
row expand/collapse     160–200 ms
state change            120–180 ms
number change           subtle crossfade
```

Avoid:

- bouncing cards;
- continuous ambient animation;
- glowing buttons;
- animated gradient backgrounds;
- excessive spring physics;
- “AI thinking” decorative animations.

Use haptics sparingly for:

```text
successful add
destructive confirmation
important toggle
```

---

## 17. Empty states

Empty states should be practical.

Example:

```text
No subscriptions yet

Add your first subscription to track
renewals, expiration dates and recurring costs.

[ Add subscription ]
```

Avoid cartoon illustrations unless a later brand system explicitly requires them.

A small geometric or typographic illustration may be acceptable.

---

## 18. Notification / reminder system

Notifications are a required V0.1 function, not only a visual treatment.

The reminder engine must persist schedules locally and reconstruct them after app restart and device reboot where Android permits.

At minimum, users must be able to configure reminders for:

```text
billing / renewal
expiration
trial end
quota reset
custom event
```

Recommended preset offsets:

```text
same day
1 day before
3 days before
7 days before
custom
```

V0.1 should support multiple reminder offsets per event if implementation cost remains reasonable; otherwise one reminder per event is acceptable for the first release, but the data model should support multiple reminders.

When notification permission is denied, the app must clearly show that reminders are disabled and provide a direct route to the relevant system/app settings rather than silently failing.

Reminder design should remain consistent with the financial-utility tone.

Examples:

```text
Cursor Pro renews tomorrow
$20.00 · Sep 03
```

```text
Claude quota resets tonight
Current usage: 82%
```

```text
Trial ends in 2 days
Notion AI · Sep 05
```

Notifications should emphasize:

1. what happens;
2. when;
3. financial / quota impact;
4. optional action.

---

## 19. Iconography

Use a consistent outline icon family with restrained stroke weight.

Recommended style:

- 1.5–2px equivalent strokes;
- simple geometry;
- minimal filled icons;
- no emoji as functional icons;
- service logos only where identity matters.

Avoid generic AI symbols such as stars, sparkles, brain icons, or magic wands for AI services.

“AI” is a category or capability, not the visual brand.

---

## 20. Responsive considerations

Although Android phone is the first target, layout tokens must support:

```text
phone portrait
phone landscape
foldable
tablet
desktop / future multiplatform client
```

On larger layouts, do not simply stretch mobile cards.

Future tablet / desktop layout may use:

```text
Subscription List | Detail Pane
```

or:

```text
Navigation Rail | Content | Context Panel
```

The domain modules should remain identical.

---

## 21. Accessibility

Minimum requirements:

- touch targets approximately 44–48dp;
- text contrast compliant with WCAG AA where applicable;
- semantic labels for icons;
- no state communicated only by color;
- Dynamic Type / system font scaling should not break primary workflows;
- dates and amounts should remain understandable under large-text settings;
- destructive actions require explicit confirmation.

---

## 22. Future AI Dashboard integration

The subscription app should remain independently understandable even if AI Dashboard never exists.

At the same time, its architecture should expose subscription data as a reusable product module.

Conceptual relationship:

```text
AI Dashboard
│
├── Subscription Core
│   ├── Billing
│   ├── Recurring Events
│   ├── Expiration
│   ├── Reminders
│   └── Spend
│
├── Quota Tracker
├── Provider Status
├── Promotion Tracker
├── Conversation Sync
├── Project Sync
└── Config / Skill / MCP Migration
```

UI rule:

> AI Dashboard capabilities should be injected as contextual modules rather than turning the subscription app into an AI-themed dashboard.

Examples:

ChatGPT detail page can gain:

```text
Quota
Provider status
Model access
Promotion
Account connection
```

Netflix detail page remains:

```text
Billing
Renewal
Plan
Reminder
```

The common shell stays the same.

---

## 23. Data-driven UI requirement

Several screens should render from structured section definitions.

Conceptually:

```text
OverviewModule[]
SubscriptionDetailModule[]
InsightModule[]
RecurringEvent[]
```

This enables future functionality to add a module without requiring major screen rewrites.

Recommended long-term module properties:

```text
id
type
priority
visibility
data
status
actions
source
lastUpdatedAt
```

V0.1 does not need a plugin system, but UI code should avoid giant `if provider == ...` page implementations.

---

## 24. V0.1 screen and workflow scope

The first implementation must include:

```text
1. Overview
2. Subscription List
3. Subscription Detail
4. Add Subscription
5. Edit Subscription
6. Archive / cancel / delete flow
7. Insights
8. Settings
9. Reminder configuration
10. Export / backup / restore
11. Permission guidance
12. Empty / loading / error states
```

The product must support the following functional workflows end-to-end:

```text
Create subscription
Read subscription
Edit subscription
Archive / cancel subscription
Delete subscription with confirmation
Create / edit / remove recurring event
Create / edit / remove quota
Search / filter / sort
Schedule and receive reminder
Review current-month and annualized spend
Export local data
Restore local data
```

Core user workflow:

```text
Launch
→ understand current spending / upcoming events
→ inspect subscription
→ add or edit subscription
→ configure reminder
→ review spend
```

---

## 25. Recommended V0.1 navigation map

```text
App
│
├── Overview
│   ├── Spend Summary
│   ├── Upcoming Events
│   ├── Attention Required
│   └── Subscription Preview
│
├── Subscriptions
│   ├── Search / Filter
│   ├── Subscription Detail
│   │   ├── Billing
│   │   ├── Events
│   │   ├── Quota
│   │   ├── Notes
│   │   └── Future Modules
│   └── Add / Edit
│
├── Insights
│   ├── Spend
│   ├── Categories
│   └── Forecast
│
└── Settings
    ├── Currency
    ├── Reminder Defaults
    ├── Appearance
    ├── Data / Backup
    └── Future Sync
```

---

## 26. Design constraints for implementation

The implementation team should explicitly avoid these shortcuts:

```text
Default Material 3 visual styling as final UI
Dynamic Color as the default brand identity
Every section implemented as ElevatedCard
Large FAB as the only add-entry point
Hard-coded dashboard widgets
Provider-specific detail screens
Global use of pill buttons
Large decorative gradients
AI-themed placeholder illustrations
```

Jetpack Compose is acceptable and recommended for Android implementation.

The requirement is not “avoid Material technology”; it is:

> do not let the framework's default component aesthetics become the product's visual identity.

Create a small internal design system above Compose primitives.

---

## 27. Visual acceptance criteria

A V0.1 screen should pass the following review questions:

1. If all logos are removed, does the interface still look deliberate and branded?
2. Is the most important number/date obvious without using bright colors?
3. Can the same screen handle twice as much data?
4. Is a card present because it groups information, or merely because cards are easy to implement?
5. Can a future module be inserted without changing navigation?
6. Does an AI subscription look like a sophisticated subscription rather than a different app?
7. Does the screen still look good in grayscale?
8. Are amount and date columns visually stable?
9. Can users distinguish renewal, expiration, reset, and warning states without opening detail pages?
10. Does the screen resemble a serious consumer utility rather than an AI-generated UI template?

---

## 28. Recommended next design deliverables

After this specification, the next design pass should create high-fidelity mobile screens for the following states using one consistent sample dataset:

```text
01 Overview — normal
02 Overview — attention required
03 Subscription list — 15+ items
04 Subscription detail — normal membership
05 Subscription detail — AI/SaaS with multiple quotas
06 Add subscription — simple
07 Add subscription — advanced recurring events
08 Insights
09 Empty state
10 Dark theme proof-of-concept
```

The normal membership and AI/SaaS detail screens are particularly important because they validate whether the module system can support both simple and complex subscriptions without creating two separate products.

---

## 29. V0.1 design decision summary

The V0.1 visual identity is **Precision Finance**:

> neutral, information-dense, precise, restrained and durable.

The product should rely on:

```text
typography
alignment
tabular numerals
thin dividers
structured modules
small radii
semantic status color
```

rather than:

```text
large rounded cards
gradients
glass effects
oversized titles
decorative AI imagery
framework-default UI
```

The most important architectural design decision is that both the Overview screen and Subscription Detail screen are module-driven.

This allows the product to begin as a clean standalone subscription tracker while later accepting AI Dashboard capabilities such as quota tracking, provider status, promotions, account sync, and collaboration without destroying the original information architecture.


---

## 30. Functional V0.1 scope

The following capabilities are release requirements.

### 30.1 Subscription CRUD

A subscription record must support:

```text
id / UUID
name
provider
category
icon / optional custom visual identifier
status
price
currency
billing interval
billing interval count
auto-renew
start date
optional next billing date
optional expiration date
optional trial end date
notes
createdAt
updatedAt
archivedAt / nullable
```

Status should support at minimum:

```text
ACTIVE
CANCELLED_PENDING_EXPIRY
EXPIRED
PAUSED
ARCHIVED
```

Deleting and archiving are separate operations.

Deleting is destructive and requires explicit confirmation.

Archiving preserves history and should be the preferred action for old subscriptions.

### 30.2 Recurring event CRUD

Each subscription may contain zero or more recurring events.

Minimum event fields:

```text
id / UUID
subscriptionId
type
title
nextOccurrence
recurrenceRule
timezone
enabled
notes
createdAt
updatedAt
```

A recurring event is the common mechanism for billing, quota resets, expiration-like reminders, and future event types.

Do not implement renewal logic as a single `nextRenewalDate` field that the rest of the UI depends on.

---

## 31. Date and recurrence engine

Date correctness is a P0 requirement.

### 31.1 Required recurrence types

V0.1 must support:

```text
one-time
daily
weekly
monthly
quarterly
yearly
custom N days
custom N weeks
custom N months
custom N years
```

### 31.2 Calendar rules

The date engine must define behavior for:

```text
Jan 31 → February
Feb 29 → non-leap year
months with 28 / 29 / 30 / 31 days
year boundaries
device timezone changes
daylight-saving transitions
manual system-clock changes
```

Recommended monthly rule:

> preserve the requested day-of-month where possible; otherwise use the last valid day of that month.

Example:

```text
Jan 31 → Feb 28 / 29 → Mar 31
```

The recurrence engine must not drift into the 28th permanently after February.

### 31.3 Date-only vs time-based data

Billing dates, expiration dates, and quota-reset calendar dates should be represented as calendar dates unless a specific time is meaningful.

Notification execution times are separate from the logical event date.

This prevents timezone conversion from changing “Sep 1” into “Aug 31”.

---

## 32. Spend calculation

V0.1 must calculate useful spending information from locally entered subscriptions.

### 32.1 Required metrics

```text
current calendar month committed spend
next 30 days expected spend
annualized recurring spend
active subscription count
category subtotal
```

### 32.2 Multi-currency behavior

V0.1 should allow arbitrary currency codes per subscription.

Automatic FX conversion is **not required**.

If multiple currencies exist, the UI must not silently sum them into a fake single-currency number.

Acceptable V0.1 behavior:

```text
Monthly spend
CNY ¥168
USD $40
HKD $58
```

A user-selected manual exchange rate or live FX conversion can be added later.

---

## 33. Manual quota tracking

V0.1 supports manual quota tracking as an optional module.

Quota fields should support:

```text
id
subscriptionId
name
unit
used / optional
remaining / optional
limit / optional
reset event
warning threshold / optional
updatedAt
```

The model must support:

```text
used / total
remaining only
percentage only
unlimited / informational quota
```

Examples:

```text
Fast requests       216 / 300
API credit          $7.20 remaining
Image generations   82%
```

V0.1 does not automatically retrieve provider quota data.

---

## 34. Local persistence

V0.1 is local-first.

Recommended Android implementation:

```text
Room
Repository layer
Domain models
Use cases / services
```

The UI must not read or write Room entities directly.

Recommended dependency direction:

```text
Compose UI
   ↓
ViewModel / Presentation
   ↓
Domain / Use Cases
   ↓
Repository
   ↓
Room
```

All primary user data must survive:

```text
app restart
process death
device reboot
app update
```

Schema migrations must be explicitly versioned.

Development builds may use destructive migration only before the first real user dataset is relied upon.

---

## 35. Backup, export, and restore

Because V0.1 has no mandatory cloud account, user-controlled backup is a required safety feature.

### 35.1 Required V0.1 operations

```text
Export data
Import / restore data
```

Preferred export format:

```text
JSON
```

Optional:

```text
CSV for subscription summary
```

JSON should preserve the complete structured model, including events, quotas, reminder configuration, and IDs.

### 35.2 Backup metadata

Export files should include:

```text
schemaVersion
appVersion
exportedAt
subscriptions
events
quotas
settings required for restoration
```

Import must validate schema compatibility before overwriting user data.

Recommended import behaviors:

```text
Replace existing data
Merge by UUID
```

If merge is too costly for V0.1, implement Replace first and clearly state the behavior before confirmation.

Never silently destroy existing data during restore.

---

## 36. Reminder scheduling and Android lifecycle

The app must account for Android background execution restrictions.

Implementation may use:

```text
AlarmManager
WorkManager
or a hybrid strategy
```

The technical choice should be based on reminder precision requirements.

For calendar-day reminders that do not require second-level precision, exact alarms should not be requested without a concrete need.

The reminder system must account for:

```text
device reboot
app update
timezone change
notification permission change
battery optimization behavior where relevant
```

The application should run a schedule reconciliation process when opened to repair missing or stale scheduled jobs.

---

## 37. Search, filter, and sort behavior

V0.1 search should operate locally and immediately.

Minimum search fields:

```text
name
provider
category
notes
```

Minimum filters:

```text
active / inactive
due soon
category
currency
```

Minimum sorting:

```text
next event
price
name
recently added
```

“Due soon” should be based on a configurable or clearly defined threshold; recommended default is 7 days.

---

## 38. Cancellation, expiration, and historical records

Cancellation must not be equivalent to deletion.

A user may cancel auto-renew today while retaining access until the paid period ends.

Support:

```text
auto-renew = false
status = CANCELLED_PENDING_EXPIRY
service end date = optional
```

After the service end date:

```text
status = EXPIRED
```

Historical subscriptions remain available in archive / inactive filters and continue to contribute to historical records if history features are added later.

V0.1 does not need complete historical spend analytics, but it must avoid destroying the data required to build them later.

---

## 39. Settings required for V0.1

Minimum settings:

```text
display language / system default
default currency
default reminder offset
default notification time
week start preference if relevant
appearance: light / dark / system if dark theme is implemented
data export
data import / restore
notification status
about / app version
```

A network account or server configuration is not required in V0.1.

A disabled or “coming soon” cloud-sync setting should generally not be shown unless it communicates useful migration planning to testers.

---

## 40. Permissions and privacy

V0.1 should request the minimum permissions necessary.

Expected permissions may include:

```text
POST_NOTIFICATIONS
```

File access should use Android's system document picker / Storage Access Framework where possible rather than broad storage permissions.

The core app should not require:

```text
contacts
SMS
call logs
accessibility service
VPN
location
microphone
camera
bank account access
email access
```

No analytics or crash reporting SDK should be added by default without an explicit product decision.

---

## 41. Offline behavior

All core V0.1 workflows must work without internet access:

```text
create / edit subscriptions
browse
search
calculate spend
calculate dates
schedule local reminders
export / import local data
manual quota updates
```

Internet-dependent features must never block app startup.

This rule creates a clean boundary for future AI Dashboard connectivity.

---

## 42. Error and edge-state requirements

V0.1 must explicitly handle:

```text
invalid date combination
zero or negative price
unsupported currency text
missing required name
next occurrence earlier than allowed
restore file malformed
restore schema unsupported
notification permission denied
reminder scheduler failure
database migration failure
empty subscription list
no events in upcoming window
no data for insights
```

User-facing errors should explain what can be corrected.

Technical stack traces, database errors, or exception messages must not be shown directly.

---

## 43. Performance baseline

The design should remain responsive with at least:

```text
100 subscriptions
500 recurring events
200 quota records
```

Expected local interactions such as list opening, filtering, and search should feel immediate on a typical modern Android device.

Do not introduce a server dependency to solve local query performance.

---

## 44. V0.1 release acceptance criteria

V0.1 is ready for real use only when all P0 checks pass.

### 44.1 P0 functional acceptance

```text
[ ] Add a monthly subscription and persist it
[ ] Edit price / currency / cycle
[ ] Archive and restore visibility through filters
[ ] Delete with explicit confirmation
[ ] Add one-time and recurring events
[ ] Correctly calculate monthly recurrence across short months
[ ] Add manual quota and reset schedule
[ ] Schedule notification reminder
[ ] Reconstruct reminders after app restart / reboot as applicable
[ ] Search by subscription name
[ ] Filter due-soon subscriptions
[ ] Sort by next event
[ ] Calculate monthly / next-30-day / annualized spend
[ ] Never sum different currencies as one amount
[ ] Export full JSON backup
[ ] Restore valid JSON backup
[ ] Reject malformed / incompatible backup safely
[ ] Work offline
[ ] Handle denied notification permission visibly
[ ] Preserve data through normal app upgrade / schema migration
```

### 44.2 P0 UX acceptance

```text
[ ] First subscription can be created without reading documentation
[ ] Primary upcoming event is visible from list / overview
[ ] Renewal, expiration, trial, and quota reset are visually distinguishable
[ ] No core screen depends on a network connection
[ ] Empty and error states are intentional
[ ] Destructive actions require confirmation
[ ] No default Material 3 card-wall appearance
[ ] AI-specific data does not dominate ordinary subscription workflows
```

---

## 45. Explicitly deferred from V0.1

The following features are intentionally not part of the V0.1 release gate:

```text
account system
cloud sync
self-hosted server
team collaboration
bank / payment-account integration
Gmail receipt parsing
Google Play subscription auto-import
automatic provider quota scraping
automatic provider login
live foreign-exchange rates
promotion monitoring
provider status monitoring
price-change crawling
conversation sync
project sync
Skill / MCP / plugin migration
AI recommendations
predictive spend analysis
full subscription history analytics
desktop / iOS client
```

These are future capabilities, not missing V0.1 requirements.

---

## 46. Suggested V0.1 implementation priority

Development order should follow dependency risk rather than screen order:

```text
P0-1 Domain model + Room schema
P0-2 Date / recurrence engine + unit tests
P0-3 Subscription / event / quota CRUD
P0-4 Reminder scheduler + permission handling
P0-5 Overview calculations
P0-6 List search / filter / sort
P0-7 Export / import / restore
P0-8 Precision Finance UI polish
P0-9 Edge-state / migration / reboot testing
P0-10 APK dogfooding
```

The visual design should be developed in parallel, but recurrence correctness, persistence, and backup safety have priority over animation or decorative polish.

---

## 47. V0.1 final product definition

V0.1 should be described as:

> **A local-first Android subscription manager that can reliably record recurring services, calculate upcoming costs and dates, track manually entered quota resets, notify users before important events, and safely export or restore its data.**

It should already be good enough for daily personal use.

The AI Dashboard integration remains an architectural extension point, not a dependency for the V0.1 product to function.
