// ── Your app goes here ───────────────────────────────
// This is the only file you write.
// Import anything from csua.js — same syntax as CSUI web.

import { Box, Label, Button, toast, device, loop } from './csua.js'

const screen = new Box({ col: true, bg: '#0f0f1a', p: 16, centerX: true })

new Label(screen, {
    text: 'Hello from CSUA!',
    fs: 28, color: '#fff', bold: true,
    mb: 12,
})

const btn = new Button(screen, {
    text: 'Tap me',
    bg: '#3b82f6', color: '#fff',
    radius: 12, p: 14, w: '80%',
    onClick: () => toast('Native Android toast!'),
})

let ticks = 0
const counter = new Label(screen, { text: 'Ticks: 0', color: '#94a3b8', fs: 16 })

loop(() => {
    ticks++
    counter.text = 'Ticks: ' + ticks
})
