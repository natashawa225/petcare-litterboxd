# INDIVIDUAL_REPORT_AUDIT.md — Evidence Behind the Individual Development Report

Working document only; not part of the submitted report. Every claim below was checked against the source at `/Users/natashawilfrid/Downloads/litterboxd/Litterboxd` (branch `main`), not assumed from feature names or prior drafts.

## Features inspected

Onboarding (`ui/onboarding/*`), Home dashboard and bottom navigation (`ui/home/HomeActivity.java`, `ui/nav/BottomNavHelper.java`), daily check-in (`checkin/*`, `ui/checkin/*`), health alerts and reading suggestions (`health/*`, `ui/health/*`), care tasks and reminders (`ui/tasks/CareTasksActivity.java`, `data/CareTaskSetup.java`, `reminders/*`), diary (`ui/diary/*`), and profile/settings (`ui/profile/*`). Persistence throughout is `SQLiteOpenHelper` + one DAO per table (`data/dao/*`), schema v7.

## Confirmed limitations (verified in code, not inferred)

1. **The reminder system never prompts the daily check-in itself.** `grep`-ing `reminders/` for any reference to `checkin`/`CheckIn` returns nothing. `CareReminderScheduler` only arms alarms for `care_tasks`, and `TodayListReminderReceiver` (`reminders/TodayListReminderReceiver.java`) only notifies when a cat "still has today's checklist open" (unfinished care tasks) — it never checks `DailyCheckinDao.getCheckin(catId, today)`. The one proactive engagement mechanism the app has therefore ignores the feature the rest of the report treats as the habit-forming core of the app.
2. **Check-in history is actively deleted after 90 days.** `DailyCheckinDao.RETENTION_DAYS = 90`, and every save calls `saveCheckinAndTrim(...)` → `deleteOlderThan(catId, cutoff)` (`data/dao/DailyCheckinDao.java:19, 92-104`, invoked from `CheckInActivity.java:401-402`). This is a real, active deletion on every save, not a dormant constant. It caps the Diary's care-history line and any future longitudinal pattern feature to a rolling three-month window.
3. **Appetite is logged twice, in two places that never see each other.** The check-in tree (`res/raw/checkin_tree.json`, category `appetite_water` → group `appetite`, values `less`/`normal`/`more`) and the Tasks tab's feeding-task completion checkbox (`care_task_logs`, via `CareTaskDao`) both record essentially the same real-world fact. `HealthRuleEvaluator.evaluate(...)` only consumes check-in answers and weight points (`health/HealthRuleEvaluator.java:36-50`) — it never queries `CareTaskDao`. The two records can silently disagree (task marked fed, check-in says "less") with nothing in the app reconciling or even surfacing that.
4. **Accessibility labelling is sparse.** Only 12 of the 44 layout files under `res/layout/` use `contentDescription`, and only 8 use `importantForAccessibility`. Icon-driven surfaces (bottom navigation, the "pet the cat" header, health-alert rows) are the most likely to be affected, though this was checked at the attribute-count level, not screen-by-screen with a screen reader.
5. **The "more info" health reading depends entirely on a live, uncontrolled third-party page.** `HealthArticleClient.find()` (`health/HealthArticleClient.java`) returns an empty list on any `IOException`, so a layout change on `icatcare.org` degrades silently to "no matching articles" rather than failing visibly — already noted in the group report, repeated here only because it bears on reliability as the app scales to more alert types.
6. **Two already-wired but unused pieces remain:** `FileStorageHelper.exportCsv` is not called from any screen, and the `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` permissions are declared but `LocationHelper` is not used by any Activity. Both are genuinely partial (code exists, no UI entry point), not merely planned.

## Checked and found *not* to be a problem

- Cat deletion does correctly cancel that cat's reminders (`CatProfileActivity.java:562`, `CareReminderScheduler.cancelForCat`) before cascading the delete — no leaked alarms.
- `DailyCheckinDao` methods are consistently scoped by `catId` (`saveCheckin`, `getAnswersByDateForCat`, `getCheckinDatesForCat`, etc.), so multi-cat check-in data does not leak between cats.
- The owner's profile photo is a real, user-pickable image (`EditProfileActivity` uses `ActivityResultContracts.GetContent()`, the same mechanism as `AddEditCatActivity`'s cat-photo picker) — an earlier working assumption that owner avatars were a fixed illustrated set only was checked and found to be wrong, so it is not used as a finding in the report.

## Strongest future-development opportunities (feed the final report)

Selected for being technically grounded in schema/API that already exists, rather than requiring new infrastructure:

- **Context-aware check-in reminder timing**, built on the fact that every check-in row already carries real `created_at`/`updated_at` timestamps (currently unused for anything but display; `setLoggedAt` exists only for seeding sample data) and that `TodayListReminderScheduler` already demonstrates the exact AlarmManager + `AppPrefs`-driven pattern needed. Directly resolves limitation 1.
- **Cross-referencing check-in answers with care-task completion** inside `HealthRuleEvaluator`, so a rule can require (or be dampened by) corroboration from both signals before alerting. Directly resolves limitation 3.
- **Aggregating check-ins older than 90 days into monthly rollups instead of deleting them outright**, extending `deleteOlderThan` into a summarise-then-delete step. Directly resolves limitation 2 and is the technical basis for a longer-horizon "year in care" retrospective.

## Distinguishing confirmed problems from potential future limitations

Confirmed (observed directly in current code): items 1–6 above. Potential/forward-looking (not a present defect, but a constraint the proposals must respect): the app has no backend, so any cross-device or cross-owner context-aware feature would need to stay on-device; this is stated as a design constraint in the report, not a bug.

## Word counts

`INDIVIDUAL_REPORT.md` body (excluding the Mermaid block): 1,102 words, within the requested 900–1,100 range and above the 800-word minimum.
