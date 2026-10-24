# Litterboxd: Course Technical Reference

What our Android module teaches, merged from the lecture slides (UI Elements, More UI Elements, Intent & Components, Permissions & Storage).

**Purpose:** anyone (human or AI) writing code for Litterboxd must stay within these techniques unless the team explicitly approves an exception. Everything here should be explainable in the oral defense.

---

## 0. Baseline stack (what to use)

| Layer | Use | Do NOT use (not taught) |
|---|---|---|
| Language | Java (Java 8+, lambdas, anonymous inner classes) | Kotlin |
| UI | XML layouts + Activities | Jetpack Compose, Fragments (unless unavoidable) |
| Layouts | ConstraintLayout (main), LinearLayout, ScrollView, FrameLayout, GridLayout | Deep nesting |
| Navigation | Explicit Intents, `putExtra()` / `Bundle` | Navigation Component |
| Storage | SQLiteOpenHelper, SharedPreferences, internal/external files | Room, DataStore |
| Threads | `Thread` / `ExecutorService` + `runOnUiThread` / `View.post` / `Handler(Looper.getMainLooper())` | Coroutines, RxJava, LiveData/ViewModel |
| Notifications | NotificationChannel + Notification.Builder + PendingIntent | — |
| Location | LocationManager + LocationListener | Google FusedLocationProviderClient |
| Libraries | Standard AndroidX only: appcompat, material, constraintlayout, core (+ test libs) | Retrofit, OkHttp, Volley, Hilt, Dagger, Firebase, Glide, etc. |

---

## 1. Project structure and resources

- `res/` holds layouts, drawables, mipmaps, strings and values.
- AAPT generates `R.java`; every resource gets an integer ID. Access with `R.<type>.<name>`, e.g. `setContentView(R.layout.activity_main)`.
- Sub-classes: `R.layout`, `R.drawable`, `R.string`, `R.mipmap`, etc.

---

## 2. Four app components

| Component | Purpose |
|---|---|
| Activity | User-facing screen |
| Service | Long-running background work, no UI |
| BroadcastReceiver | React to system/app broadcast events |
| ContentProvider | Share structured data between apps |

Every component must be declared in `AndroidManifest.xml`.

---

## 3. Activity

### Basics
- One Activity is generally one screen; an app can have many.
- Each Activity: extends `AppCompatActivity`, calls `setContentView()` in `onCreate()`, and is registered in the manifest.
- Launcher Activity needs an `<intent-filter>` with `android.intent.action.MAIN` and `android.intent.category.LAUNCHER`.

### Back stack
- LIFO. Starting a new Activity pushes the old one onto the back stack. Back destroys the current Activity and resumes the one on top.
- `finish()` closes the current Activity.

### Lifecycle
States: **Running** (foreground, focused), **Paused** (partially covered), **Stopped** (fully hidden; may be killed under low memory), **Destroyed**.

- Launch: `onCreate()` → `onStart()` → `onResume()`
- Partially covered: `onPause()`
- Fully hidden: `onPause()` → `onStop()`
- Return from stopped: `onRestart()` → `onStart()` → `onResume()`
- Close: `onPause()` → `onStop()` → `onDestroy()`

| Callback | Typical use |
|---|---|
| `onCreate()` | Initial setup, load saved state |
| `onRestart()` | Read cached state |
| `onStart()` | Reset application |
| `onResume()` | Start foreground-only behaviour (animations) |
| `onPause()` | Pause animations / foreground tasks, unregister listeners |
| `onStop()` | Cache/save state |
| `onDestroy()` | Release resources, save persistent data |

Poor lifecycle handling causes crashes on phone calls, app switching and rotation, lost progress, and wasted resources.

### View measurement caveat
`getWidth()` / `getHeight()` return 0 in `onCreate()` / `onStart()`. Read real sizes in `onWindowFocusChanged(boolean hasFocus)`.

---

## 4. Intents

### Explicit and implicit
- Explicit: `startActivity(new Intent(this, Activity2.class));`
- Implicit: describe action/data/category; the OS picks a matching component via intent-filters. Multiple matches show an "Open with" chooser. Check a handler exists with `intent.resolveActivity(getPackageManager()) != null`.

### Six Intent properties
1. Component: explicit target class
2. Action: e.g. `ACTION_MAIN`, `ACTION_VIEW`, `ACTION_DIAL`
3. Data: URI, e.g. `tel:123456`, `https://...`
4. Type: MIME type, e.g. `image/*` (`setDataAndType()` sets both)
5. Category: implicit `startActivity()` adds `CATEGORY_DEFAULT` automatically
6. Extras: `Bundle` of key-value data

### Intent-filter matching
Action must match one filter action; every Intent category must be in the filter; data is matched on scheme, host, port, path and MIME type.

### Taught implicit system intents
- Dialer: `ACTION_DIAL`
- Browser: `ACTION_VIEW` with a URL
- Image picker: `ACTION_GET_CONTENT` with `image/*`

### Passing data
- `intent.putExtra("cost", roomCost);` → receive with `getIntent().getIntExtra("cost", default)`.
- `Bundle` with `putExtras()` / `getExtras()` for batches.
- Returning results: `startActivityForResult()` + child `setResult(RESULT_OK, intent); finish();` + parent `onActivityResult()` with a requestCode. **Taught as deprecated**: the newer Activity Result API (`registerForActivityResult`) is the recommended replacement.
- Intents are for small data only; large data goes in files or the database.

---

## 5. Services

- Callbacks run on the **UI thread**; a Service is not a Thread. Heavy work still needs a worker thread.
- **Started service:** `startService()`; runs until `stopService()` or `stopSelf()`; override `onStartCommand()`.
- **Bound service:** `bindService()`; lives while clients are bound; uses `IBinder` / `Binder` subclass; client uses `ServiceConnection` (`onServiceConnected` / `onServiceDisconnected`); override `onBind()`.
- **Foreground service:** must show an ongoing notification (e.g. music playback).
- **Background service:** invisible to the user.
- Lifecycle: `onCreate()`, `onStartCommand()`, `onBind()`, `onUnbind()`, `onDestroy()`.
- Media: `MediaPlayer.create(...)`; SeekBar synced via a background polling thread.

---

## 6. Broadcast receivers and Context

### BroadcastReceiver
- Send with `sendBroadcast(intent)`; handle in `onReceive(Context, Intent)`. Keep `onReceive()` short.
- **Static registration:** `<receiver>` + `<intent-filter>` in the manifest. Restricted for most implicit broadcasts since Android 8.0 (API 26).
- **Dynamic registration:** `registerReceiver(receiver, IntentFilter)`; always `unregisterReceiver()` (in `onPause()` / `onDestroy()`) to avoid leaks.
- Taught example: battery status via `Intent.ACTION_BATTERY_CHANGED`.

### Context
Access resources, `getSystemService(...)`, start Activities, send broadcasts, `checkSelfPermission()`.
System services: `SENSOR_SERVICE`, `LOCATION_SERVICE`, `NOTIFICATION_SERVICE`, `VIBRATOR_SERVICE`, `WIFI_SERVICE`, `CONNECTIVITY_SERVICE`.

---

## 7. Views, layouts and attributes

### View and ViewGroup
- `View`: base class for widgets; draws a rectangle and handles touch.
- `ViewGroup`: container that positions children; can be nested (but deep nesting hurts performance).

### Layouts
1. **ConstraintLayout:** default for new Activities. Position relative to parent or siblings (`layout_constraintTop_toBottomOf`, etc.); bias 0.0–1.0 (0.5 = centre). Flat hierarchy, better performance. Misconfigured constraints break on rotation or other aspect ratios, so test multiple Pixel sizes and orientations.
2. **LinearLayout:** vertical/horizontal stacking with `orientation` and `layout_weight`.
3. RelativeLayout, FrameLayout (overlaying children), GridLayout, TableLayout + TableRow.
4. ScrollView / HorizontalScrollView for scrollable content.
5. WebView for web content. AbsoluteLayout is deprecated.

### Key attributes
- `android:id="@+id/name"` → `findViewById(R.id.name)`.
- `android:text`: put all text in `res/values/strings.xml`, reference as `@string/key`. Alternate locale folders (e.g. `values-zh/strings.xml`) for translations.
- `layout_width` / `layout_height`: `wrap_content`, `match_parent`, or a dp value.

### Units
| Unit | Use |
|---|---|
| `dp` | Layout dimensions (`px = dp * dpi / 160`) |
| `sp` | Font sizes only (scales with user font setting) |
| `px` | Avoid |
| `in` / `mm` / `pt` | Physical sizes |

### Padding vs margin
- `padding`: space inside the view; background fills it; included in `getWidth()`.
- `layout_margin`: space outside the view; not part of the view.

---

## 8. Widgets taught

**Basic:** TextView, EditText, Button, ImageButton, ImageView, CheckBox, RadioButton (in RadioGroup), Switch, ToggleButton (CompoundButton, `setOnCheckedChangeListener`), ProgressBar, SeekBar, DatePicker, TimePicker, NumberPicker, SearchView, Chronometer, AnalogClock, DigitalClock, TabWidget, ZoomControls.

**Adapter-based (AdapterView):** Spinner, ListView, GridView, with `BaseAdapter` / `ArrayAdapter<T>`.

**Pop-ups / system UI:** Toast, status-bar notifications.

### Event handling
- `View.OnClickListener` via anonymous inner class, lambda, or the Activity implementing the interface.
- `View.OnLongClickListener`.
- `AdapterView.OnItemSelectedListener` for Spinner/ListView.

### Spinner
1. Options as `<string-array>` in strings.xml.
2. Spinner in layout XML with `android:entries` pointing to the array; `spinnerMode` = `dropdown` or `dialog` (dialog shows a prompt title).
3. `OnItemSelectedListener` → `onItemSelected(parent, view, position, id)` and `onNothingSelected()`.
4. Or populate in Java: `ArrayList<String>` → `ArrayAdapter` → `setAdapter()`.

### Adapters
An Adapter bridges a dataset and an AdapterView, creating each item view. `ArrayAdapter` is backed by an array/list.

---

## 9. Dynamic UI (Java instead of XML)

- Create views with `new ViewClass(context)`.
- `LayoutParams` are the Java version of layout attributes. A child must use the **LayoutParams type of its direct parent** (e.g. `LinearLayout.LayoutParams`); the wrong type loses settings.
- `LayoutInflater.from(context).inflate(R.layout.x, root, false)` then `parent.addView(view, params)`.
- `findViewById()` returns null for layouts not yet inflated/attached, which leads to crashes.

---

## 10. Custom drawing and graphics

- Override `onDraw(Canvas)` (runs on UI thread); style with `Paint`. Origin (0,0) is top-left.
- Redraw: `invalidate()` from the UI thread, `postInvalidate()` from a worker thread.
- Custom View needs the constructor taking `AttributeSet` to appear in the layout designer.
- **SurfaceView:** drawing on a separate thread for real-time content. `SurfaceHolder.lockCanvas()` (may return null) → draw → `unlockCanvasAndPost()`. Callbacks: `surfaceCreated` (start thread), `surfaceChanged`, `surfaceDestroyed` (stop thread). Content is not preserved between frames; repaint everything.
- **Drawables:** BitmapDrawable / Bitmap (load with `BitmapFactory`; `ARGB_8888`; draw on a bitmap via `new Canvas(bitmap)`), VectorDrawable (XML, scales losslessly), TransitionDrawable (`startTransition(ms)` cross-fade).

---

## 11. Threading

- One UI (main) thread draws UI, dispatches events, and runs lifecycle and Service callbacks, via `Looper` / `MessageQueue`.
- Blocking the UI thread (e.g. `Thread.sleep()`, heavy computation, I/O) causes **ANR**.
- Only the UI thread may touch views; otherwise `CalledFromWrongThreadException`.
- Three taught ways to update UI from a worker thread:
  1. `Activity.runOnUiThread(Runnable)`
  2. `View.post(Runnable)` / `View.postDelayed(Runnable, delayMs)`
  3. `new Handler(Looper.getMainLooper()).post(Runnable)`

---

## 12. Toasts and notifications

### Toast
`Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();` Optional `setGravity(Gravity.CENTER, x, y)`. Non-interactive.

### Status-bar notifications
- API 26+: create a **NotificationChannel** first (importance `IMPORTANCE_HIGH` / `IMPORTANCE_DEFAULT` / `IMPORTANCE_MIN`). Once created, the app can't change its importance/sound; duplicate creation is ignored.
- Build with `Notification.Builder(context, channelId)`: small icon, title, text.
- Tap action via `PendingIntent.getActivity(...)`, which lets the system run the Intent later even if the app process is gone.
- Show: `NotificationManager.notify(id, notification)`; cancel: `cancel(id)` with the same id.

---

## 13. Permissions

- Apps have no permissions by default; declare each with `<uses-permission>` in the manifest.
- **Install-time** (low-risk, auto-granted) vs **runtime** (Android 6.0+; camera, location, storage, etc.). Android 11 adds one-time permissions.
- Runtime flow: `checkSelfPermission()` → `requestPermissions()` → handle `onRequestPermissionsResult()`.

---

## 14. Local data storage

### Internal storage
Private sandbox `/data/data/<package>/files`, deleted on uninstall. `openFileOutput()`, `openFileInput()`, `new File(context.getFilesDir(), name)`.

### External app-specific storage
`/sdcard/Android/data/<package>/files` via `getExternalFilesDir(null)`. Always check `Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)`; removable media may be missing.

### SharedPreferences
Small primitive key-value data (boolean, int, float, String) that persists across restarts. `getSharedPreferences("name", Context.MODE_PRIVATE)`; write via `Editor.putXxx()` then `commit()`; read with `getInt(key, default)` etc. Not for large datasets.

### SQLite
- `SQLiteOpenHelper`: `onCreate()` creates tables; `onUpgrade()` migrates when the version increases.
- `getReadableDatabase()` / `getWritableDatabase()`.
- CRUD: `ContentValues` + `db.insert()`, `db.query()` returning a `Cursor`, `db.update()`, `db.delete()`, `db.execSQL()`.

---

## 15. Sensors

1. `SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);`
2. `sm.getDefaultSensor(Sensor.TYPE_XXX)`; null means the hardware is missing.
3. Implement `SensorEventListener`: `onSensorChanged(SensorEvent)` (`event.values[]`) and `onAccuracyChanged()`.
4. Register with a rate such as `SENSOR_DELAY_NORMAL`.
5. **Unregister in `onPause()`** to save battery.
Taught example: ambient light (`Sensor.TYPE_LIGHT`).

---

## 16. Location

- Sources: GNSS (GPS, Galileo, GLONASS, BeiDou), cell towers, Wi-Fi (network triangulation).
- Permissions: `ACCESS_COARSE_LOCATION` (network), `ACCESS_FINE_LOCATION` (GPS + network), `ACCESS_BACKGROUND_LOCATION` (background).
- `LocationManager` from `getSystemService(LOCATION_SERVICE)`.
- `LocationListener.onLocationChanged(Location)`; `Location` gives latitude, longitude, altitude, speed, accuracy, time.
- Periodic: `requestLocationUpdates(provider, minTimeMs, minDistanceM, listener)`. Instant cached fix: `getLastKnownLocation(provider)`.
- Trade-offs: GPS drains battery; accuracy depends on provider and environment; choose polling frequency and filter by accuracy. **Remove updates in `onPause()`** / when not needed.

---

## 17. AndroidManifest.xml responsibilities

Declare all components, intent-filters, `<uses-permission>`, app theme, Activity properties (`android:screenOrientation`, `android:exported`).

---

## 18. Lab tasks (examples of expected skill level)

1. RoomBookingExample: show booked rooms list in the launcher Activity.
2. Two-Activity auto-jump after 5 s; investigate Back behaviour, log `onStop()`, test rotation and screen on/off, prevent unwanted jump after Back.
3. Colour-toggle UI: text alternates red/blue every second; button freezes it for 5 s.
4. Bouncing balls on View/SurfaceView; button adds balls (max 3).
5. MusicPlayer: started + bound service, foreground service with notification, play history in SharedPreferences/file.
6. Battery monitor with a dynamic BroadcastReceiver.
7. Light-sensor reader.
8. Location demo switching GPS/network providers.

---

## 19. Best-practice checklist

1. Register every component in the manifest.
2. `dp` for dimensions, `sp` for text, never raw `px`.
3. All text in `strings.xml`; no hard-coded strings.
4. Don't read view sizes in `onCreate()` / `onStart()`.
5. Test layouts on several screen sizes and both orientations.
6. Unregister sensors, receivers and location updates in `onPause()` / `onDestroy()`.
7. Never block the UI thread; only the UI thread touches views.
8. Intents for small data only; files/DB for large data.
9. Prefer dynamic receivers over manifest receivers on API 26+ (except broadcasts still allowed, e.g. `BOOT_COMPLETED`).
10. Avoid deep ViewGroup nesting.
11. Create the NotificationChannel before posting on API 26+.
12. Use the Activity Result API instead of deprecated `startActivityForResult()`.
13. Child views must use the parent's `LayoutParams` type.
