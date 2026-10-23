# Litterboxd — Audit Report (Phase 1: audit only, no code changed)

Audited: 2026-10-24, working tree on `main` (uncommitted state, HEAD `310d553`).
Reference: `course_reference.md` (found at `~/Downloads/course_reference.md`; the brief says `docs/course_reference.md`, which does not exist in the repo — consider committing it there).

Every finding has an ID so you can approve or strike items individually. "Verified" means I observed it running; everything else is from reading the code.

**Concurrent edits:** while I was auditing, someone changed the launcher icon (`res/mipmap-*/app_icon.png` added, `AndroidManifest.xml` icon attributes changed at 02:49). Those changes are included below. If more edits land before Phase 2, I will re-read the affected files first.

---

## A. BROKEN / BUGS

### A0. Build, lint, tests

Run with JDK 17 (`JAVA_HOME=~/Library/Java/JavaVirtualMachines/jbr-17.0.14/...`). The default JDK 25 on this Mac fails Gradle 8.0 immediately with the message `25.0.3`; Android Studio's bundled JBR is also 25. Team members building from the terminal need JDK 17.

| Command | Result |
|---|---|
| `./gradlew assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew testDebugUnitTest` | 8 tests, 0 failures (`AgeUtilsTest` 5, `ValidatorsTest` 3) |
| `./gradlew lint` | 0 errors, 57 warnings: UnusedResources 25, SetTextI18n 9, Overdraw 7, IconLauncherShape 5, GradleDependency 5 (ignore, versions are frozen), CustomSplashScreen, ObsoleteSdkInt, PluralsCandidate, IconDipSize, IconDuplicatesConfig |
| `connectedDebugAndroidTest` | **Not run.** It uninstalls/reinstalls the app on the emulator and `CatDaoTest` writes to the real app database, which would wipe the data currently on your emulator. Run it yourself when that's acceptable. |

### Crashes / likely crashes

**A1. Notification permission dialog can never appear — `POST_NOTIFICATIONS` is not declared**
- `AndroidManifest.xml` (no `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>`), `HomeActivity.java:161-170`
- On API 33 with targetSdk 33, requesting an undeclared runtime permission is silently auto-denied: no dialog. `notification_permission_asked` is then set to `true`, so it is never retried. Feature 2's reminders would never be allowed to post.
- Fix: add the `<uses-permission>` line. Optionally override `onRequestPermissionsResult` to show a Toast on denial (the course's taught flow). No crash today, but the flow is broken.
- Risk: low.

**A2. "Edit cat" creates a duplicate cat instead of editing**
- `CatProfileActivity.java:75-79` passes `cat_id`; `AddEditCatActivity.java` never reads it (no `getIntent()` at all). It always inserts a new cat (`:208`) and then routes into onboarding step 3 (`:216-219`) with "Step 2 of 3 / Add Cat Profile" titles. Saving from there clears the task and lands on Home with two cats.
- Fix: in `onCreate`, read `cat_id`. If it isn't `-1`, load the cat on the executor, pre-fill the fields, switch the title to `@string/title_edit_cat` (already exists, unused) and hide the step indicator. On save, call `catDao.updateCat()` and `finish()` back to the profile instead of going to `FeedingScheduleActivity`. Delete the old photo if it was replaced (see A20).
- Risk: medium (largest change in the plan, but confined to one Activity).

**A3. Cat weight is silently saved as 4.0 kg when the input is empty or uses a comma**
- `AddEditCatActivity.java:153-157`: `double weight = 4.0;` then `Double.parseDouble` fails on `""` or `"4,2"` → the exception is swallowed → 4.0 passes validation and is saved. Many European keyboards produce `,` for `numberDecimal`.
- Fix: one helper (e.g. `Validators.parseWeight(String)` returning `Double` or `null`) that does `s.trim().replace(',', '.')`. Show `error_invalid_weight` when it is `null` or out of range. Reuse it in the add-weight dialog (A4). Add unit tests for `"4,2"`, `"4.2"`, `""` and `"abc"`.
- Risk: low.

**A4. Add-weight dialog ignores invalid input silently and skips range validation**
- `CatProfileActivity.java:197-217`: `"4,2"` → `NumberFormatException` swallowed, so the dialog just closes and nothing happens. Any value > 0 is accepted (e.g. 400 kg), unlike onboarding's 0.5–15 kg rule.
- Fix: use the A3 helper plus `Validators.isValidWeight`, and show a Toast with `error_invalid_weight` on failure.
- Risk: low.

**A5. Possible NullPointerException when adding a weight**
- `CatProfileActivity.java:211`: `currentCat.setWeight(...)` on the background thread. `currentCat` is assigned from another background task (`:96`) and may be `null` if the dialog is confirmed before the load finishes, or after the cat was deleted.
- Fix: inside the executor, re-load with `catDao.getCatById(catId)`. If it's `null`, skip the update.
- Risk: low.

**A6. Fields read from background threads (data race)**
- `HomeActivity.java:94-108`: `allCats` and `currentCat` are written on the executor thread and read on the UI thread (`showCatSwitcherDialog`, the profile button). `CatProfileActivity.java:96` has the same issue with `currentCat`.
- `AddEditCatActivity.java:178-204` and `FeedingScheduleActivity.java:154-166` read Spinner, RadioGroup, EditText and Button text from the worker thread. The course rule is "only the UI thread touches views".
- Fix: read all view values into local `final` variables on the UI thread before `diskIO().execute(...)`. Load into locals in the worker, then assign the fields inside the `mainThread()` block.
- Risk: low.

### Navigation / back stack

**A7. Back during onboarding creates duplicate cats**
- Flow: Welcome → OwnerName → AddEditCat (calls `finish()` after insert) → FeedingSchedule. Back on FeedingSchedule returns to OwnerName. Continue → AddEditCat → a **second** copy of the cat is inserted.
- Fix (minimal, no new screens): FeedingSchedule's Back should go to Home as if Skip were pressed, or simply block Back once the cat exists. Simplest: override `onBackPressed()` in `FeedingScheduleActivity` to call `finishOnboarding()`. This is the course's back-stack material. Alternative: don't `finish()` AddEditCat, and pass the existing `cat_id` back (more complex).
- Risk: low.

**A8. Killing the app after step 2 duplicates cats on the next launch**
- The cat is inserted in step 2, but `onboarding_complete` is only set in step 3. Splash then routes to Welcome (`SplashActivity.java:28`) and the user re-onboards, adding another cat. `selected_cat_id` still points at the first one.
- Fix: in Splash, if cats exist but onboarding is incomplete, open `FeedingScheduleActivity` with the first cat's id (resume where they left off), or treat "has cats" as onboarded. One-line routing change.
- Risk: low.

**A9. Deleting the last cat lands on a dead-end Home**
- `CatProfileActivity.java:229-245`: when no cats remain, it sets `onboarding_complete=false` but still opens Home ("No cat selected", with no way to add a cat).
- Fix: in that branch, start `SplashActivity` with `NEW_TASK|CLEAR_TASK` instead (Splash then routes to Welcome).
- Risk: low.

**A10. Back from Home exits correctly; Reset clears the task correctly.** No change needed: Home is started with `NEW_TASK|CLEAR_TASK` from FeedingSchedule and Splash, and Reset restarts Splash with the same flags.

### Lifecycle / rotation

**A11. AddEditCat loses the photo and birthday on rotation**
- `AddEditCatActivity.java:50-52, 62, 111, 119`: `pickedImageUri`, `selectedBirthdayMs` and the birthday button text are plain fields, so after rotation the photo preview resets and the photo is **not saved**, and the birthday silently becomes "today". The radio buttons, spinners and EditTexts survive rotation (they have ids).
- Fix: `onSaveInstanceState` → `putParcelable("photo_uri", ...)` and `putLong("birthday", ...)`. Restore in `onCreate` and re-render the button text and the preview (via the downscaled decode, see A19).
- Risk: low.

**A12. FeedingSchedule loses every edit to meal rows on rotation**
- `FeedingScheduleActivity.java:73-83, 88-145`: the rows are built in code without ids. On rotation the spinner restores and `onItemSelected` rebuilds the rows with defaults, discarding labels, times and portions.
- Fix: in `onSaveInstanceState`, save three arrays (labels, hour/minute, portions) and re-apply them after `buildMealRows` on restore. Keep `MealRowView` as is.
- Risk: low–medium.

**A13. Birthday defaults to "today" and future dates are allowed**
- `AddEditCatActivity.java:110-111, 115-120`: if the user never opens the picker, the cat is saved as 0 months old (life stage "Kitten"). The DatePicker has no max date.
- Fix: start `selectedBirthdayMs = null` and require a pick when "Exact Birthday" is chosen (show an error on the button/Toast). Add `dialog.getDatePicker().setMaxDate(System.currentTimeMillis())`.
- Risk: low.

**A14. Listeners and executors**
- `LocationHelper` is not used by any Activity, so no updates are left registered. Its `stop()` is correct for future callers.
- `AppExecutors` holds an app-lifetime pool that is never shut down. That's acceptable for an app-wide pool; no change.
- `WelcomeActivity` releases its `MediaPlayer` in `onDestroy` and its surface in `onSurfaceTextureDestroyed` (OK).
- Risk: none.

### Data

**A15. Foreign keys enabled — OK.** `LitterboxdDbHelper.java:57-61` (`onConfigure` → `setForeignKeyConstraintsEnabled(true)`), and both child tables declare `ON DELETE CASCADE`. There is no test proving the cascade (see A24).

**A16. Reset app leaves photo files behind and relies on calling `onUpgrade()` directly**
- `SettingsActivity.java:156-158`: photos in `getFilesDir()` are never deleted. Calling `dbHelper.onUpgrade(db, 2, 2)` by hand is an unusual pattern that's hard to explain in the oral defense, and it also drops a `users` table (an auth leftover).
- Fix: `db.delete()` on the three tables (feeding, weights, cats), then delete every `cat_photo_*.jpg` in `getFilesDir()` (one small loop, or a `FileStorageHelper.deleteAllCatPhotos()`), then `appPrefs.clear()`.
- Risk: low.

**A17. Deleting a cat leaves its photo file behind**
- `CatProfileActivity.java:230`.
- Fix: delete `fileStorageHelper.getInternalImageFile(photoPath)` before or after `deleteCat`.
- Risk: low.

**A18. Sample data is inconsistent and duplicates on a second tap**
- `SettingsActivity.java:101,108,136`: meal times are stored as `"08:00 AM"`, but onboarding stores `"HH:mm"` 24-hour times (`FeedingScheduleActivity.java:122,127`). Feature 2 (reminders) will have to parse both formats.
- Tapping "Load sample data" twice inserts Mochi and Biscuit twice.
- `selected_cat_id` is set only when it's `-1`, which is fine.
- Fix: use `"08:00"`, `"18:00"` and `"07:30"`. Before inserting, skip if a cat named Mochi already exists, or show a Toast. (I don't need to add the "TODO hooks" — there are none in this generator today; see F3.)
- Risk: low.

**A19. Splash routing when onboarding is complete but no cats exist — OK.** It goes to Welcome (`SplashActivity.java:26-32`). There is still the duplicate risk from A8.

### Permissions

**A1** covers POST_NOTIFICATIONS. Location permission is **not** requested anywhere (verified by search). The two `<uses-permission>` lines remain for the future vet-finder (OK).

### Image handling

**A20. Photo preview decodes the full-resolution image on the UI thread (OutOfMemoryError / jank risk)**
- `AddEditCatActivity.java:62` (`imgCatPhoto.setImageURI(uri)`) decodes a 12–50 MP camera photo at full size on the main thread. The save path (`:224-256`) correctly uses `inSampleSize`.
- `HomeActivity.java:127` and `CatProfileActivity.java:113` use `setImageURI` on the saved file (at most ~2047 px), which is acceptable but still main-thread I/O.
- Fix: decode the preview with the existing `inSampleSize` logic on the executor and post the Bitmap with `mainThread()`. Extract that logic once into `FileStorageHelper` (see C3).
- Risk: low.

**A21. Old photo is not deleted on replace** — it only becomes relevant once A2 (edit) exists. Fold it into A2.

**A22. A failed photo save still records a filename**
- `AddEditCatActivity.java:170-172`: `savedPhotoFileName` is set before the save. If the decode fails, the DB points to a missing file. The UI falls back to the placeholder, so nothing crashes.
- Fix: only set the path if the save returned `true`.
- Risk: low.

### Theming (visibly broken)

**A23. Dark mode loses all brand colours**
- `res/values-night/themes.xml` redefines `Theme.Litterboxd` with **no items**, so in system dark mode colorPrimary etc. fall back to MaterialComponents purple. The emulator is in dark mode; the purple status bar in my screenshots is this bug.
- Fix: delete `values-night/themes.xml`. The day theme is already a `Light` theme, so the app stays light in dark mode, which is what the file's comment intends.
- Risk: low.

### Tests

**A24. `CatDaoTest` is weak and uses the real database**
- `androidTest/.../CatDaoTest.java`: the `weightLogDao` and `feedingScheduleDao` fields are unused, there is no cascade test, and it writes to the app's real `litterboxd.db`.
- Fix: add one test that inserts a cat plus a weight plus a meal, deletes the cat, and asserts both lists are empty (proves A15). Use `context.deleteDatabase(...)` in `@Before` and `@After`.
- Risk: low.

---

## B. OVER-ENGINEERING / UNUSED

| ID | File:line | What | Proposed fix | Risk |
|---|---|---|---|---|
| B1 | `ui/auth/CenterCropVideoView.java` (whole file, untracked) | Unused custom VideoView left over from the welcome-animation attempts. It is not one of the hard-rule files. | Delete. | low |
| B2 | `auth/Validator.java` | Email/password validator from the removed accounts; unused. Its test was already deleted (staged). | Delete the file and the empty `auth` package. | low |
| B3 | `LitterboxdDbHelper.java:85` | `DROP TABLE IF EXISTS users` (auth leftover). | Remove the line. | low |
| B4 | `util/AppExecutors.java` | Singleton plus a private `Executor` wrapper class; the comment mentions "hashing" (auth leftover). It is close to "one ExecutorService + main Handler" but adds a layer. | Simplify to `public static final ExecutorService DISK_IO = Executors.newFixedThreadPool(3);` and `public static final Handler MAIN = new Handler(Looper.getMainLooper());`, and update the ~10 call sites (`AppExecutors.MAIN.post(...)`). This maps exactly to taught item §11.3. **Optional**: the current code is explainable; strike this if you'd rather avoid touching every Activity. | low–medium (many call sites) |
| B5 | `FileStorageHelper.java:39-54` | `saveImageToInternal` is unused (it copies full-size images and duplicates the downscale logic in AddEditCat). | Replace with the downscale method moved here from AddEditCat (see C3). | low |
| B6 | `FileStorageHelper.java:71-91` | `exportCsv` is unused; it's a "vet report" hook. It's not listed under Feature 1 or 2. | **Your call**: keep it if someone owns CSV export, otherwise delete. It is the only demonstration of external storage (§14), which may be worth keeping for the report. | low |
| B7 | `DateFormatUtils.java:9,15-17` | `formatTime` is unused. | Delete, or keep if Feature 2 will use it (ask the owner). | low |
| B8 | `Cat.java:20-35`, `FeedingMeal.java:12-18`, `WeightLog.java:12-18` | All-args constructors are unused. | Delete (each model keeps its no-arg constructor and setters). | low |
| B9 | `LitterboxdApplication.java:18` | `if (SDK_INT >= O)` is always true (minSdk 26; lint ObsoleteSdkInt). | Remove the `if`. | low |
| B10 | Resources (lint UnusedResources) | Strings: `title_left`, `title_right`, `tagline` (duplicate of `subtitle_text`), `btn_add`, `btn_delete`, `meal_label_prefix`, `meal_time_prefix`, `meal_portion_prefix`, `home_greeting_guest`, `nav_settings`, `error_invalid_portion`. Dimens: `margin_vertical`, `spacing_small/medium/large`, `text_size_body`. Colour: `accent_warm`. Drawables: `ic_small_heart`, `marker_highlight_background`, `neobrutalist_button_secondary`. Launcher: `mipmap-*/ic_launcher*`, `drawable/ic_launcher_background/foreground` (now unused since the manifest switched to `app_icon`). | Delete the clearly dead ones. **Keep** `title_edit_cat` (used by A2), `home_greeting_guest` (used by C2), `meal_*_prefix` (used by C1), `error_invalid_portion` (used by C4), and the `dimens` (used by C1/C5) — those get wired up instead of deleted. | low |
| B11 | `res/backup_rules.xml`, `res/data_extraction_rules.xml` | Stray copies directly in `res/` (not a valid resource folder; the real ones are in `res/xml/`). | Delete the two root copies. | low |
| B12 | Repository | 937 of the 1014 tracked files are build/IDE output (`app/build/`, `.gradle/`, `.idea/`), plus `.DS_Store` files and `local.properties` (contains your SDK path). These cause the constant merge noise in `git status`. | Add a standard Android `.gitignore` and `git rm -r --cached` those paths (the files stay on disk). **Needs team agreement**, because it changes everyone's checkout. | low |

---

## C. REDUNDANT / DUPLICATED / HARD-CODED

| ID | File:line | What | Proposed fix | Risk |
|---|---|---|---|---|
| C1 | `FeedingScheduleActivity.java:92,100,108,109,135,137` | Hard-coded "Breakfast/Lunch/Dinner/Snack", "Meal ", hint texts and default "50". `setPadding(0, 8, 0, 16)` is in **px**. | Add `<string-array name="default_meal_labels">`. Use the existing `meal_label_prefix` and `meal_portion_prefix` strings. Convert the padding using `getResources().getDimensionPixelSize(R.dimen.spacing_small)`. | low |
| C2 | `HomeActivity.java:90,121,136`; `OwnerNameActivity.java:51` | "Cat Parent" is duplicated in two classes (and `home_greeting_guest` exists but is unused). "No cat selected" and `" kg"` are hard-coded. | Use `R.string` resources (add `no_cat_selected`, `cat_summary` with placeholders, `default_owner_name`). | low |
| C3 | `AddEditCatActivity.java:224-256` vs `FileStorageHelper.java:39-54` | Two image-saving implementations. | One `FileStorageHelper.saveDownscaledImage(Uri, String)` plus `decodeSampled(...)` (used by A20). | low |
| C4 | `CatProfileActivity.java:107,134,143,158,166,177,224`; `SettingsActivity.java:68` | Hard-coded labels ("Breed: ", "No weight logs recorded yet.", " kg on ", "No feeding schedule configured.", "this cat", "Owner name updated"). `setPadding(0,4,0,4)` and `(32,16,32,16)` are px. | Move to `strings.xml` with placeholders; convert padding via dimens. | low |
| C5 | `AddEditCatActivity.java:127` | Breed list is a Java array. The course explicitly teaches `<string-array>` + `android:entries` for Spinners (§8). | Move to `<string-array name="cat_breeds">` and set `android:entries` in the XML (removes 3 lines of Java). | low |
| C6 | `AddEditCatActivity.java:119` | Date shown as `year + "-" + (month+1) + "-" + day` (lint SetTextI18n; it is also a second date format). | `DateFormatUtils.formatDate(selectedBirthdayMs)`. | low |
| C7 | `AgeUtils.java:8-22` and `:45-59` | Identical month calculation copied twice. | Extract `private static int totalMonths(Long, int, int)`. The existing tests cover it. | low |
| C8 | `getResources().getColor(...)` ×5 (`FeedingScheduleActivity.java:101`, `CatProfileActivity.java:135,148,159,168`) | Deprecated API. | `ContextCompat.getColor(this, ...)` (core library, already used). | low |
| C9 | New `LitterboxdDbHelper(this)` in every Activity (Splash, AddEditCat, Feeding, Home, Profile, Settings) | Multiple helper instances and connections that are never closed; risk of "database is locked" with the 3-thread pool. | One shared instance, e.g. `LitterboxdDbHelper.getInstance(context)` (a static synchronized method using the application context). Small change, easy to explain. | low |
| C10 | Layout nesting | `activity_home.xml:43-106` (Card → LinearLayout → LinearLayout → LinearLayout) and `activity_add_edit_cat.xml:137-172` (LinearLayout ×3). Three levels; acceptable but borderline. | **Leave** (rule 4: no redesign). Listed for the report. | — |
| C11 | Hard-coded colours in XML | `drawable/neobrutalist_button_primary.xml`, `ic_cat_face.xml`, `ic_small_heart.xml`, plus `activity_welcome.xml` `#000000`/`#26000000`/`#33000000`. | The drawables could use `@color/black`. Welcome is rule 1, so report only (F1). | low |

---

## D. OUTSIDE COURSE CONTENT

| API / pattern | Where | Verdict |
|---|---|---|
| `TextureView`, `SurfaceTexture`, `Surface`, `Matrix` + `MediaPlayer` on a surface | `WelcomeActivity` | **Keep** (hard rule 1). Justification: `MediaPlayer.create` is taught (§5); a `TextureView` is used because the course's `SurfaceView` (§10) cannot be cropped with a transform matrix to match the still image. |
| Activity Result API (`registerForActivityResult`, `GetContent`) | `AddEditCatActivity.java:57` | **Keep**: explicitly recommended in §4 and §19. |
| `AlertDialog` (appcompat), `DatePickerDialog`, `TimePickerDialog` | Home, Profile, Settings, AddEditCat, Feeding | **Keep**: the course teaches DatePicker/TimePicker widgets; the dialogs are their standard wrappers. `AlertDialog` isn't listed — justify it as the standard confirm-dialog for destructive actions, or replace the cat switcher with a Spinner (taught) if the lecturer is strict. |
| `Application` subclass | `LitterboxdApplication` | **Keep, justify**: the channel must exist before any notification (§12, §19.11). The alternative is creating it in `HomeActivity.onCreate` (duplicate creation is ignored). |
| `ActivityCompat` / `ContextCompat` | Home, LocationHelper | **Keep**: compat wrappers of the taught `checkSelfPermission` / `requestPermissions` (androidx.core is allowed). |
| SQLite `onConfigure` + foreign keys + `CREATE INDEX` | `LitterboxdDbHelper` | **Keep, justify**: plain SQLiteOpenHelper. FKs give cascade delete, which avoids manual cleanup code. |
| `Executors.newFixedThreadPool` + `Handler(Looper.getMainLooper())` | `AppExecutors` | **Keep**: both taught (§11). See B4 for the simplification. |
| try-with-resources, lambdas, `UUID` | various | **Keep**: plain Java. |
| XML `<font-family>`, `<layer-list>` drawables | `res/font`, `res/drawable` | **Keep**: resources only; the fonts are system fonts (no downloadable fonts or library). |
| Material Components used for styling | `MaterialButton`, `TextInputLayout`/`TextInputEditText`, `MaterialCardView`, `Theme.MaterialComponents.Light.NoActionBar`, `Widget.MaterialComponents.Button.*` styles | **Keep** per your note (styling only). |
| Not found anywhere | Kotlin, Compose, Fragments, Room, DataStore, coroutines, LiveData/ViewModel, RxJava, Hilt, Glide, Retrofit, FusedLocationProvider | Clean. Dependencies are appcompat, material, constraintlayout plus test libs only. |

---

## E. UI CONSISTENCY (report; fix only if clearly broken)

| ID | Where | Issue | Proposal | Risk |
|---|---|---|---|---|
| E1 | `activity_add_edit_cat.xml:280-293` | **Clearly broken**: 6 coat-colour RadioButtons in one horizontal row overflow a ~360 dp phone width, so Calico/Tabby are clipped or unreachable on small screens. | Two rows aren't possible inside one RadioGroup; switch the group to `android:orientation="vertical"` (like Lifestyle). Smallest fix, no redesign. | low |
| E2 | `activity_owner_name.xml` | No ScrollView. In landscape with the keyboard open, the bottom-anchored Continue/Skip buttons overlap the input. | Wrap in a ScrollView, or add `android:windowSoftInputMode="adjustResize"` and constrain the buttons below the input. | low |
| E3 | `activity_home.xml:110-117` | In landscape the greeting + cat card take most of the height; the feature-card ScrollView becomes ~100 dp tall. Usable, not broken. | Leave. | — |
| E4 | `activity_home.xml:89-95` | Switch Cat uses `textSize="12sp"`; its height is still 48 dp (Material min). OK. | Leave. | — |
| E5 | `FeedingScheduleActivity.java:128` | TimePicker opens in 12-hour mode but the button shows 24-hour text. | Pass `true` (24-hour), or use `DateFormat.is24HourFormat(this)`. | low |
| E6 | Empty states | No photo → `ic_cat_face` placeholder (OK). No weights / no meals → text shown (OK, but hard-coded, C4). No cats → Home dead end (A9). | — | — |
| E7 | Feature cards on Home | Not clickable, with "Coming soon" text (OK, hooks preserved). | Leave (rule 2). | — |
| E8 | `mipmap-*/app_icon.png` | The same **2000×2000, 549 KB** PNG is copied into all 5 densities (~2.7 MB APK bloat; lint IconDipSize/IconDuplicatesConfig). Used as `roundIcon` without being round. | Export proper sizes (48/72/96/144/192 px), e.g. with Android Studio → New → Image Asset. Whoever added the icon should decide. | low |
| E9 | Lint Overdraw ×7 | Each layout sets `@color/off_white_background`, which the theme already paints. | Cosmetic; remove the `android:background` attributes. | low |

---

## F. CHECKED AND LEFT ALONE

**F1. `WelcomeActivity.java` / `activity_welcome.xml` (hard rule 1): report only, not applied**
- *Real bug — the still image can disappear while idle.* `WelcomeActivity.java:113-119`: the `MEDIA_INFO_VIDEO_RENDERING_START` listener hides `imgCatStatic` without checking `isPlaying`. Some decoders also emit this event after the idle `seekTo(0)` in `resetToIdleState()`, which would hide the still image while nothing plays. The video's first frame would then show instead, which looks the same, so the impact is low.
  - Exact fix: change line 114 to `if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && isPlaying) {`. That guard existed at the end of the previous session and has since been removed. The listener is redundant with `onSurfaceTextureUpdated` (lines 98-102), so deleting lines 113-120 entirely is equally valid.
- *Minor — stream not closed on error.* `:154-163`: the `InputStream` isn't closed if decoding throws. Exact fix: `try (InputStream is = getResources().openRawResource(R.raw.homepage_cat)) { imgCatStatic.setImageBitmap(BitmapFactory.decodeStream(is)); } catch (IOException e) { Log.e(...); }`.
- *Structure — wrong package.* WelcomeActivity lives in the package `ui.auth` although auth was removed. Moving it would modify the file, so I left it.
- No crash, leak or missing resource found: `btnGetStarted`, `textureViewCat`, `imgCatStatic`, `rootWelcomeLayout`, `@raw/homepage_cat`, `@raw/home_page_animation`, `@font/*` and `@string/btn_get_started` all exist. The player is released in `onDestroy` and in `onSurfaceTextureDestroyed`.

**F2. Feature hooks — preserved; nothing implemented**
- The three Home "Coming soon" cards and their `<!-- TODO -->` comments.
- The DB `TODO` naming the `daily_checkins`, `checkin_answers`, `care_tasks`, `care_task_logs` and `alerts` tables.
- The location permissions plus `LocationHelper`.

**F3. Discrepancies with the brief — please confirm**
- The notification channel is `litterboxd_feeding_channel` ("Litterboxd Notifications"), **not** `care_reminders`. Should I rename the ID to `care_reminders`? It's safe now, since nothing posts to it yet. Otherwise the Feature 2 owner should know the real ID. Its name and description are also hard-coded strings (they should move to `strings.xml`).
- The sample-data generator contains **no TODO hooks**, so there is nothing to preserve there.
- The audit scope lists "edit feeding schedule", but no screen offers it after onboarding: CatProfile only displays meals. Adding an "Edit schedule" button that opens `FeedingScheduleActivity` with `cat_id` is arguably a new feature. Tell me whether it's in scope.
- There is likewise no way to add a second cat except via sample data. Same question.

**F4.** `LitterboxdDbHelper.onUpgrade` drops all tables. That's acceptable at this stage; the TODO is kept.

**F5.** `SplashActivity` runs a tiny DB query on the main thread (A0-adjacent). It's equivalent to the lab-task pattern; leave it unless you want strictness (lint CustomSplashScreen is also just a warning).

**F6.** Gradle, AGP and dependency versions are unchanged, as instructed; the lint GradleDependency warnings are ignored.

---

## Prioritised fix plan (awaiting your approval; strike any item)

**P1 — broken / crash / data corruption** (one commit each)
1. A1 Declare `POST_NOTIFICATIONS`, plus a denial Toast
2. A2 (+A21) Make AddEditCatActivity actually edit when given `cat_id`
3. A3 + A4 Weight parsing (`,`/`.`, empty) and validation in both places, plus unit tests
4. A7 + A8 Onboarding duplicate-cat paths (Back from step 3; relaunch after step 2)
5. A9 Delete last cat → Splash
6. A5 + A6 Thread-safety: read views on the UI thread, assign fields on the UI thread, null-check the cat
7. A11 + A13 AddEditCat rotation state and birthday default/max date
8. A12 FeedingSchedule rotation state
9. A16 + A17 + A22 Reset/delete clean up photo files; failed save records no path
10. A20 Downscaled photo preview off the main thread (with C3)
11. A23 Remove the empty night theme
12. E1 Coat-colour row overflow (clearly broken)

**P2 — course-constraint violations**
13. C1, C2, C4, C6 Hard-coded strings → `strings.xml`; px padding → dimens
14. C5 Breed list → `<string-array>` + `android:entries`
15. C8 Deprecated `getColor` → `ContextCompat.getColor`
16. F3 Channel ID rename / move channel strings to resources (if approved)

**P3 — over-engineering / redundancy**
17. B1, B2, B3 Remove auth/video leftovers
18. B5, B7, B8, B9, B10, B11 Remove unused code and resources
19. C7 AgeUtils duplication
20. C9 Shared DB helper
21. A18 Sample-data time format plus duplicate guard
22. A24 Cascade-delete instrumented test
23. *(optional)* B4 Simplify AppExecutors
24. *(needs team agreement)* B12 `.gitignore` + untrack build/IDE files

**P4 — cosmetic**
25. E2 OwnerName landscape/keyboard
26. E5 24-hour TimePicker
27. E9 Overdraw backgrounds
28. E8 Properly sized launcher icons (only if the icon's author agrees)

**Will not touch:** WelcomeActivity / activity_welcome.xml (F1, report only), the Feature 1/2 hooks (F2), and Gradle versions.

*Phase 3 sections (fixed / skipped / final results) and MANUAL_TEST_CHECKLIST.md will be added after Phase 2.*
