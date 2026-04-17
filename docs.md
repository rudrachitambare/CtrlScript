# CtrlScript (CSUI) — Documentation
> Single-file DOM framework. NativeScript-inspired, chainable, game-ready.

---

## Import

```js
// Named imports (recommended, tree-shakeable)
import { Box, App, Text, loop, save, sound } from './csui.js'

// Single namespace (easiest for beginners)
import { ctrlscript } from './csui.js'
const { Box, App, Text, loop } = ctrlscript

// Short alias
import { CS } from './csui.js'

// Optional heavy modules
import { engine2d, sound, particles } from './csui.js'
```

---

## Core Elements

Elements take `(containerIndexOrName, props)`. Pass `null` if not using containers.
**Recommended:** Use descriptive string names for containers instead of numbers.

```js
new App()                        // root app div → appends to document.body

// Containers (they register themselves)
new Box("home", { bg: "#111" })  // named lookup
new Box(1, { bg: "#111" })       // legacy numeric index

// Children (attaching to named container)
new Text("home", { text: "Hello" })   
new Label("home", { text: "Hello" })  // alias of Text
new Button("home", { text: "Click" })
new Input("home", { placeholder: "Name" })
new Image("home", { src: "img.png", w: 200 })
new Heading(2, "home", { text: "Title" })  // level, container, props
new Paragraph("home", { text: "..." })
new Link("home", { text: "Go", href: "/page" })
new Canvas("home", { w: 400, h: 300 })

// Shapes
new Rectangle("home", { w: 120, h: 80, bg: "blue" })
new Square("home", { size: 80, bg: "red" })
new Circle("home", { size: 60, bg: "green" })
new Ellipse("home")
new Line("home", { w: 200, bg: "#000" })
```

---

## Props Shorthand

Pass props in the constructor or call `.set({})`:

| Prop | CSS |
|------|-----|
| `bg` | `backgroundColor` |
| `c` | `color` |
| `w`, `h` | `width`, `height` |
| `p`, `m` | `padding`, `margin` |
| `mt`, `mb`, `ml`, `mr` | margin sides |
| `pt`, `pb`, `pl`, `pr` | padding sides |
| `br` | `borderRadius` |
| `o` | `opacity` |
| `z` | `zIndex` |
| `fs`, `fw`, `ff` | font size/weight/family |
| `ta` | `textAlign` |
| `d` | `display` |
| `bs` | `boxShadow` |
| `pos`, `position` | `position` |
| `name` | sets `id` + `data-name` for debugging |

---

## Chaining API

Every style shorthand is also a chainable method:

```js
box.bg("red").p(10).w(200).h(100).br(8).o(0.9)
box.c("#fff").fs(16).fw("bold").ff("monospace")
box.m(20).mt(10).mx(16).my(8)
box.bs("0 4px 12px rgba(0,0,0,0.3)")
```

---

## Positioning

```js
box.x(100)          // left: 100px (sets position:absolute)
box.x()             // read current x (returns number)
box.y(200)          // top: 200px
box.center()        // absolute center in parent (translate -50% -50%)
box.mx(20).mt(10)   // margin helpers
```

---

## Color Helpers

```js
box.bg("#ff0000")
box.bgRGB(255, 0, 0)
box.bgHSL(120, 100, 50)
box.c("white")
```

---

## Layout (Box methods)

```js
// Flex row — optionally pass children
new Box("header").row(labelA, labelB, btn)

// Flex column
new Box("form").col(input, button).gap(8).p(12)

// Just set direction (no children yet)
const nav = new Box("nav").row().bg("#111").p(10)
nav.addChild(logo)
nav.addChild(btn)

// Translate a box
box.move(100, 50)

// group() — position:relative wrapper for layering
const g = group(sprite, shadow)
g.move(200, 100)
```

---

## Events

```js
// Generic
el.on("click", fn)
el.on("mouseover", fn)

// Props syntax
new Button(1, { onClick: () => alert("hi") })

// Sugar
button.click(() => {})
input.onEnter(val => {})
input.onInput(val => console.log(val))
```

---

## Input Helpers

```js
const inp = new Input(1)
inp.get()           // returns current value
inp.set("hello")    // sets value
inp.onInput(v => {})
inp.onEnter(v => {})
```

---

## Animation

```js
// Animate any props
box.animate({ opacity: 0, x: 100, width: 200 }, 300)
box.animate({ backgroundColor: "blue" }, 500, "ease-in")

// Presets
box.fadeIn()          // opacity 0 → 1
box.fadeOut()         // opacity 1 → 0
box.slideLeft()       // slides in from right
box.slideRight()      // slides in from left
box.slideUp()         // slides in from below
box.slideDown()       // slides in from above

// Custom distance & duration
box.slideLeft(80, 400)
```

Animatable props: `opacity`, `x`, `y`, `scale`, `rotate`, `width`, `height`, `backgroundColor`, `color`

---

## Game System

### Game Loop
```js
const game = loop((dt) => {
    // dt = delta time in ms
    box.x(box.x() + 1)
})

game.stop()   // stop the loop
```

### Keyboard Input
```js
onKey("w", () => moveUp())
onKey("arrowleft", () => moveLeft())
onKey(" ", () => jump())           // spacebar
onKey("w", () => stopUp(), { up: true })  // keyup
```

### Collision
```js
if (collides(player, enemy)) {
    // AABB bounding box check
}
```

### Scene System
```js
scene("menu", () => {
    new Text(1, { text: "Press Start" })
})

scene("game", () => {
    // build game UI
})

go("menu")   // switch to scene
```

### Camera
```js
// camera.x / camera.y move a layer div
loop(dt => {
    camera.x += 2
})

// Attach things to the camera layer
camera.el.appendChild(mySprite.el)
```

---

## Timer Helpers

```js
after(1000, () => console.log("1 second later"))

const tick = every(16, () => update())
tick.stop()   // cancel interval
```

---

## Storage

```js
save("score", 100)
save("player", { name: "Rudra", hp: 100 })

const score  = load("score", 0)          // default: 0
const player = load("player", { name: "" })
```

---

## Prompt Helper

```js
const name = ask("Enter your name:")
const name = ask("Enter your name:", "Player1")  // with default
```

---

## Sound Module

```js
sound.load("hit",   "sounds/hit.ogg")
sound.load("music", "sounds/bg.mp3")

sound.play("hit")
sound.play("music", { loop: true, volume: 0.5 })
sound.stop("music")
sound.volume("hit", 0.8)
```

---

## Particles Module

```js
// CSS-based, no canvas required
particles.emit({
    x: 200,          // origin x
    y: 300,          // origin y
    count: 16,       // number of particles
    color: "#ffcc00",
    size: 8,         // px
    duration: 700,   // ms
    spread: 80,      // radius in px
})
```

---

## Physics (Matter.js)

Matter.js is lazy-loaded from CDN when `engine2d.init()` is called.

```js
import { engine2d } from './csui.js'

await engine2d.init({ gravity: { x: 0, y: 1 } })

// Attach physics to any element
const box = new Box(1, { w: 50, h: 50, bg: "red" })
box.physics({ mass: 1, bounce: 0.8, friction: 0.1 })

// Or manually
engine2d.attach(box, { mass: 2, isStatic: false })

// Add a static ground
engine2d.addGround(500)  // y position
```

Shorthand `.physics()` internally calls `engine2d.attach(this, opts)`.

---

## Dev Tools

### Debug Mode
Enables:
- Error overlay on `window.onerror` and unhandled promise rejections
- Console info logging

```js
csui.debug(true)
csui.isDebug   // boolean
```

### Strict Mode
```js
csui.strict(true)
csui.isStrict
```

### Element Highlighting
```js
box.highlight()          // red outline
box.highlight("#0f0")    // custom color
```

### Overlap Detection
```js
checkOverlap(boxA, boxB)  // warns in console if overlapping
```

### Manual Error Screen
```js
csui.showError("Something went wrong!")
```

---

## Named Elements

```js
new Box(1, { name: "player" })
// → sets id="player" and data-name="player"
// → used in debug output and overlap warnings
```

---

## Utility Functions

```js
remove(element)         // removes from DOM
clearContainers()       // resets container registry
getContainer(1)         // returns Box at index 1
```

---

## Label / Text Alias

`Label` and `Text` are the same class — use either:

```js
new Text(1, { text: "Hello" })
new Label(1, { text: "Hello" })  // identical
```

---

---

## Lifecycle Hooks

```js
const box = new Box("app")
  .onMount(() => console.log("mounted"))
  .onDestroy(() => console.log("cleanup"))

box.destroy()   // fires onDestroy callbacks + removes from DOM
```

---

## URL Router

Separate from the existing `scene()`/`go()` system. Uses `history.pushState`.

```js
router.define("/",       () => { /* render home */ })
router.define("/about",  () => { /* render about */ })
router.define("*",       path => { /* 404 fallback */ })

router.go("/about")      // navigate + render
router.back()            // history.back()
router.forward()         // history.forward()
router.current           // → location.pathname
```

---

## Device APIs

```js
const pos = await device.location()        // { lat, lng, accuracy }
device.vibrate(200)                        // or device.vibrate([100, 50, 100])
const stop = device.tilt((a, b, g) => {}) // returns cleanup fn
await device.notify("Title", "Body")
await device.clipboard.copy("hello")
const text = await device.clipboard.paste()
```

---

## UI Components

All components accept standard CSUI style props (`bg`, `c`, `br`, `bs`, `p`, `w`, `h`, etc.)  
plus named style props for their internal parts.

### DropdownMenu

```js
new DropdownMenu("app", {
  label: "Options",
  buttonBg: "#1d4ed8", buttonHoverBg: "#2563eb", buttonColor: "#fff",
  menuBg: "#1e1e2e", itemColor: "#e0e0e0", itemHoverBg: "#2d2d3e",
  radius: "8px", minWidth: "180px",
  items: [
    { label: "Profile", onClick: () => {} },
    { divider: true },
    { label: "Logout",  onClick: () => {} },
  ],
})
.addItem({ label: "Added later", onClick: () => {} })
.open() .close() .toggle()
menu.label = "New Label"
```

### Modal

```js
const m = new Modal("app", {
  title: "Confirm", content: "Are you sure?",
  overlayColor: "rgba(0,0,0,0.65)", dialogBg: "#1a1a2e",
  titleColor: "#fff", contentColor: "rgba(255,255,255,0.8)",
  radius: "12px", maxWidth: "500px",
  onClose: () => {}, closeOnOverlay: true, showClose: true,
})
m.open()
m.close()
m.title = "New Title"
m.content = "New content"      // string, BaseElement, or HTMLElement
```

### toast

```js
toast("Saved!", { type: "success", duration: 2500 })
toast("Error!", { type: "error",   position: "tl"  })   // tl tr bl br
toast("Custom", { bg: "#7c3aed",   color: "#fff", radius: "12px" })
```

### Tabs

```js
const tabs = new Tabs("app", {
  activeColor: "#fff", inactiveColor: "rgba(255,255,255,0.45)",
  indicatorColor: "#3b82f6", barBg: "transparent",
  tabs: [
    { label: "Home",    content: homeBox },
    { label: "Profile", content: profileBox },
  ],
})
tabs.show(1)          // switch to index 1
tabs.addTab({ label: "New", content: box })
tabs.active           // → current index
```

### Slider

```js
const s = new Slider("app", {
  min: 0, max: 100, value: 50, step: 1,
  trackColor: "rgba(255,255,255,0.12)", fillColor: "#3b82f6",
  thumbColor: "#fff", height: 6, thumbSize: 18,
})
s.onChange(v => console.log(v))
s.get()          // current value
s.set(75)        // set value
s.value          // getter/setter
```

### ProgressBar

```js
const bar = new ProgressBar("app", {
  value: 0, fillColor: "#3b82f6",
  trackColor: "rgba(255,255,255,0.1)",
  height: 8, radius: 999, animated: true, striped: false,
})
bar.set(75)      // animates to 75%
bar.get()        // → 75
```

### Toggle

```js
const t = new Toggle("app", {
  checked: false,
  trackColor: "rgba(255,255,255,0.15)", activeColor: "#3b82f6",
  thumbColor: "#fff", size: 1,       // size multiplier
  onChange: on => console.log(on),
})
t.onChange(on => {})
t.set(true)
t.get()          // → boolean
t.checked        // getter/setter
```

### Accordion

```js
const acc = new Accordion("app", {
  headerBg: "#1e1e2e", headerHoverBg: "#2d2d3e",
  bodyBg: "#16162a", radius: "8px", multi: false,
  items: [
    { title: "Section 1", content: "Text or BaseElement", open: true },
    { title: "Section 2", content: anotherBox },
  ],
})
acc.addItem({ title: "Dynamic", content: "Added later" })
```

### Drawer

```js
const drawer = new Drawer("app", {
  side: "left",          // left | right | top | bottom
  size: "280px",
  bg: "#1a1a2e", overlayColor: "rgba(0,0,0,0.6)",
  content: myBox,        // BaseElement or HTMLElement
  onClose: () => {},     closeOnOverlay: true,
})
drawer.open()
drawer.close()
drawer.setContent(newBox)
```

### Chip

```js
new Chip("app", {
  text: "TypeScript",
  bg: "rgba(59,130,246,0.2)", color: "#93c5fd",
  border: "1px solid rgba(59,130,246,0.3)",
  radius: "999px", fontSize: "12px",
  removable: true, onRemove: chip => chip.destroy(),
})
chip.text = "New Label"
```

### Card

```js
new Card("app", {
  title: "Card Title", content: "Body text or BaseElement",
  footer: "Footer content",
  bg: "#1a1a2e", radius: "12px", padding: "20px",
  titleColor: "#fff", contentColor: "rgba(255,255,255,0.75)",
  shadow: "0 4px 24px rgba(0,0,0,0.3)",
})
card.title = "New Title"
card.setContent(newBox)
card.body          // → the body div element
```

---

## Prototype Helpers

### .tooltip(text, opts)

```js
box.tooltip("Hello!", {
  pos: "top",            // top | bottom | left | right
  bg: "rgba(0,0,0,0.85)", color: "#fff",
  fontSize: "12px", radius: "6px",
  delay: 200, padding: "6px 10px",
})
```

### .badge(count, opts)

```js
btn.badge(5, { bg: "#ef4444", color: "#fff", size: 18, fontSize: "11px" })
btn.badge(0)   // removes badge
```

### .contextMenu(items, opts)

```js
box.contextMenu([
  { label: "Copy",   onClick: () => {} },
  { divider: true },
  { label: "Delete", onClick: () => {} },
], { bg: "#1e1e2e", itemHoverBg: "#2d2d3e", radius: "8px" })
```

---

## Glassmorphism

```js
// Lazy — semi-transparent background only
box.glass({ opacity: 0.15, borderColor: "rgba(255,255,255,0.2)" })

// Professional — backdrop-filter blur (real glassmorphism)
box.glass({
  pro: true,
  blur: 12,                              // px
  opacity: 0.12,                         // background alpha
  saturation: 1.8,                       // backdrop saturation multiplier
  borderColor: "rgba(255,255,255,0.18)",
  shadowColor: "rgba(0,0,0,0.25)",
  border: true, shadow: true,
  bg: null,                              // override bg entirely if needed
})
```

> Note: `pro: true` requires the element to be over a visible background.  
> Wrap in a div with `position: relative` if blur doesn't appear.

---

## Color Palette Generator

Generates a Figma-style 10-shade palette. Logs swatches, CSS vars, and JS object to console.

```js
import { palette } from './csui.js'

palette("#3b82f6")
palette("#3b82f6", { name: "brand" })
palette("hsl(217,91%,60%)", { name: "primary", shades: [100,300,500,700,900] })

const result = palette("#059669", { name: "green", display: true })
// result → { 50: "#f0fdf4", 100: "#dcfce7", ..., 900: "#14532d" }
// Logs swatches + CSS vars (:root { --green-50: ... }) + JS object
```

---

## Plugin System

Register custom components from `csui.js` or any user file.

```js
// myPlugin.js
import { csui, BaseElement } from './csui.js'

const SpinnerPlugin = {
  name: 'spinner',
  install({ BaseElement, register, addProto }) {
    class Spinner extends BaseElement {
      constructor(containerRef = null, props = {}) {
        const { color = '#3b82f6', size = 32, ...rest } = props
        super(containerRef)
        this.el = document.createElement('div')
        this.el.style.cssText = `width:${size}px;height:${size}px;border-radius:50%;
          border:3px solid rgba(255,255,255,0.1);border-top-color:${color};
          animation:spin 0.75s linear infinite`
        this._attachToContainer()
        if (Object.keys(rest).length) this.props = rest
      }
    }
    register('Spinner', Spinner)                     // → ctrlscript.Spinner
    addProto(BaseElement, 'spin', function() {        // → any el.spin()
      this.el.style.animation = 'spin 0.75s linear infinite'
      return this
    })
  }
}

csui.use(SpinnerPlugin)
// or: use(SpinnerPlugin)   (named import)
```

```js
// main.js
import './myPlugin.js'
import { ctrlscript } from './csui.js'
const { Spinner } = ctrlscript
new Spinner("app", { color: "#f59e0b", size: 40 })
```

`install` receives:
| Arg | Purpose |
|---|---|
| `BaseElement` | Base class to extend |
| `Box` | Box class |
| `register(name, Cls)` | Adds `Cls` to `ctrlscript[name]` |
| `addProto(Target, name, fn)` | Adds method to any existing class prototype |
| `containers`, `containersByName` | Internal registries |

---

## Quick Example

```js
import { App, Box, Text, Button, loop, onKey, save, load, csui } from './csui.js'

csui.debug(true)

const appScreen = new Box("appScreen", { bg: "#1a1a2e", fullPage: true })

const score = load("score", 0)
const scoreText = new Text("appScreen", { text: `Score: ${score}`, c: "#fff", fs: 24 })

const player = new Box("appScreen", { w: 50, h: 50, bg: "#e94560", name: "player" })
player.x(100).y(300)

let vel = 0
onKey("arrowup", () => vel = -5)
onKey("arrowdown", () => vel = 5)

loop(() => {
    player.y(player.y() + vel)
    save("score", score)
})
```
