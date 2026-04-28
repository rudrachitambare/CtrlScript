# CtrlScript

**One codebase. Browser and Android. No setup. No BS.**

```js
import { App, Box, Text, Button, toast } from './csui.js'

const app = new App()
const card = new Box(app, { bg: '#111', br: 12, p: 20 })
new Text(card, { text: 'Hello world', fs: 24, color: '#fff' })
new Button(card, { text: 'Click' }).click(() => toast('🔥', { type: 'success' }))
```

No HTML. No CSS files. No npm. No config.

**The entire library is a single file. Download `csui.js`, drop it in your project folder, import it, and you're done.** No install step. No terminal. No build tools. Nothing else needed.

```
your-project/
├── csui.js       ← this is all you need
├── index.html
└── app.js
```

---

## The problem

This is what building a UI in vanilla JS looks like:

```js
const card = document.createElement('div')
card.style.backgroundColor = '#111'
card.style.borderRadius = '12px'
card.style.padding = '20px'

const title = document.createElement('span')
title.textContent = 'Hello world'
title.style.fontSize = '24px'
title.style.color = '#fff'
card.appendChild(title)

const btn = document.createElement('button')
btn.textContent = 'Click'
btn.addEventListener('click', () => alert('🔥'))
card.appendChild(btn)
document.body.appendChild(card)
```

This is what it looks like with CtrlScript:

```js
const app = new App()
const card = new Box(app, { bg: '#111', br: 12, p: 20 })
new Text(card, { text: 'Hello world', fs: 24, color: '#fff' })
new Button(card, { text: 'Click' }).click(() => toast('🔥'))
```

Same result. A fraction of the code. And it only gets better from here.

---

## What's inside

All of this ships in a single file:

- **UI components** — Modal, Tabs, Drawer, Toast, Accordion, Card, Chip, Slider, ProgressBar, Toggle, Dropdown
- **Game system** — delta-time loop, scene manager, camera, keyboard input, AABB collision
- **Physics** — Matter.js, lazy-loaded from CDN, one line to enable
- **Animations** — `fadeIn`, `slideUp`, `animate()`, full chaining API
- **Particles** — CSS-based burst effects, no canvas needed
- **Sound** — load, play, stop, loop, volume
- **Router** — URL-based, `define()` and `go()`
- **Device APIs** — geolocation, vibration, gyro, clipboard, notifications
- **Storage** — `save()` / `load()` backed by localStorage
- **Glassmorphism** — `.glass()` / `.glass({ pro: true })`
- **Tooltips, badges, context menus** — one method call on any element
- **Color palette generator** — generate full shade palettes from any color
- **Plugin system** — extend any element or add global utilities

---

## Chaining API

Every element supports a full chaining API:

```js
new Box(app)
  .bg('#0f0f1a')
  .w(320).h(200)
  .br(16).p(20)
  .center()
  .glass({ pro: true, blur: 16 })
  .tooltip('Hello', { pos: 'top' })
  .highlight()
```

---

## Game in 15 lines

```js
import { App, Box, loop, onKey, collides, particles, toast } from './csui.js'

const app = new App({ bg: '#000', w: '100vw', h: '100vh', pos: 'relative' })
const player = new Box(app, { w: 40, h: 40, bg: '#3b82f6', pos: 'absolute' })
const enemy  = new Box(app, { w: 40, h: 40, bg: '#ef4444', pos: 'absolute' }).x(300).y(200)

let px = 100, py = 300
onKey('arrowleft',  () => px -= 5)
onKey('arrowright', () => px += 5)
onKey('arrowup',    () => py -= 5)
onKey('arrowdown',  () => py += 5)

loop(dt => {
  player.x(px).y(py)
  if (collides(player, enemy)) {
    particles.emit({ x: px, y: py, color: '#3b82f6', count: 20 })
    toast('Hit!', { type: 'error' })
  }
})
```

---

## Drop in and go

```html
<script type="module">
  import { App, Box, Text } from './csui.js'

  const app = new App()
  new Text(app, { text: 'Running.', fs: 32, color: '#fff' })
</script>
```

No terminal. No `node_modules`. Works offline. Works on low-end devices.

---

## csui-A — Android Runtime

> 🚧 Early work in progress

`csui-A` is CtrlScript's Android runtime, living right here in the same repo. It runs your JS natively on Android using **QuickJS + JNI** -> no WebView, no Electron, no Capacitor.

The goal: **write once, run on browser and Android natively.** Same API, same syntax, same codebase.

```js
// This runs identically on both web and Android
import { App, Box, Text, device } from './csui.js' // or csua.js on Android

const app = new App()
new Text(app, { text: 'Hello from anywhere', fs: 24 })

device.vibrate(200)           // → navigator.vibrate() on web
                              // → Android Vibrator API on Android

statusBar.color('#1a1a2e')    // → no-op on web (graceful)
                              // → native status bar on Android
```

When you call a platform-specific API, CtrlScript automatically bridges it to the right implementation. On web, Android-only calls gracefully no-op or fall back to a browser equivalent. No `if (platform === 'android')` checks. No separate codebases.

**csui-A stack:** QuickJS (JS engine) → JNI bridge → Android Canvas / Views / native modules

Planned modules: Camera, Bluetooth, NFC, Sensors, Biometric, Intents, Notifications, and more.

---

## Status

| | |
|---|---|
| csui (web) | 🟢 Core solid, actively evolving |
| csui-A (Android) | 🟡 Early WIP, architecture in place |
| Docs site | 🟢 |
| Playground/Web IDE for dev | 🟢 UI overhaul underway |
| Interactive playground | 🔨 In progress |
> The web CSUI is made from purely javascript. Other languages are for csui-a (Android)
---

## Where to try or learn CtrlScript

On my website -> which is not published as of now

RRudra.dev/ctrlscript/

(please dont steal the name rudra.dev was taken)
---

> Built by a developer who wanted full control without the overhead.  
> If you feel the same way, this is for you.
