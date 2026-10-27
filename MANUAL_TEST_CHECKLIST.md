# Litterboxd — Manual Test Checklist

Run on the emulator (ideally one **API 33** image plus one smaller phone, e.g. Pixel 4a). Tick each box, and note the device and API level at the top of your copy.

**Setup:** install a fresh debug build. In Android Studio: Run, or `./gradlew installDebug` with JDK 17. Then start from a clean state:

```bash
adb shell pm clear com.litterboxd.app
```

To rotate the emulator, use the rotate buttons in the emulator toolbar, or Ctrl+← / Ctrl+→.

---

## 1. Fresh install → full onboarding

- [ ] Launch the app: the Splash screen briefly shows, then **Welcome** (full-screen still cat).
- [ ] Tap the cat: it rolls, then returns to the still image. Tapping again while it plays does not restart it.
- [ ] Tap **Get started** → **Step 1 of 3** (OwnerName). Tapping the cat area did not navigate.
- [ ] Enter a name → **Continue** → **Step 2 of 3** (Add Cat Profile).
- [ ] Leave the name empty → Continue → "Please enter a cat name."
- [ ] Enter a name; keep **Exact Birthday** but don't pick a date → Continue → Toast "Please select a birthday…".
- [ ] Tap **Select Birthday**: future dates can't be chosen. Pick a past date; the button shows it as e.g. "Mar 05, 2023".
- [ ] Weight: try `abc` → you can't type letters. Try `20` → error "Weight must be between 0.5 and 15.0 kg." Try `4,2` → accepted.
- [ ] Coat colour: all six options (Black … Tabby) are visible and selectable.
- [ ] **Select Photo** → pick an image → the preview shows it.
- [ ] Continue → **Step 3 of 3** (Feeding Schedule) with 2 default meals.
- [ ] Change meals per day to 3 → 3 rows; your edits to row 1 are kept.
- [ ] Tap a time → the picker is in **24-hour** mode → the time updates.
- [ ] Set a portion to `abc` or `0` → Save → "Please enter a valid portion in grams."
- [ ] Fix it → **Save** → **Home**.
- [ ] Home: "Hello, <name>!", the cat card shows name • age • weight kg, and the photo appears.

## 2. Rotate on every onboarding screen

- [ ] Welcome: rotate → no crash; the still image fills the screen.
- [ ] OwnerName: type a name, rotate → the name is kept. In landscape with the keyboard open, Continue/Skip are reachable (scroll) and don't cover the field.
- [ ] Add Cat: fill everything (name, a picked birthday, Approximate Age choice, spinners, weight, radio buttons, **photo**), rotate twice → **all values, the birthday text and the photo preview are kept**. Save → the photo appears on Home/Profile.
- [ ] Feeding Schedule: edit labels/times/portions and the meals-per-day count, rotate twice → all edits kept. Save → Profile shows the edited meals.

## 3. Back button on every screen

- [ ] Welcome → Back: the app closes.
- [ ] OwnerName → Back: Welcome.
- [ ] Add Cat (onboarding) → Back: OwnerName (no cat saved yet).
- [ ] Feeding Schedule (onboarding, cat already saved) → Back: goes to **Home** (counts as Skip). Then check Settings/Profile: **only one** cat exists.
- [ ] Home → Back: the app closes (does **not** return to onboarding).
- [ ] Profile → Back: Home.
- [ ] Add/Edit Cat opened from Profile or Home → Back: returns to where you came from; nothing saved.
- [ ] Feeding Schedule opened via **Edit Schedule** → Back: returns to Profile; meals unchanged.
- [ ] Settings → Back: Home.

## 4. Add / edit / delete cat

- [ ] Profile → **Edit** → title "Edit Cat Profile", no step indicator, all fields pre-filled (including the photo).
- [ ] Change the name and weight → **Save** → back on Profile with the new values. Home still shows **one** cat (no duplicate).
- [ ] Edit again → pick a different photo → Save → the new photo shows.
- [ ] Home → **Switch Cat** → last row **"+ Add another cat"** → Add Cat (no step indicator) → Save → Feeding Schedule (no step indicator, no Skip) → Save → **Home shows the new cat selected**.
- [ ] Delete a cat that is **not** selected → the selected cat stays selected.
- [ ] Delete the selected cat → Home shows the remaining cat.
- [ ] Delete the **last** cat → the app goes to **Welcome** (not an empty Home).

## 5. Add weight

- [ ] Profile → **Add Weight** → `4,5` + note → Save → the history shows "• 4.5 kg on <date> (note)", and the cat's weight on the card updates.
- [ ] Add Weight → `abc` / empty / `40` → Toast "Weight must be between 0.5 and 15.0 kg."; nothing added.
- [ ] A cat with no weights shows "No weight logs recorded yet."

## 6. Edit feeding schedule

- [ ] Profile → **Edit Schedule** → shows the saved meals (labels, times, portions).
- [ ] Change the count to 1 → Save → back on Profile with 1 meal.
- [ ] Rotate while editing → edits kept.
- [ ] A cat with no meals (Skip during onboarding) shows "No feeding schedule configured."; Edit Schedule then starts from 2 default rows.

## 7. Switch cats

- [ ] With 2+ cats: Home → Switch Cat → choose another → the card (name, details, photo) changes.
- [ ] Close and reopen the app → the same cat is still selected.

## 8. Load sample data

- [ ] Settings → **Load Sample Data** → Toast "Sample cats (Mochi & Biscuit) loaded…"; Home → Switch Cat lists Mochi and Biscuit.
- [ ] Tap it again → Toast "Sample cats are already loaded."; no duplicates.
- [ ] Mochi's profile shows meals at **08:00** and **18:00**; Biscuit's at **07:30**.

## 9. Reset app

- [ ] Settings → **Reset App** → Cancel → nothing changes.
- [ ] Reset App → Yes → **Welcome**; Back now closes the app (old screens are gone).
- [ ] Reopen → Welcome again; onboarding starts empty (owner name not pre-filled).
- [ ] *(optional, adb)* No photos remain:

```bash
adb shell run-as com.litterboxd.app ls files
```

## 10. Notification permission (Android 13+)

- [ ] Fresh install → finish onboarding → on Home the system dialog "Allow Litterboxd to send you notifications?" appears **once**.
- [ ] Tap **Don't allow** → Toast "Notifications are off…"; no crash. Close and reopen → the dialog does **not** appear again.
- [ ] Reset → onboard again → the dialog appears → **Allow** → no crash. *(System Settings → Apps → Litterboxd → Notifications shows a "Care reminders" category.)*
- [ ] Location permission is **never** requested.

## 11. Kill mid-onboarding and reopen

- [ ] Clear data → onboard through step 2 (cat saved) → on step 3, swipe the app away from Recents (or run `adb shell am force-stop com.litterboxd.app`).
- [ ] Reopen → the app resumes at **Feeding Schedule** (step 3), **not** Welcome.
- [ ] Save or Back → Home with exactly **one** cat.
- [ ] Clear data → kill during step 1 or step 2 (before saving the cat) → reopen → Welcome; no stray cat.

## 12. Dark mode and small screens

- [ ] Switch the system to dark mode → the app keeps its sage/off-white colours (no purple buttons or status bar).
- [ ] On a small phone, in both orientations: every screen scrolls to its last button; nothing is clipped.

## 13. Daily check-in

- [ ] On Home, tap the **Daily Check-in** card → the check-in opens with "How is *<cat name>* today?", an **All normal** button, and an "Appetite & water" card with six chips.
- [ ] With nothing selected, tap the bottom button → Toast "Tap at least one chip to log today."; the screen stays open.
- [ ] Tap **Ate less**, then **Ate more** → only **Ate more** is highlighted (one choice per group). Tap it again → it deselects.
- [ ] Select **Ate less** and **Drank more** → the bottom button says **Continue**. Tap it → "A few more questions" lists *vomited*, *hiding* and *peeing more* (in that order).
- [ ] On the follow-ups, tap **Once**, then tap **Once** again → it deselects. Skipping every follow-up and tapping **Save check-in** still works.
- [ ] **Back** (button and system Back) returns to the chips with the selection kept. System Back on the chips screen leaves without saving.
- [ ] Select only **Ate normally** and **Drank normally** → the button says **Save check-in** (no follow-ups). Save → Toast "Check-in saved for *<cat name>*" and Home.
- [ ] Tap **All normal** → both normal chips highlight, replacing any other choice.
- [ ] Rotate on the chips screen and on the follow-ups screen → selections and the current screen are kept.
- [ ] Save a check-in, run `adb shell am force-stop com.litterboxd.app`, reopen and tap the card → the same chips (and follow-up answers) are pre-selected.
- [ ] Change the answers and save again the same day → reopening shows the new answers only (one check-in per day; old follow-up answers are gone).
- [ ] Two cats: save a check-in for cat A, switch to cat B on Home, open the check-in → B's chips are empty. Switch back → A's are still there.
- [ ] Delete the cat → no crash; creating a new cat starts with an empty check-in.
- [ ] Tapping the card during the first moments after launch does nothing wrong (no crash, no stray message).

---

**Record:** device / API, date, tester, any failures (screen + steps + screenshot).
