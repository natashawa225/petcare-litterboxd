# REPORT_AUDIT.md — Audit of LITTERBOXD_Group_Report

This document records how the group report was produced, what was verified against the source code, and what still needs the team's confirmation before submission. It is a working document for the team, not part of the submitted report itself.

## 1. Source of truth

All claims in the report were checked against the repository at `/Users/natashawilfrid/Downloads/litterboxd/Litterboxd` on the `main` branch (HEAD at the time of writing: `3490624`, "improved UI, organised articles in horizontal carouse. seed diary entries"). Only the `app/` Gradle module is active (`settings.gradle.kts` includes only `:app`). A second, nested copy of an older project state exists at `petcare-litterboxd-main/` — see Section 4.4.

## 2. Implemented features verified

Each item below was confirmed by reading the named source file(s), not inferred from naming alone.

| Feature | Verified in | Status |
|---|---|---|
| Onboarding (Welcome → owner name → cat → feeding schedule) | `ui/splash/SplashActivity.java`, `ui/auth/WelcomeActivity.java`, `ui/onboarding/*` | Fully implemented |
| Multi-cat profiles, avatars, weight history | `ui/profile/CatProfileActivity.java`, `ManageCatsActivity.java`, `CatAvatars.java`, `data/dao/WeightLogDao.java` | Fully implemented |
| Daily check-in (chip groups + follow-ups) | `checkin/DecisionTree.java`, `CheckInSession.java`, `ui/checkin/CheckInActivity.java`, `res/raw/checkin_tree.json` | Fully implemented |
| History-aware follow-up questions (repeat-day triggers) | `checkin/HistoryTrigger.java`, `HistoryTriggerEvaluator.java` | Fully implemented |
| Health alert engine (signal + baseline-ratio + weight-drop rules) | `health/HealthRuleEvaluator.java`, `HealthRule.java`, `WeightTrend.java`, `res/raw/health_rules.json` | Fully implemented |
| Live "more info" search against icatcare.org | `health/HealthArticleClient.java`, `HealthArticleFinder.java` | Fully implemented, but fragile (Section 5) |
| Reading Corner suggestions | `health/ReadingCard.java`, `HealthContentParser.java`, `ui/home/ReadingCardAdapter.java` | Fully implemented |
| Care tasks (daily/monthly/one-off) + feeding-derived reminders | `ui/tasks/CareTasksActivity.java`, `data/CareTaskSetup.java`, `data/FeedingTaskPlanner.java` | Fully implemented |
| Reminder scheduling (AlarmManager, boot-persistent) | `reminders/CareReminderScheduler.java`, `CareTaskReminderReceiver.java`, `BootReceiver.java` | Fully implemented |
| Diary (memories, milestones, merged care history, tags, photos) | `ui/diary/DiaryActivity.java`, `DiaryEntryEditActivity.java`, `util/DiaryTags.java` | Fully implemented |
| Diary photo collage | `ui/diary/DiaryMemoriesActivity.java` | Fully implemented |
| Location features | `location/LocationHelper.java`, manifest permissions | **Not implemented** — class exists and permissions are declared, but no Activity calls it |
| CSV export | `data/file/FileStorageHelper.java` (`exportCsv`) | **Not implemented in UI** — method exists, not called from any screen |
| Accounts / login / cloud sync / social features | — searched manifest and `ui/` tree | **Not present** — confirmed absent, not merely unfound |

## 3. Important classes and files referenced in the report

See Section 2.4 of the report for the full grouped list. Line counts (via `wc -l`) were used only to support claims like "the largest class in the app" (`CareTasksActivity.java`, 628 lines) and were verified directly, not estimated.

## 4. Architecture summary

Three-layer, non-MVVM structure (Activities + plain-Java domain classes + SQLiteOpenHelper/DAO data layer), explicitly chosen because the team's own `docs/course_reference.md` forbids Room, Compose, Fragments, Navigation Component, and coroutines/RxJava/LiveData/ViewModel. This constraint is stated in-repo, not assumed. See Figure 1 in the report (`architecture_diagram.png`, generated from `make_diagram.py`, not hand-drawn or AI-hallucinated box positions — every box corresponds to a real class or package named in Section 2.4).

## 5. Development challenges identified (and why these five)

The five challenges in Section 3.1 of the report were chosen because each has direct, dated evidence in the repository (a commit, a test file, or an explicit code comment), not because they were the only plausible candidates:

1. The 24-item audit (`AUDIT_REPORT.md`) — primary evidence is the document itself plus its 20+ remediation commits on 2026-10-24.
2. The care-task duplication problem — evidence is the `CareTaskSetup` Javadoc explicitly stating "Replaces the old seeder that gave every cat the same six generic tasks."
3. The baseline-vs-signal rule design — evidence is the two distinct code paths (`matchesSignals` / `matchesBaseline`) in `HealthRuleEvaluator.java`.
4. The Glide exception — evidence is `docs/course_reference.md` explicitly forbidding Glide, cross-referenced against `app/build.gradle.kts`, which includes it with an explanatory comment.
5. The icatcare.org scraping safeguards — evidence is the host-validation code in `HealthArticleClient.get()`.

Other candidate challenges (e.g., rotation handling, Validators decimal parsing) exist in the audit but were folded into challenge 1 rather than listed separately, to avoid padding the count with sub-items of the same root cause.

## 6. Evidence supporting group contributions

Built entirely from `git log --no-merges --pretty=format:'COMMIT|%an|%s' --name-only` and `git log --pretty=format:'%ad|%an|%s'` across all 74 commits on `main`. Key supporting observations:

- Seven distinct Git author identities exist; the report treats two pairs as the same person (`h4rlieee`/`h4rlieeee`, `k4chow`/`kachow`) based on matching usernames and non-overlapping commit timing, **not** on certainty. This is explicitly flagged as needing team confirmation in both the report and this audit.
- Commit **subjects** were used to attribute feature ownership (e.g., "Add check-in logic: decision tree, session and JSON parser" → `h4rlieeee` owns the `checkin` package), rather than raw file-touch counts, because at least one commit (`k4chow`, "Sync current Litterboxd app") touches dozens of files in what its message describes as a sync operation, which would otherwise over-attribute authorship.
- `natashawa225` is both the dominant committer (51 of 74 commits) and the account that ran this report-generation session; the report does not inflate or deflate this person's share relative to the git evidence.

## 7. Unverified assumptions (explicitly flagged in the report)

- The real full names behind the Git identities `h4rlieee`/`h4rlieeee`, `k4chow`/`kachow`, `Darren`, and `farrell` are not stated anywhere in the repository. "Raymond Harlie" is used in the report only as an inference from the email `raymond.m.harlie@gmail.com` / `r.harlie23@student.xjtlu.edu.cn`, and is marked for confirmation. The `k4chow`/`kachow` member is left as an explicit `[GROUP MEMBER — CONTRIBUTION TO BE CONFIRMED]` placeholder with no name guessed.
- Whether the two paired Git identities are genuinely the same two people (not four) is inferred, not confirmed.
- The exact split of the very first commit (2026-10-14, "First commit: project structure and auth", `natashawa225` only) is unknown — it may represent work actually done by more than one person before Git history began tracking individual contributions.

## 8. Missing information the team must provide

1. Real full names for each Git identity in the contribution table.
2. Confirmation of the two identity-pairing inferences in Section 6 above.
3. Whether the Glide dependency exception has been (or should be) formally ratified in `docs/course_reference.md`.
4. Whether `petcare-litterboxd-main/` (Section 4.4 below) should be deleted from the repository before submission, or is intentionally kept for some reason not evident from the commit history.
5. Confirmation that no usability study, user testing, or performance benchmarking was actually run — the report assumes none was, based on the absence of any such data, logs, or scripts in the repository. If one *was* run outside the repository, its results are not reflected in this report and should be added by the team with real data.

## 4.4 Repository hygiene note (supports Section 3 above)

`petcare-litterboxd-main/` is a complete, separately-committed snapshot of an earlier project state (added by `farrell`'s "Update Android project" commit on 2026-10-24), including its own `.idea/` project files, `gradlew`, and an older `SettingsActivity.java` that no longer exists in the live `app/` module. It is **not** referenced by `settings.gradle.kts` and does not build as part of the app. It should not be read as evidence of current functionality, and its presence was excluded from every feature claim in the report. The team should decide whether to remove it; this report does not do so, since deleting files is outside this task's scope.

## 9. Rubric compliance checklist

| Requirement | Status |
|---|---|
| Minimum 2,000 words | Met — report body is approximately 3,800–4,200 words excluding references (verified by word count of `LITTERBOXD_Report_Source.md` minus the Mermaid diagram block and reference list) |
| MS Word (.docx) and PDF | `.docx` generated with docx (Node.js); PDF exported from the `.docx` with LibreOffice and visually verified page-by-page (7 pages) in this session |
| US Letter paper | Set explicitly (8.5in × 11in) in `build_docx.js` page properties |
| Two-column layout | Set explicitly (`column: { count: 2 }`) for all body sections; title, abstract, Figure 1, and the contribution table use single-column continuous sections for readability, which is standard IEEE practice for wide elements |
| Single-spaced | Default paragraph spacing used throughout; no added line spacing |
| 10-point Times New Roman | Set explicitly (`font: "Times New Roman", size: 20` half-points = 10pt) as the document default style |
| IEEE-style structure | Followed: title block, abstract, numbered sections, numbered references, figure/table captions |
| Abstract 100–200 words | Abstract is approximately 175 words |
| Required sections (Abstract, Introduction, App Design, Evaluation, Conclusion, References) | All present, in order |
| Architecture diagram | Figure 1, generated programmatically from the verified class/package structure (not hand-wavy) |
| Contribution table | Table 1, present with explicit confirmation flags where evidence is incomplete |
| IEEE-style numbered citations [n] | Used throughout; 11 references, all real and checked (Section 10) |
| No fabricated content | No invented test results, user studies, member names, or API claims; every technical claim traces to a named file |

## 10. Reference verification notes

- [1] and [2] (Li et al. 2010; Rooksby et al. 2014): DOIs confirmed via web search against independent bibliographic sources (BibTeX record and university eprint repositories) before inclusion, not recalled from memory alone.
- [3]–[7]: Official Android Developers documentation pages, cited by topic rather than by a specific accessed date, since their content is maintained/updated by Google.
- [8] (Letterboxd) and [9] (Flo): Claims about these products' logging/diary behaviour were checked against their own official FAQ/help-center pages via web search immediately before writing the Related Work and Comparison sections, not assumed from general familiarity.
- [10] (11pets): Explicitly caveated in the report itself as "its own marketing" / "public description" language, because the only available sources were app-store listings, not independent documentation.
- [11] (Glide): Primary-source GitHub repository, used only to support the factual claim that Glide is an image-loading/caching library, which is also independently verified from its actual use in `ReadingCardAdapter.java` and `DiaryEntryDetailActivity.java`.

## 11. Issues that could affect the final submission

1. **PDF verified.** This machine had no LibreOffice, pandoc, or LaTeX installation to render the `.docx` into a verifiable PDF. LibreOffice was installed via Homebrew during this session; `LITTERBOXD_Group_Report.pdf` was then generated from the final `.docx` and all 7 pages were rendered to images and visually checked (Letter size, two-column body, Times New Roman, figure and table placement, page numbers). If the `.docx` is edited later, regenerate the PDF with `soffice --headless --convert-to pdf LITTERBOXD_Group_Report.docx` and re-check it before resubmitting.
2. **Group member names.** The report cannot be considered final until the team supplies real names for the Git identities, particularly the `k4chow`/`kachow` contributor, who is currently a named placeholder.
3. **No independent usability or security testing was performed as part of this report.** All functional and architectural claims come from static code reading, not from running the app end-to-end in this session (though the earlier `AUDIT_REPORT.md` in the repository does include a prior emulator verification pass, which this report cites rather than repeats).
4. **The `petcare-litterboxd-main/` directory** should be addressed by the team (Section 4.4) — its presence is reported honestly but not resolved.
