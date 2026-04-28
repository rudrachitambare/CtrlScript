// ── Your app goes here ───────────────────────────────
// This is the ONLY file you write.
// No imports. Everything is global on both Android and browser.
// Run on Android:  csua run
// Run in browser:  <script src="csui.js"></script><script src="app.js"></script>

new Box({ col: true, bg: '#0f0f1a', p: 16, centerX: true })

new Label({ text: 'Hello from CtrlScript!', fs: 28, c: '#fff', bold: true, mb: 12 })

new Button({
    text: 'Tap me',
    bg: '#3b82f6', c: '#fff',
    br: 12, p: 14, w: '80%',
    onClick: () => dialog.alert('Works on Android and browser!'),
})

let ticks = 0
const counter = new Label({ text: 'Ticks: 0', c: '#94a3b8', fs: 16 })

loop(() => {
    ticks++
    counter.text = 'Ticks: ' + ticks
})
