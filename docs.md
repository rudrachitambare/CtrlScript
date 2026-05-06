# CSUI — Documentation

A single-file DOM framework for building UIs in the browser (and Android via csua). No build step. No dependencies. Just drop in `csui.js` and go.

---

## Quick Start

```html
<script type="module">
  import { Box, Text, Button } from './csui.js'

  const app = new Box({ bg: '#111', p: 24 })
  new Text(app, { text: 'Hello!', c: '#fff', fs: 32 })
  new Button(app, { text: 'Click me' }).click(() => alert('Hi'))
</script>
```

Or grab everything from the namespace:

```js
import { ctrlscript as CS } from './csui.js'
const { Box, Text, Button, loop, save } = CS
```

Everything is also exposed as globals, so scripts loaded after `csui.js` need zero imports.

---

## Containers & Attachment

Every element takes an optional first argument: a **container reference**.

```js
const root = new Box()                     // no parent → root element
const card = new Box(root)                 // attach to Box instance
const item = new Text(root, { text: 'Hi' })

// Named containers (recommended for larger apps)
const nav = new Box('nav')                 // registers as "nav"
new Button('nav', { text: 'Home' })        // attaches to it by name
```

If exactly one container exists, elements without a ref attach to it automatically.

---

## Style Props

All elements accept a props object. Shorthands map directly to CSS:

| Prop | CSS | Prop | CSS |
|------|-----|------|-----|
| `bg` | backgroundColor | `c` | color |
| `fs` | fontSize | `fw` | fontWeight |
| `w` | width | `h` | height |
| `p` | padding | `m` | margin |
| `mt` `mb` `ml` `mr` | margin edges | `pt` `pb` `pl` `pr` | padding edges |
| `br` | borderRadius | `bs` | boxShadow |
| `d` | display | `fd` | flexDirection |
| `jc` | justifyContent | `ai` | alignItems |
| `ta` | textAlign | `ff` | fontFamily |
| `lh` | lineHeight | `gap` | gap |
| `z` | zIndex | `o` | opacity |
| `cur` | cursor | `pos` | position |

Numbers auto-convert to `px`. Strings pass through as-is.

```js
new Box(root, { bg: '#1a1a2e', p: 24, br: 12, w: '100%' })
```

**Special props:**

```js
{ text: 'hello' }           // sets textContent
{ html: '<b>bold</b>' }     // sets innerHTML
{ src: './img.png' }        // sets src
{ href: 'https://...' }     // sets href
{ rotation: 45 }            // CSS rotate transform
{ size: 80 }                // sets width + height together
{ name: 'my-box' }          // sets id + data-name (debug label)
{ fullPage: true }          // width: 100%, minHeight: 100vh
{ pos: { x: 10, y: 20 } }  // position: absolute + left/top
{ children: [a, b, c] }    // appends child elements
{ onClick: fn }             // addEventListener('click', fn)
{ onMouseenter: fn }        // any on* key → addEventListener
```

---

## Elements

### Core

| Class | HTML | Notes |
|-------|------|-------|
| `Box` | `<div>` | Main container. Aliases: `Container`, `Div` |
| `App` | `<div#app>` | Mounts to body, resets margin/padding |
| `Text` / `Label` | `<span>` | `.text` getter/setter |
| `Button` | `<button>` | `.text`, `.click(fn)` |
| `Input` | `<input>` | `.value`, `.placeholder`, `.type` |
| `TextArea` | `<textarea>` | `.value`, `.placeholder` |

### Content

| Class | HTML |
|-------|------|
| `Heading` | `<h1>`–`<h6>` — `new Heading(2, ref, props)` |
| `Paragraph` | `<p>` |
| `Link` | `<a>` — `.href`, `.target`, `.text` |
| `Span` | `<span>` |
| `Code` | `<code>` |
| `CodeBlock` | `<pre><code>` — styled dark block, `.text` setter |
| `Pre` | `<pre>` |

### Layout

| Class | HTML |
|-------|------|
| `Section` | `<section>` |
| `Article` | `<article>` |
| `Header` | `<header>` |
| `Footer` | `<footer>` |
| `Nav` | `<nav>` |
| `Form` | `<div>` |
| `HR` | `<hr>` |
| `BR` | `<br>` |

### Lists & Tables

```js
const ul = new List(ref)              // unordered (default)
const ol = new List(true, ref)        // ordered
new ListItem(ul, { text: 'Item' })

const t   = new Table(ref)
const row = new TableRow(t)
new TableCell(row, { text: 'Cell' })
new TableCell(true, row, { text: 'Header' })  // isHeader: true
```

### Form Controls

```js
const sel = new Select(ref)
sel.addOption('val', 'Label')
sel.value   // current value

new Checkbox(ref, { checked: true })
new Radio(ref, { name: 'group' })
```

### Media

```js
new Image(ref, { src: './photo.jpg', size: 200 })
new Video(ref, { src: './clip.mp4', controls: true })
new Audio(ref, { src: './track.mp3' })
new Iframe(ref, { src: 'https://example.com', w: 600, h: 400 })
new Canvas(ref, { w: 800, h: 600 })
```

### Shapes

```js
new Rectangle(ref, { w: 120, h: 80, bg: '#e74c3c' })
new Square(ref, { size: 60, bg: '#3b82f6' })
new Circle(ref, { size: 60, bg: '#22c55e' })
new Ellipse(ref, { w: 140, h: 90, bg: '#a855f7' })
new Line(ref, { w: 200, bg: '#fff' })
line.thickness = 3
```

---

## Chaining API

All elements support a fluent chaining API:

```js
new Box(root)
  .bg('#111').p(24).br(12).w('100%')
  .row()

new Text(root)
  .c('#fff').fs(18).fw(700).ta('center').mx('auto')
```

| Method | CSS | Method | CSS |
|--------|-----|--------|-----|
| `.bg(v)` | backgroundColor | `.c(v)` | color |
| `.w(v)` | width | `.h(v)` | height |
| `.p(v)` | padding | `.m(v)` | margin |
| `.mt/mb/ml/mr(v)` | margin edges | `.pt/pb/pl/pr(v)` | padding edges |
| `.mx(v)` | left+right margin | `.my(v)` | top+bottom margin |
| `.px(v)` | left+right padding | `.py(v)` | top+bottom padding |
| `.br(v)` | borderRadius | `.bs(v)` | boxShadow |
| `.fs(v)` | fontSize | `.fw(v)` | fontWeight |
| `.ff(v)` | fontFamily | `.ta(v)` | textAlign |
| `.d(v)` | display | `.o(v)` | opacity |
| `.z(v)` | zIndex | `.cur(v)` | cursor |
| `.x(v)` | left (absolute) | `.y(v)` | top (absolute) |
| `.bgRGB(r,g,b)` | rgb() color | `.bgHSL(h,s,l)` | hsl() color |
| `.center()` | absolute center in parent | `.highlight(color)` | debug outline |

---

## Lifecycle

```js
const box = new Box(root)

box.onMount(() => console.log('in the DOM'))
box.onDestroy(() => console.log('being removed'))
box.destroy()   // fires onDestroy, removes from DOM
```

---

## Layout

### Box methods

```js
box.row(a, b, c)   // flex row, optionally appends children
box.col(a, b, c)   // flex column, optionally appends children
box.move(x, y)     // translate(x, y)
```

### group()

Creates a `position: relative` wrapper — useful for layering:

```js
const scene = group(background, player, hud)
root.addChild(scene)
```

---

## Animation

```js
el.animate({ opacity: 0, x: 100 }, 300, 'ease-out')
el.animate({ backgroundColor: '#f00', width: 200 }, 500)

el.fadeIn(300)
el.fadeOut(300)
el.slideLeft(40, 300)    // slides in from the right
el.slideRight(40, 300)   // slides in from the left
el.slideUp(40, 300)
el.slideDown(40, 300)
```

Animatable keys: `opacity`, `x`, `y`, `scale`, `rotate`, `width`, `height`, `backgroundColor`, `color`.

---

## Game System

### Loop

```js
const handle = loop((dt) => {
  player.x(player.x() + speed * dt / 16)
})

handle.stop()
```

### Keyboard

```js
onKey('arrowleft', () => player.x(player.x() - 5))
onKey('space', () => jump())                // keydown (default)
onKey('space', () => land(), { up: true })  // keyup
```

### Collision

```js
if (collides(player, enemy)) {
  // AABB — works with BaseElement or HTMLElement
}
```

### Scenes

```js
scene('menu', () => {
  new Button(null, { text: 'Play', onClick: () => go('game') })
})

scene('game', () => { /* game setup */ })

go('menu')
```

### Camera

```js
camera.x = 200   // pan right
camera.y += 5    // pan down
```

---

## Timers

```js
after(1000, () => console.log('1s later'))

const t = every(500, () => tick())
t.stop()
```

---

## Storage

Thin `localStorage` wrappers with JSON serialization:

```js
save('user', { name: 'Rudra', score: 100 })

const user = load('user', { name: 'Guest' })  // second arg is default
```

---

## Sound

```js
sound.load('hit', './hit.ogg')
sound.load('music', './music.mp3')

sound.play('hit')
sound.play('music', { loop: true, volume: 0.5 })
sound.stop('music')
sound.volume('hit', 0.8)
```

---

## Particles

CSS-only, no canvas required:

```js
particles.emit({
  x: 200, y: 300,
  count: 16,
  color: '#f59e0b',
  size: 8,
  duration: 600,
  spread: 80,
})
```

---

## Physics (engine2d)

Lazy-loads Matter.js from CDN:

```js
engine2d.init({ gravity: { x: 0, y: 1 } }).then(() => {
  engine2d.attach(ball, { mass: 1, bounce: 0.8 })
  engine2d.addGround(window.innerHeight - 20)
})

// or shorthand on any element:
ball.physics({ mass: 1, bounce: 0.5, friction: 0.1 })
```

---

## Router

URL-based routing. Leaves `scene()`/`go()` untouched.

```js
router.define('/', () => showHome())
router.define('/about', () => showAbout())
router.define('*', (path) => show404(path))  // fallback

router.go('/about')
router.go('/user', { id: 1 })  // optional state payload
router.back()
router.forward()
router.current               // → location.pathname
```

Browser back/forward buttons fire handlers automatically via `popstate`.

---

## Device APIs

```js
// Geolocation
const { lat, lng, accuracy } = await device.location()

// Vibration
device.vibrate(200)                // ms, or pattern [200, 100, 200]

// Tilt
const stop = device.tilt((alpha, beta, gamma) => { /* ... */ })
stop()

// Notifications
await device.notify('Title', 'Body text')

// Clipboard
await device.clipboard.copy('hello')
const text = await device.clipboard.paste()
```

---

## UI Components

### DropdownMenu

```js
const menu = new DropdownMenu(root, {
  label: 'Options',
  items: [
    { label: 'Edit',   onClick: () => edit() },
    { divider: true },
    { label: 'Delete', onClick: () => del() },
  ],
  buttonBg: '#3b82f6', menuBg: '#1e1e1e',
  itemColor: '#eee', itemHoverBg: '#333', radius: '8px',
})

menu.open() / menu.close() / menu.toggle()
menu.addItem({ label: 'New', onClick: fn })
menu.label = 'Renamed'
```

### Modal

```js
const m = new Modal(null, {
  title: 'Confirm', content: 'Are you sure?',
  onClose: () => {},
  overlayColor: 'rgba(0,0,0,0.7)',
  dialogBg: '#1a1a2e', radius: '16px', maxWidth: '400px',
})

m.open() / m.close()
m.title   = 'New Title'
m.content = 'Updated text'  // string, BaseElement, or HTMLElement
```

### toast

```js
toast('Saved!',         { type: 'success' })
toast('Error occurred', { type: 'error', duration: 4000 })
toast('FYI',            { type: 'info',  position: 'tl' })  // tl tr bl br
toast('Warning',        { type: 'warning', bg: '#f59e0b', color: '#000' })
```

### Tabs

```js
const tabs = new Tabs(root, {
  tabs: [
    { label: 'Home',     content: homeBox },
    { label: 'Settings', content: 'Plain text works too' },
  ],
  activeColor: '#fff', inactiveColor: 'rgba(255,255,255,0.4)',
  indicatorColor: '#3b82f6',
})

tabs.show(1)
tabs.addTab({ label: 'New', content: newBox })
tabs.active  // → current index
```

### Slider

```js
const s = new Slider(root, {
  min: 0, max: 100, value: 50, step: 1,
  fillColor: '#3b82f6', thumbColor: '#fff',
  trackColor: 'rgba(255,255,255,0.1)',
})

s.onChange(val => console.log(val))
s.value = 75
s.set(25) / s.get()
```

### ProgressBar

```js
const bar = new ProgressBar(root, {
  value: 0, fillColor: '#22c55e',
  height: 8, animated: true, striped: false,
})

bar.set(60)  // 0–100
bar.value = 100
```

### Toggle

```js
const t = new Toggle(root, {
  checked: false,
  activeColor: '#3b82f6', thumbColor: '#fff', size: 1,
})

t.onChange(val => console.log(val))
t.set(true) / t.get()
t.checked  // → boolean
```

### Accordion

```js
const acc = new Accordion(root, {
  multi: false,
  items: [
    { title: 'Section 1', content: 'Body text', open: true },
    { title: 'Section 2', content: someElement },
  ],
  headerBg: '#1e1e2e', bodyBg: '#16162a', radius: '8px',
})

acc.addItem({ title: 'New', content: 'Text' })
```

### Drawer

```js
const drawer = new Drawer(null, {
  side: 'left',   // left right top bottom
  size: '300px',
  bg: '#1a1a2e', content: myPanel,
  onClose: () => {},
})

drawer.open() / drawer.close()
drawer.setContent(newPanel)
```

### Chip

```js
new Chip(root, {
  text: 'TypeScript',
  bg: 'rgba(59,130,246,0.2)', color: '#93c5fd',
  onRemove: (chip) => chip.destroy(),
})
```

### Card

```js
new Card(root, {
  title: 'Card Title',
  content: 'Body text or a BaseElement',
  footer: 'Footer text',
  bg: '#1a1a2e', radius: '12px',
})
```

---

## Prototype Helpers

Available on every element.

### .tooltip()

```js
el.tooltip('Helpful hint', {
  pos: 'top',   // top bottom left right
  delay: 300,
  bg: 'rgba(0,0,0,0.85)', color: '#fff',
})
```

### .badge()

```js
el.badge(5)                      // red dot with count
el.badge(99, { bg: '#22c55e' })
el.badge(0)                      // removes badge
```

### .contextMenu()

```js
el.contextMenu([
  { label: 'Copy',   onClick: () => copy() },
  { divider: true },
  { label: 'Delete', onClick: () => del() },
])
```

### .glass()

```js
el.glass()                        // semi-transparent bg
el.glass({ pro: true })           // + backdrop-filter blur
el.glass({ pro: true, blur: 16, opacity: 0.15, saturation: 2 })
```

---

## SVG

```js
const svg = new Svg(root, { w: 400, h: 300, bg: '#0f0f1a' })

// Shapes (all accept standard SVG attributes)
new SvgRect(svg,     { x: 10, y: 10, width: 80, height: 50, fill: '#3b82f6', rx: 8 })
new SvgCircle(svg,   { cx: 200, cy: 150, r: 40, fill: '#22c55e' })
new SvgEllipse(svg,  { cx: 100, cy: 100, rx: 60, ry: 30, fill: '#a855f7' })
new SvgLine(svg,     { x1: 0, y1: 0, x2: 400, y2: 300, stroke: '#fff', strokeWidth: 2 })
new SvgPath(svg,     { d: 'M10 10 L200 100 Z', stroke: '#f59e0b', fill: 'none' })
new SvgPolygon(svg,  { points: '0,0 100,0 50,80', fill: '#a855f7' })
new SvgPolyline(svg, { points: '0,0 50,50 100,0', stroke: '#fff', fill: 'none' })
new SvgText(svg,     { x: 50, y: 80, text: 'Hello SVG', fill: '#fff', fontSize: 20 })

// Update any shape live
const rect = new SvgRect(svg, { x: 10, y: 10, width: 80, height: 50, fill: '#3b82f6' })
rect.set({ fill: '#ef4444', rx: 16 })
rect.remove()

// Groups
const g = new SvgGroup(svg, { transform: 'translate(50, 50)' })
new SvgCircle(g, { cx: 0, cy: 0, r: 20, fill: '#fff' })

svg.clear()   // remove all shapes
svg.render()  // no-op in browser (live DOM) — exists for Android API compat
```

---

## Color Palette

Generates a 10-shade palette from any color, printed to the console:

```js
const colors = palette('#3b82f6')
// → { 50: '#eff6ff', 100: '...', ..., 900: '#1e3a5f' }

palette('#3b82f6', {
  name: 'brand',
  shades: [100, 300, 500, 700, 900],
  display: true,   // log to console (default: true)
})
```

CSS variables and a JS object are printed automatically — just copy from the console.

---

## Plugin System

Extend csui with reusable modules:

```js
const SpinnerPlugin = {
  name: 'spinner',
  install({ BaseElement, register }) {
    class Spinner extends BaseElement {
      constructor(ref, props = {}) {
        const { color = '#3b82f6', size = 32, ...rest } = props
        super(ref)
        this.el = document.createElement('div')
        this.el.style.cssText = `
          width:${size}px; height:${size}px; border-radius:50%;
          border:3px solid #333; border-top-color:${color};
          animation:_spin 0.7s linear infinite
        `
        if (!document.getElementById('_spinKF')) {
          const s = document.createElement('style')
          s.id = '_spinKF'
          s.textContent = '@keyframes _spin{to{transform:rotate(360deg)}}'
          document.head.appendChild(s)
        }
        this._attachToContainer()
        if (Object.keys(rest).length) this.props = rest
      }
    }
    register('Spinner', Spinner)
  }
}

csui.use(SpinnerPlugin)

const { Spinner } = ctrlscript
new Spinner(root, { color: '#f59e0b', size: 48 })
```

**`install` receives:**

| Key | Description |
|-----|-------------|
| `BaseElement` | Base class to extend |
| `Box` | Box class |
| `register(name, Cls)` | Adds `Cls` to `ctrlscript[name]` |
| `addProto(Target, name, fn)` | Adds method to any class prototype |
| `containers` | Internal container registry (array) |
| `containersByName` | Named container registry (object) |

---

## Dev Tools

```js
csui.debug(true)                // red overlay on uncaught errors
csui.showError('Custom error')  // trigger overlay manually
csui.isDebug                    // → boolean
```

---

## Android Compat Layer

These APIs are stubs in the browser — they let the same `app.js` run unmodified on both targets.

| API | Browser behavior |
|-----|-----------------|
| `dialog.alert / confirm / prompt` | native browser dialogs |
| `app.onPause / onResume / onBack` | window blur / focus / popstate |
| `share({ text, title, url })` | Web Share API or prompt fallback |
| `openUrl(url)` | `window.open` |
| `openMaps({ lat, lng })` | opens Google Maps |
| `perm.request('camera')` | Permissions API |
| `save / load` | `localStorage` |
| `onTouch(el, fn)` | mousedown + touchstart |
| `onSwipe(el, fn)` | touch direction detection |
| `onPinch(el, fn)` | pinch scale detection |
| `SafeArea` | Box with `env(safe-area-inset-*)` padding |
| `ScrollBox` | Box with `overflow: auto` |
| `Sound` | wraps browser `Audio` |
| `cancel(id)` | `clearTimeout` + `clearInterval` |
| `clearAll()` | `localStorage.clear()` |
| `db.run / query / get` | warns — Android SQLite only |
| `files.read / write / etc` | warns — Android only |
| `keyboard / statusBar / navigationBar` | warns — Android only |

---

## Utility Functions

```js
remove(el)              // removes element from DOM
clearContainers()       // reset all registries + ID counter
getContainer('nav')     // get by name or index
listContainers()        // → { named: {...}, indexed: [...] }
checkOverlap(a, b)      // AABB check + console warning if overlapping
collides(a, b)          // AABB check, no warning
ask('Your name?')       // window.prompt wrapper
```

---

## Namespace

```js
import { ctrlscript } from './csui.js'   // full namespace object
import { CS }         from './csui.js'   // short alias

// Tree-shakeable named imports:
import { Box, loop, save, router, device, toast } from './csui.js'
```
