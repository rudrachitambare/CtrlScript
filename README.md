# CSUI (CtrlScript)

**A lightweight, no-build UI system for browser-based apps and game interfaces.**
Built for simplicity, performance, and full control — without heavy frameworks.

---

## ⚡ Why CSUI?

CSUI is for when:

* frameworks feel like overkill
* vanilla JavaScript gets messy
* you want full control without abstraction

👉 **No build tools. No complex setup. Just code and go.**

---

## 🚀 Quick Example

```js
const app = new Box({ id: "app" })

const label = new Label(app, {
  text: "Hello World",
  fs: "20px",
  color: "white"
})
```

---

## 🧠 Features

* 🧱 Simple component system (Label, Box, etc.)
* ⚡ Direct DOM rendering (no virtual DOM, predictable behavior)
* 🧾 Clean props-based API
* 🔤 Shorthand props (`bg`, `w`, `h`, `fs`, etc.)
* 🔁 Built-in loop system (for real-time updates / games)
* 🔌 Modular system (physics, sound, etc. planned)
* 🧩 Alias support (`Text` = `Label`)
* 📦 Single-file friendly architecture
* 🛠 No build tools required

---

## 🎮 Built For

* Browser-based game UIs
* Lightweight apps and tools
* Fast prototyping
* Low-end devices
* Developers who want control without complexity

---

## ⚙️ Containers

CSUI supports multiple ways to mount elements:

### ✅ Recommended (object reference)

```js
const app = new Box()
new Label(app, { text: "Hello" })
```

### ✅ Named containers

```js
const app = new Box({ id: "app" })
new Label("app", { text: "Hello" })
```

### ⚠️ Numeric containers (legacy)

```js
new Label(1, { text: "Hello" })
```

> Numeric containers are supported for low-level usage but not recommended.

---

## 🔄 Updating UI

### Using props (partial update)

```js
label.props = { text: "Updated text" }
```

### Direct property updates (faster & cleaner)

```js
label.text = "Updated text"
label.fs = "24px"
label.color = "red"
```

👉 Direct updates are ideal for frequent changes (like scores, health bars, etc.)

---

## 🔁 Loop (Real-Time Updates)

CSUI includes a built-in loop system for dynamic UI:

```js
const score = new Label(app, { text: "Score: 0" })

let value = 0

loop(() => {
  value++
  score.text = "Score: " + value
})
```

👉 Perfect for game UIs and constantly updating interfaces.

---

## ⚔️ Why Not Vanilla JS?

Vanilla JS:

* repetitive DOM handling
* messy structure in larger UIs
* no built-in patterns

CSUI:
👉 structured, readable, and faster to work with

---

## ⚖️ Why Not Big Frameworks?

Frameworks like React/Vue:

* require setup and tooling
* add abstraction layers
* can be overkill for small/medium projects

CSUI:
👉 gives structure **without the overhead**

---

## 🎯 Philosophy

* Keep it simple
* Keep it fast
* No unnecessary abstraction
* Developer stays in control

---

## ⚖️ Comparison

| Approach       | Problem                |
| -------------- | ---------------------- |
| Vanilla JS     | Messy, repetitive      |
| Big frameworks | Heavy, complex         |
| **CSUI**       | Simple, fast, balanced |

---

## 📦 Status

🚧 Work in progress — actively being improved and optimized.

---

## 🧠 Final Note

> **CSUI is the best choice when frameworks feel like overkill and you just want fast, simple UI.**

---
