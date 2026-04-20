# CSUA — CtrlScript Android Runtime

Build real Android apps in JavaScript — no WebView, no Android Studio, no Java.  
Same syntax as CSUI (web framework). Your `app.js` runs on both platforms.

---

## How It Works

```
app.js  →  QuickJS (C engine)  →  JNI bridge  →  Android Views
```

- **QuickJS** is a tiny JS engine (~1.5 MB) embedded in the APK as a `.so` file
- **JNI bridge** exposes 4 primitives: `createView`, `setProp`, `addChild`, `call`
- **csua.js** wraps those primitives into a full API identical to csui.js
- **No WebView** — every UI element is a real native Android View

---

## Installation

```bash
npm install -g csua
# or
bun install -g csua
```

### Prerequisites

- Node.js 18+ or Bun 1.0+
- Java 17+ (`java -version`)
- USB-connected Android device with **USB Debugging** enabled
- Android SDK (auto-installed by `csua setup` if missing)

---

## CLI Commands

| Command | Description |
|---|---|
| `csua setup` | Install Android SDK (guided, shows drive space) |
| `csua create <name>` | Create a new project |
| `csua run` | Build + install + hot-reload on save |
| `csua build` | Build release APK |
| `csua install` | Build debug APK and install on device |
| `csua logs` | Stream app logs from device |
| `csua devices` | List connected Android devices |

---

## Project Structure

After `csua create my-app` you get:

```
my-app/
  app.js           ← your entire app lives here
  csua.config.js   ← app name, package ID, icon, etc.
```

That's it. Everything else is handled by the runtime.

---

## csua.config.js

```js
export default {
    name:        'My App',
    package:     'com.yourname.myapp',  // unique reverse-domain ID
    version:     '1.0.0',
    icon:        './icon.png',          // 512×512 PNG
    splash:      './splash.png',        // 1080×1920 PNG (optional)
    orientation: 'portrait',            // 'portrait' | 'landscape' | 'auto'
    minAndroid:  8,                     // minimum Android API level (26+)
    // scheme: 'myapp',                 // deep link: myapp://path
    // updateUrl: 'https://...app.js',  // OTA JS updates without new APK
}
```

---

## Views

All views accept props as the second argument (or first if no parent).

```js
import { Box, Label, Button, Input, Image, Canvas } from './csua.js'

const root = new Box()                          // root container
const text = new Label(root, { text: 'Hello', fs: 18, color: '#fff' })
const btn  = new Button(root, { text: 'Tap me', bg: '#3b82f6' })
```

### Prop Shorthand Map

| Prop | Android Property |
|---|---|
| `bg` | `backgroundColor` |
| `c` / `color` | `textColor` |
| `fs` | `textSize` (sp) |
| `p` / `pad` | `padding` (dp) |
| `pt`, `pb`, `pl`, `pr` | directional padding |
| `m` / `margin` | `margin` (dp) |
| `w` | `width` (dp or `'match'`/`'wrap'`) |
| `h` | `height` (dp or `'match'`/`'wrap'`) |
| `br` | `cornerRadius` |
| `bs` | `elevation` (shadow) |
| `bold` | `textStyle` bold |
| `italic` | `textStyle` italic |
| `align` | `gravity` |
| `gravity` | `layoutGravity` |

### Available View Classes

| Class | Android View | Notes |
|---|---|---|
| `Box` | `LinearLayout` | default container |
| `ScrollBox` | `ScrollView` | scrollable container |
| `Label` / `Text` | `TextView` | — |
| `Button` | `Button` | — |
| `Input` | `EditText` | single line |
| `TextArea` | `EditText` | multiline |
| `Image` | `ImageView` | `src` = URL or asset |
| `Canvas` | `CsuaCanvasView` | 2D drawing |
| `List` | `RecyclerView` | `setItems(data, renderFn)` |
| `Rectangle` / `Square` | `ShapeView` | — |
| `Circle` | `ShapeView` | — |
| `SafeArea` | `LinearLayout` | respects system insets |

### Direct Property Setters

```js
label.text    = 'Updated'
label.color   = '#ff0000'
label.bg      = '#1e293b'
label.visible = false
label.opacity = 0.5
label.w       = 200        // dp
label.h       = 48
label.x       = 100        // absolute position
label.y       = 200
```

---

## Layout

```js
import { group } from './csua.js'

// group — all children share the same parent
group(root, { direction: 'row', gap: 8 },
    new Button(null, { text: 'A' }),
    new Button(null, { text: 'B' }),
    new Button(null, { text: 'C' }),
)
```

---

## Timers & Loop

```js
import { loop, stopLoop, after, every, cancel } from './csua.js'

const stop = loop(() => {
    // runs every frame (~60fps)
})
stopLoop(stop)        // stop it

const id = after(1000, () => console.log('1 second later'))
const iv = every(500, () => console.log('every 500ms'))
cancel(id)            // cancel after()
cancel(iv)            // cancel every()
```

---

## Storage

### Key-Value (SharedPreferences)

```js
import { save, load, remove, clearAll } from './csua.js'

save('score', 42)
const score = load('score', 0)   // 0 = default
remove('score')
clearAll()
```

### SQLite Database

```js
import { db } from './csua.js'

await db.run('CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, name TEXT)')
await db.run('INSERT INTO users (name) VALUES (?)', 'Alice')
const rows = await db.query('SELECT * FROM users')
const one  = await db.get('SELECT * FROM users WHERE id = ?', 1)
```

### File System

```js
import { files } from './csua.js'

await files.write('data.json', JSON.stringify({ key: 'value' }))
const content = await files.read('data.json')
const exists  = await files.exists('data.json')
const list    = await files.list('.')
await files.delete('data.json')
```

---

## Network

### fetch

```js
import { fetch } from './csua.js'

const res  = await fetch('https://api.example.com/data')
const json = JSON.parse(res.body)
```

Options: `method`, `headers` (object), `body` (string)

### WebSocket

```js
import { WebSocket } from './csua.js'

const ws = new WebSocket('wss://echo.example.com')
ws.onopen    = () => ws.send('hello')
ws.onmessage = (e) => console.log(e.data)
ws.onerror   = (e) => console.error(e.message)
ws.onclose   = () => console.log('closed')
ws.close()
```

---

## Permissions

```js
import { perm } from './csua.js'
// aliases: permission, permissions — all the same object

const granted = await perm.request('camera', 'microphone')
const has     = await perm.check('location')
```

Friendly permission names: `camera`, `microphone`, `location`, `storage`, `contacts`, `calendar`, `phone`, `sms`, `notifications`, `bluetooth`, `nfc`, `biometric`, `activity`

---

## Device APIs

```js
import { device } from './csua.js'

// Info
const info = await device.info()
// { model, brand, os, sdk, width, height, density, language, timezone, isTablet }

// Location
const loc = await device.location()   // { lat, lng, accuracy }
device.watchLocation(fn)              // continuous updates → returns stop()

// Sensors
device.tilt(fn)      // DeviceOrientation → fn({ alpha, beta, gamma })
device.step(fn)      // pedometer steps → fn(count)
device.proximity(fn) // fn(near: bool)
device.light(fn)     // fn(lux: number)
device.pressure(fn)  // fn(hPa: number)

// Hardware
device.vibrate(200)                     // ms
device.torch(true)                      // flashlight
device.brightness(0.8)                  // 0.0–1.0
device.keepAwake(true)                  // prevent screen sleep
device.battery(fn)                      // fn({ level, charging })

// UI
device.statusBar('dark')               // 'dark' | 'light'
device.hideSystemUI()
device.showSystemUI()
device.hideKeyboard()

// Clipboard
device.clipboard.copy('text')
const text = await device.clipboard.paste()

// Camera
const photo = await device.camera.snap()    // returns file path
const video = await device.camera.record()
const pick  = await device.camera.pick()    // gallery picker
```

---

## Audio

```js
import { Sound } from './csua.js'

const s = new Sound('https://example.com/sound.mp3')
s.play()
s.pause()
s.stop()
s.volume = 0.8   // 0.0–1.0
s.loop   = true

// Text-to-speech
import { device } from './csua.js'
device.speak('Hello world')
device.speak('Hello', { pitch: 1.2, rate: 0.9, lang: 'en-US' })
```

---

## Navigation / Screens

```js
import { scene, go, router } from './csua.js'

scene('home', () => {
    const root = new Box()
    new Label(root, { text: 'Home Screen' })
    new Button(root, { text: 'Go to Profile', onPress: () => go('profile', { id: 42 }) })
})

scene('profile', (data) => {
    new Label(null, { text: 'User ID: ' + data.id })
})

go('home')   // start
```

---

## System Intents

```js
import { share, openUrl, openApp, openSettings, openMaps } from './csua.js'

share({ text: 'Check this out!', title: 'Share' })
openUrl('https://example.com')
openApp('com.whatsapp')
openSettings()
openMaps({ lat: 37.7749, lng: -122.4194, label: 'San Francisco' })
```

---

## Dialogs & Toast

```js
import { dialog, toast } from './csua.js'

// Dialogs (returns Promise)
await dialog.alert('Something happened')
const yes = await dialog.confirm('Are you sure?')
const name = await dialog.prompt('Enter name:', 'Default')
const date = await dialog.datePicker()
const time = await dialog.timePicker()

// Toast notification
toast('Saved!')
toast('Error!', { duration: 'long', gravity: 'top' })
```

---

## Touch & Gestures

```js
import { onTouch, onSwipe, onPinch } from './csua.js'

onTouch(myBox, ({ x, y, type }) => {
    console.log(`${type} at ${x},${y}`)
})

onSwipe(myBox, ({ direction, dx, dy }) => {
    // direction: 'up' | 'down' | 'left' | 'right'
})

onPinch(myBox, ({ scale, distance }) => {
    // scale > 1 = zooming in, < 1 = zooming out
})
```

---

## Animations

```js
import { animate } from './csua.js'

await animate(myBox, { alpha: 0, translationY: -100 }, { duration: 300, easing: 'ease' })
// easing: 'linear' | 'ease' | 'ease_in' | 'ease_out' | 'bounce' | 'overshoot'
```

---

## 2D Game Engine (Matter.js)

Uses the same Matter.js physics engine as csui.js (browser). Loaded from CDN on first use.

```js
import { engine2d, Box, loop } from './csua.js'

// 1. Init physics world
await engine2d.init({ gravity: { x: 0, y: 1 } })

// 2. Add ground
await engine2d.addGround(900, 1080, 20)

// 3. Create a view and attach physics
const ball = new Box(root, { w: 60, h: 60, bg: 'red', x: 500, y: 100 })
await engine2d.attach(ball, { mass: 1, bounce: 0.6, friction: 0.1 })

// 4. Physics loop runs automatically — positions sync to Android Views every frame
```

### `element.physics(opts)` shorthand

```js
const box = new Box(root, { w: 80, h: 80 })
box.physics({ mass: 2, bounce: 0.4, isStatic: false })
```

---

## Background Tasks

```js
import { background } from './csua.js'

// Run once after delay
background.schedule(() => {
    console.log('runs even when app is closed')
}, { delay: 60 * 1000 })  // ms

// Repeat on interval
background.schedule(() => {
    // sync data, check updates, etc.
}, { repeat: 15 * 60 * 1000 })  // every 15 min
```

---

## Biometric / Keystore

```js
import { device } from './csua.js'

// Authenticate
const ok = await device.biometric.authenticate({ title: 'Confirm', subtitle: 'Use fingerprint' })

// Encrypted storage (AES-GCM in Android Keystore)
await device.biometric.saveSecure('token', 'abc123')
const token = await device.biometric.loadSecure('token')
```

---

## NFC

```js
import { device } from './csua.js'

device.nfc.read(tag => {
    console.log('NFC tag:', tag.id, tag.payload)
})

await device.nfc.write('Hello NFC')
```

---

## Bluetooth

```js
import { device } from './csua.js'

const devices = await device.bluetooth.scan()
const conn    = await device.bluetooth.connect(devices[0].address)
conn.send('hello')
conn.onReceive = data => console.log(data)
conn.close()
```

---

## Notifications

```js
import { device } from './csua.js'

await device.notify('Title', 'Message body', {
    id: 1,
    icon: 'notification_icon',
    channel: 'default',
    importance: 'high',
})
```

---

## Canvas Drawing

```js
import { Canvas } from './csua.js'

const canvas = new Canvas(root, { w: 400, h: 400 })

canvas.clear()
canvas.drawRect({ x: 10, y: 10, w: 100, h: 50, color: '#3b82f6' })
canvas.drawCircle({ x: 200, y: 200, r: 40, color: '#ef4444' })
canvas.drawLine({ x1: 0, y1: 0, x2: 400, y2: 400, color: '#fff', width: 2 })
canvas.drawText({ text: 'Hello', x: 50, y: 50, color: '#fff', size: 20 })
```

---

## App Lifecycle

```js
import { app } from './csua.js'

app.onPause(() => save('state', currentState))
app.onResume(() => { /* refresh UI */ })
app.onDestroy(() => { /* cleanup */ })
app.onBack(() => {
    // return true to prevent default back behaviour
    if (currentScreen !== 'home') { go('home'); return true; }
})
app.onDeepLink(url => go('product', { url }))
app.onMemoryWarning(() => { /* free caches */ })

app.exit()                // force quit
console.log(app.version) // '1.0.0'
console.log(app.package) // 'com.yourname.myapp'
app.devtools()            // open dev overlay programmatically
```

---

## Status Bar & Navigation Bar

```js
import { statusBar, navigationBar } from './csua.js'

statusBar.color('#1e293b')
statusBar.light()    // dark icons on light background
statusBar.dark()     // white icons on dark background
statusBar.hide()
statusBar.show()

navigationBar.color('#0f172a')
navigationBar.hide()
navigationBar.show()
```

---

## Plugin System

Same plugin API as csui.js — works on both platforms.

```js
// myPlugin.js
export const SpinnerPlugin = {
    name: 'spinner',
    install({ BaseElement, register }) {
        class Spinner extends BaseElement {
            constructor(ref, props = {}) {
                super(ref, 'ProgressBar')
                bridge.setProp(this._viewId, 'indeterminate', true)
                this._attach()
            }
        }
        register('Spinner', Spinner)
    }
}
```

```js
// app.js
import { use, ctrlscript } from './csua.js'
import { SpinnerPlugin } from './myPlugin.js'

use(SpinnerPlugin)
const { Spinner } = ctrlscript
new Spinner(root)
```

---

## Universal Code (Browser + Android)

The same `app.js` runs on both platforms. Unsupported APIs log a warning and return safe fallbacks.

```js
// This works on both csui (browser) and csua (Android):
import { Box, Label, Button, save, load, perm, share, dialog } from './csua.js'

const root = new Box()
new Label(root, { text: 'Works everywhere!' })
new Button(root, {
    text: 'Share',
    onPress: () => share({ text: 'Hello from CtrlScript!' })
})
```

---

## Dev Tools

**5-finger tap** anywhere on screen → opens the dev overlay showing:
- Package name & version
- QuickJS context pointer
- View count
- Module count
- Live JS eval console
- Reload JS button (hot reload without rebuild)

Or open programmatically: `app.devtools()`

---

## Hot Reload

During `csua run`, saving `app.js` automatically:
1. Pushes new `app.js` to `/sdcard/csua/` via ADB
2. Broadcasts `com.csua.RELOAD` intent
3. Bootstrap reloads JS without reinstalling the APK

---

## Native Library (libquickjs.so)

Pre-built for all 4 CPU architectures via GitHub Actions:

| ABI | Devices |
|---|---|
| `arm64-v8a` | All modern phones (2016+) |
| `armeabi-v7a` | Older 32-bit phones |
| `x86_64` | Android emulator (64-bit) |
| `x86` | Android emulator (32-bit) |

If `.so` files are missing, the CLI will error with instructions to run the GitHub Actions build.

---

## Architecture

```
app.js + csua.js
    │
    ▼
QuickJS (C engine, JS thread)
    │  bridge.createView / setProp / addChild / call
    ▼
Bootstrap.java (BridgeInterface)
    │  _ui.post() for all View operations
    ▼
Android UI Thread
    │
    ▼
Native Android Views (no WebView)
```

**Threading model:**
- JS runs on a dedicated `csua-js` thread
- All View operations are posted to the Android UI thread
- `createView` blocks the JS thread via `CountDownLatch` until the View is created
- Callbacks fire back to JS via `fireCallback` / `fireRawCallback`
