// ──────────────────────────────────────────────────────
// CSUA — CtrlScript Android Runtime
// Same syntax as CSUI (web), runs on real Android Views
// via QuickJS + Java bridge (no WebView)
//
// Usage in app.js — no imports needed, everything is global:
//   new Box("app", { bg: "#fff" })
//   new Label("app", { text: "Hello" })
// ──────────────────────────────────────────────────────

'use strict';

// ── QuickJS global polyfills (needed by Matter.js + other browser libs) ──────
globalThis.window          = globalThis;
globalThis.performance     = globalThis.performance || { now: () => Date.now() };
globalThis.requestAnimationFrame = (fn) => { setTimeout(fn, 1000 / 60); };
globalThis.cancelAnimationFrame  = (id) => { clearTimeout(id); };

// ──────────────────────────────────────────────────────
// §1  BRIDGE
//     Injected by Bootstrap.java into the QuickJS context.
//     Four primitives — everything else is built on these.
//
//     bridge.createView(type)              → id (int)
//     bridge.setProp(id, key, value)
//     bridge.addChild(parentId, childId)
//     bridge.call(module, method, ...args) → result
// ──────────────────────────────────────────────────────

if (typeof bridge === 'undefined') {
    // Dev stub so csua.js can be syntax-checked in Node/browser
    globalThis.bridge = {
        createView:  type        => { console.warn('[csua] bridge not available'); return 0; },
        setProp:     (id, k, v)  => {},
        addChild:    (p, c)      => {},
        call:        (m, fn, ...a) => null,
    };
}


// ──────────────────────────────────────────────────────
// §2  UNIT HELPERS
//     Android uses dp (density-independent px) and sp (scalable px for text).
//     Users can pass numbers (treated as dp) or strings ('16dp', '14sp', '50%').
// ──────────────────────────────────────────────────────

function _dp(v)  { return typeof v === 'number' ? `${v}dp` : String(v); }
function _sp(v)  { return typeof v === 'number' ? `${v}sp` : String(v); }
function _col(v) { return typeof v === 'string' && !v.startsWith('#') ? v : v; }


// ──────────────────────────────────────────────────────
// §3  STYLE PROP MAP
//     Translates CSUI shorthand → bridge.setProp calls
// ──────────────────────────────────────────────────────

const _styleMap = {
    // layout
    w:          (v, id) => bridge.setProp(id, 'width',          _dp(v)),
    h:          (v, id) => bridge.setProp(id, 'height',         _dp(v)),
    p:          (v, id) => bridge.setProp(id, 'padding',        _dp(v)),
    pt:         (v, id) => bridge.setProp(id, 'paddingTop',     _dp(v)),
    pb:         (v, id) => bridge.setProp(id, 'paddingBottom',  _dp(v)),
    pl:         (v, id) => bridge.setProp(id, 'paddingLeft',    _dp(v)),
    pr:         (v, id) => bridge.setProp(id, 'paddingRight',   _dp(v)),
    m:          (v, id) => bridge.setProp(id, 'margin',         _dp(v)),
    mt:         (v, id) => bridge.setProp(id, 'marginTop',      _dp(v)),
    mb:         (v, id) => bridge.setProp(id, 'marginBottom',   _dp(v)),
    ml:         (v, id) => bridge.setProp(id, 'marginLeft',     _dp(v)),
    mr:         (v, id) => bridge.setProp(id, 'marginRight',    _dp(v)),
    gap:        (v, id) => bridge.setProp(id, 'gap',            _dp(v)),
    // direction
    row:        (v, id) => bridge.setProp(id, 'orientation',    'horizontal'),
    col:        (v, id) => bridge.setProp(id, 'orientation',    'vertical'),
    // alignment
    center:     (v, id) => bridge.setProp(id, 'gravity',        'center'),
    centerX:    (v, id) => bridge.setProp(id, 'gravity',        'center_horizontal'),
    centerY:    (v, id) => bridge.setProp(id, 'gravity',        'center_vertical'),
    // appearance
    bg:         (v, id) => bridge.setProp(id, 'backgroundColor', v),
    color:      (v, id) => bridge.setProp(id, 'textColor',       v),
    c:          (v, id) => bridge.setProp(id, 'textColor',       v),
    fs:         (v, id) => bridge.setProp(id, 'textSize',        _sp(v)),
    bold:       (v, id) => bridge.setProp(id, 'textStyle',       v ? 'bold' : 'normal'),
    italic:     (v, id) => bridge.setProp(id, 'textStyle',       'italic'),
    radius:     (v, id) => bridge.setProp(id, 'cornerRadius',    _dp(v)),
    br:         (v, id) => bridge.setProp(id, 'cornerRadius',    _dp(v)),
    opacity:    (v, id) => bridge.setProp(id, 'alpha',           String(v)),
    // elevation / shadow
    elevation:  (v, id) => bridge.setProp(id, 'elevation',       _dp(v)),
    shadow:     (v, id) => bridge.setProp(id, 'elevation',       _dp(v)),
    // scroll
    scroll:     (v, id) => bridge.setProp(id, 'scrollable',      v ? 'true' : 'false'),
    // visibility
    visible:    (v, id) => bridge.setProp(id, 'visibility',      v ? 'visible' : 'gone'),
    // text
    text:       (v, id) => bridge.setProp(id, 'text',            String(v)),
    hint:       (v, id) => bridge.setProp(id, 'hint',            String(v)),
    // image
    src:        (v, id) => bridge.setProp(id, 'src',             String(v)),
    scaleType:  (v, id) => bridge.setProp(id, 'scaleType',       String(v)),
    // input
    inputType:  (v, id) => bridge.setProp(id, 'inputType',       String(v)),
    multiline:  (v, id) => bridge.setProp(id, 'multiline',       v ? 'true' : 'false'),
    // click
    onClick:    (v, id) => bridge.setProp(id, 'onClick',         v),
    onLongClick:(v, id) => bridge.setProp(id, 'onLongClick',     v),
    onChange:   (v, id) => bridge.setProp(id, 'onChange',        v),
};


// ──────────────────────────────────────────────────────
// §4  BASE ELEMENT
// ──────────────────────────────────────────────────────

let _idCounter = 0;

class BaseElement {
    constructor(containerRef = null, viewType = 'LinearLayout') {
        this.id        = ++_idCounter;
        this._viewType = viewType;
        this._viewId   = bridge.createView(viewType);
        this._children = [];
        this._mountCallbacks   = [];
        this._destroyCallbacks = [];
        this._mounted  = false;
        this._containerRef = containerRef;
    }

    _attach() {
        if (this._containerRef instanceof BaseElement) {
            this._containerRef.addChild(this);
        } else if (typeof this._containerRef === 'string') {
            const parent = _namedContainers[this._containerRef];
            if (parent) parent.addChild(this);
            else console.error(`[csua] No container named "${this._containerRef}"`);
        } else if (this._containerRef === null && _rootContainer && _rootContainer !== this) {
            _rootContainer.addChild(this);
        }
        this._triggerMount();
    }

    addChild(child) {
        this._children.push(child);
        bridge.addChild(this._viewId, child._viewId);
        child._triggerMount();
        return this;
    }

    set props(obj) {
        for (const [k, v] of Object.entries(obj)) {
            if (_styleMap[k]) _styleMap[k](v, this._viewId);
            else bridge.setProp(this._viewId, k, v);
        }
    }

    _triggerMount() {
        if (this._mounted) return;
        this._mounted = true;
        if (this._mountCallbacks.length)
            Promise.resolve().then(() => this._mountCallbacks.forEach(fn => fn()));
    }

    onMount(fn)   { this._mountCallbacks.push(fn);   return this; }
    onDestroy(fn) { this._destroyCallbacks.push(fn); return this; }

    destroy() {
        this._destroyCallbacks.forEach(fn => fn());
        bridge.call('views', 'remove', this._viewId);
        return this;
    }

    // convenience setters
    set text(v)      { bridge.setProp(this._viewId, 'text',            String(v)); }
    get text()       { return bridge.call('views', 'getProp', this._viewId, 'text'); }
    set color(v)     { bridge.setProp(this._viewId, 'textColor',       v); }
    set bg(v)        { bridge.setProp(this._viewId, 'backgroundColor', v); }
    set visible(v)   { bridge.setProp(this._viewId, 'visibility',      v ? 'visible' : 'gone'); }
    set opacity(v)   { bridge.setProp(this._viewId, 'alpha',           String(v)); }
    set w(v)         { this._w = typeof v === 'number' ? v : parseFloat(v); bridge.setProp(this._viewId, 'width',  _dp(v)); }
    set h(v)         { this._h = typeof v === 'number' ? v : parseFloat(v); bridge.setProp(this._viewId, 'height', _dp(v)); }
    set x(v)         { this._x = typeof v === 'number' ? v : parseFloat(v); bridge.setProp(this._viewId, 'x', v); }
    set y(v)         { this._y = typeof v === 'number' ? v : parseFloat(v); bridge.setProp(this._viewId, 'y', v); }
    set fs(v)        { bridge.setProp(this._viewId, 'textSize',        _sp(v)); }
}


// ──────────────────────────────────────────────────────
// §5  CONTAINER REGISTRY
// ──────────────────────────────────────────────────────

let _rootContainer = null;
const _namedContainers = {};


// ──────────────────────────────────────────────────────
// §6  VIEWS
// ──────────────────────────────────────────────────────

class Box extends BaseElement {
    constructor(containerRef = null, props = {}) {
        if (containerRef !== null && typeof containerRef === 'object' && !Array.isArray(containerRef)) {
            props = containerRef; containerRef = null;
        }
        super(containerRef, 'LinearLayout');

        if (typeof containerRef === 'string' && !_namedContainers[containerRef]) {
            _namedContainers[containerRef] = this;
        }
        if (!_rootContainer) _rootContainer = this;

        if (Object.keys(props).length) this.props = props;
        this._attach();
    }
}

class ScrollBox extends BaseElement {
    constructor(containerRef = null, props = {}) {
        super(containerRef, 'ScrollView');
        if (Object.keys(props).length) this.props = props;
        this._attach();
    }
}

class Label extends BaseElement {
    constructor(containerRef = null, props = {}) {
        super(containerRef, 'TextView');
        if (typeof props === 'string') { bridge.setProp(this._viewId, 'text', props); }
        else if (Object.keys(props).length) this.props = props;
        this._attach();
    }
}
const Text = Label;

class Button extends BaseElement {
    constructor(containerRef = null, props = {}) {
        super(containerRef, 'Button');
        if (Object.keys(props).length) this.props = props;
        this._attach();
    }
}

class Input extends BaseElement {
    constructor(containerRef = null, props = {}) {
        super(containerRef, 'EditText');
        if (Object.keys(props).length) this.props = props;
        this._attach();
    }
    get value()  { return bridge.call('views', 'getProp', this._viewId, 'text'); }
    set value(v) { bridge.setProp(this._viewId, 'text', String(v)); }
    focus()      { bridge.call('views', 'focus', this._viewId); return this; }
    blur()       { bridge.call('views', 'blur',  this._viewId); return this; }
}
const TextArea = Input;

class Image extends BaseElement {
    constructor(containerRef = null, props = {}) {
        super(containerRef, 'ImageView');
        if (Object.keys(props).length) this.props = props;
        this._attach();
    }
    set src(v) { bridge.setProp(this._viewId, 'src', String(v)); }
}

class Canvas extends BaseElement {
    constructor(containerRef = null, props = {}) {
        super(containerRef, 'CanvasView');
        if (Object.keys(props).length) this.props = props;
        this._attach();
    }
    drawRect(x, y, w, h, color)   { bridge.call('canvas', 'drawRect',   this._viewId, x, y, w, h, color); }
    drawCircle(x, y, r, color)    { bridge.call('canvas', 'drawCircle', this._viewId, x, y, r, color); }
    drawLine(x1, y1, x2, y2, color, strokeWidth = 2) {
        bridge.call('canvas', 'drawLine', this._viewId, x1, y1, x2, y2, color, strokeWidth);
    }
    drawText(text, x, y, color, size = 16) {
        bridge.call('canvas', 'drawText', this._viewId, text, x, y, color, size);
    }
    clear()  { bridge.call('canvas', 'clear',    this._viewId); }
    redraw() { bridge.call('canvas', 'invalidate', this._viewId); }
}

class List extends BaseElement {
    constructor(containerRef = null, props = {}) {
        const { data = [], renderItem, keyExtractor, ...rest } = props;
        super(containerRef, 'RecyclerView');
        this._renderItem    = renderItem;
        this._keyExtractor  = keyExtractor;
        if (Object.keys(rest).length) this.props = rest;
        this._attach();
        if (data.length) this.setData(data);
    }
    setData(data) {
        bridge.call('list', 'setData', this._viewId, JSON.stringify(data));
        return this;
    }
    scrollTo(index) { bridge.call('list', 'scrollTo', this._viewId, index); return this; }
}

// Shape aliases backed by CanvasView
class Rectangle extends BaseElement {
    constructor(containerRef = null, props = {}) {
        const { color = '#fff', radius = 0, ...rest } = props;
        super(containerRef, 'ShapeView');
        bridge.setProp(this._viewId, 'shapeType',  'rect');
        bridge.setProp(this._viewId, 'shapeColor', color);
        bridge.setProp(this._viewId, 'cornerRadius', _dp(radius));
        if (Object.keys(rest).length) this.props = rest;
        this._attach();
    }
}
const Square = Rectangle;

class Circle extends BaseElement {
    constructor(containerRef = null, props = {}) {
        const { color = '#fff', ...rest } = props;
        super(containerRef, 'ShapeView');
        bridge.setProp(this._viewId, 'shapeType',  'oval');
        bridge.setProp(this._viewId, 'shapeColor', color);
        if (Object.keys(rest).length) this.props = rest;
        this._attach();
    }
}

class SafeArea extends BaseElement {
    constructor(containerRef = null, props = {}) {
        super(containerRef, 'SafeAreaView');
        if (Object.keys(props).length) this.props = props;
        this._attach();
    }
}


// ──────────────────────────────────────────────────────
// §7  LAYOUT HELPER
// ──────────────────────────────────────────────────────

function group(containerRef, props = {}, ...children) {
    const box = new Box(containerRef, props);
    children.forEach(c => { if (c instanceof BaseElement) box.addChild(c); });
    return box;
}


// ──────────────────────────────────────────────────────
// §8  LOOP  (requestAnimationFrame equivalent)
// ──────────────────────────────────────────────────────

const _loops    = new Set();
let   _loopRunning = false;

function _tick() {
    if (!_loops.size) { _loopRunning = false; return; }
    _loops.forEach(fn => fn());
    bridge.call('app', 'requestFrame', '_csuaTick');
}
globalThis._csuaTick = _tick;

function loop(fn) {
    _loops.add(fn);
    if (!_loopRunning) {
        _loopRunning = true;
        bridge.call('app', 'requestFrame', '_csuaTick');
    }
    return () => _loops.delete(fn);
}

function stopLoop(fn) { _loops.delete(fn); }


// ──────────────────────────────────────────────────────
// §9  TIMERS
// ──────────────────────────────────────────────────────

function after(ms, fn)  { return setTimeout(fn, ms); }
function every(ms, fn)  { return setInterval(fn, ms); }
function cancel(id)     { clearTimeout(id); clearInterval(id); }


// ──────────────────────────────────────────────────────
// §10  STORAGE
//      save / load  →  SharedPreferences (simple key/value)
//      db           →  SQLite
//      files        →  File system
// ──────────────────────────────────────────────────────

function save(key, value) {
    bridge.call('storage', 'set', key, JSON.stringify(value));
}

function load(key, fallback = null) {
    const raw = bridge.call('storage', 'get', key);
    if (raw === null || raw === undefined) return fallback;
    try { return JSON.parse(raw); } catch { return raw; }
}

function remove(key) { bridge.call('storage', 'remove', key); }
function clearAll()  { bridge.call('storage', 'clear'); }

const db = {
    run(sql, ...params) {
        return bridge.call('db', 'run', sql, JSON.stringify(params));
    },
    query(sql, ...params) {
        const raw = bridge.call('db', 'query', sql, JSON.stringify(params));
        return raw ? JSON.parse(raw) : [];
    },
    transaction(fn) {
        bridge.call('db', 'beginTransaction');
        try { fn(); bridge.call('db', 'commit'); }
        catch (e) { bridge.call('db', 'rollback'); throw e; }
    },
};

const files = {
    read(path)         { return bridge.call('files', 'read',   path); },
    write(path, data)  { bridge.call('files', 'write',  path, data); },
    append(path, data) { bridge.call('files', 'append', path, data); },
    delete(path)       { bridge.call('files', 'delete', path); },
    exists(path)       { return bridge.call('files', 'exists', path) === 'true'; },
    list(dir)          {
        const raw = bridge.call('files', 'list', dir);
        return raw ? JSON.parse(raw) : [];
    },
    mkdir(path)        { bridge.call('files', 'mkdir', path); },
};


// ──────────────────────────────────────────────────────
// §11  NETWORK
//      fetch() and WebSocket — bridged through Java OkHttp
// ──────────────────────────────────────────────────────

function fetch(url, opts = {}) {
    return new Promise((resolve, reject) => {
        const cbId = `_csuaFetch_${++_idCounter}`;
        globalThis[cbId] = (err, status, headers, body) => {
            delete globalThis[cbId];
            if (err) { reject(new Error(err)); return; }
            resolve({
                status,
                ok: status >= 200 && status < 300,
                headers: headers ? JSON.parse(headers) : {},
                text:   () => Promise.resolve(body),
                json:   () => Promise.resolve(JSON.parse(body)),
            });
        };
        bridge.call('network', 'fetch', cbId, url, JSON.stringify(opts));
    });
}

class WebSocket {
    constructor(url) {
        this._id      = ++_idCounter;
        this._onMsg   = null;
        this._onOpen  = null;
        this._onClose = null;
        this._onError = null;
        globalThis[`_csuaWS_${this._id}`] = (event, data) => {
            if (event === 'open'    && this._onOpen)  this._onOpen();
            if (event === 'message' && this._onMsg)   this._onMsg({ data });
            if (event === 'close'   && this._onClose) this._onClose();
            if (event === 'error'   && this._onError) this._onError(new Error(data));
        };
        bridge.call('network', 'wsConnect', this._id, url);
    }
    onMessage(fn) { this._onMsg   = fn; return this; }
    onOpen(fn)    { this._onOpen  = fn; return this; }
    onClose(fn)   { this._onClose = fn; return this; }
    onError(fn)   { this._onError = fn; return this; }
    send(data)    { bridge.call('network', 'wsSend',  this._id, data); }
    close()       { bridge.call('network', 'wsClose', this._id); }
}


// ──────────────────────────────────────────────────────
// §12  PERMISSIONS
//      perm / permission / permissions  (all aliases)
// ──────────────────────────────────────────────────────

const perm = {
    request(perms) {
        const list = Array.isArray(perms) ? perms : [perms];
        return new Promise((resolve, reject) => {
            const cbId = `_csuaPerm_${++_idCounter}`;
            globalThis[cbId] = (err, results) => {
                delete globalThis[cbId];
                if (err) reject(new Error(err));
                else resolve(JSON.parse(results));
            };
            bridge.call('permissions', 'request', cbId, JSON.stringify(list));
        });
    },
    check(p) {
        return bridge.call('permissions', 'check', p);  // 'granted'|'denied'|'never_asked'
    },
    openSettings() { bridge.call('permissions', 'openSettings'); },
};
const permission  = perm;
const permissions = perm;


// ──────────────────────────────────────────────────────
// §13  DEVICE APIS
//      Lazy-loaded — module only initializes when first used
// ──────────────────────────────────────────────────────

function _asyncBridge(module, method, ...args) {
    return new Promise((resolve, reject) => {
        const cbId = `_csuaCb_${++_idCounter}`;
        globalThis[cbId] = (err, result) => {
            delete globalThis[cbId];
            if (err) reject(new Error(err));
            else resolve(result !== undefined ? JSON.parse(result) : undefined);
        };
        bridge.call(module, method, cbId, ...args);
    });
}

function _watchBridge(module, method, fn, ...args) {
    const cbId = `_csuaWatch_${++_idCounter}`;
    globalThis[cbId] = data => fn(JSON.parse(data));
    bridge.call(module, method, cbId, ...args);
    return () => {
        delete globalThis[cbId];
        bridge.call(module, method + 'Stop', cbId);
    };
}

const device = {
    // ── Info ──
    get info() {
        const raw = bridge.call('device', 'info');
        return raw ? JSON.parse(raw) : {};
    },

    // ── Location / GPS ──
    location()               { return _asyncBridge('sensors', 'getLocation'); },
    watchLocation(fn)        { return _watchBridge('sensors', 'watchLocation', fn); },

    // ── Motion sensors ──
    tilt(fn)                 { return _watchBridge('sensors', 'tilt',         fn); },
    accelerometer(fn)        { return _watchBridge('sensors', 'accelerometer',fn); },
    gyroscope(fn)            { return _watchBridge('sensors', 'gyroscope',    fn); },
    compass(fn)              { return _watchBridge('sensors', 'compass',      fn); },
    proximity(fn)            { return _watchBridge('sensors', 'proximity',    fn); },
    light(fn)                { return _watchBridge('sensors', 'light',        fn); },
    pressure(fn)             { return _watchBridge('sensors', 'pressure',     fn); },
    pedometer(fn)            { return _watchBridge('sensors', 'pedometer',    fn); },

    // ── Camera ──
    camera: {
        snap(opts = {})      { return _asyncBridge('camera', 'snap',   JSON.stringify(opts)); },
        record(ms = 10000)   { return _asyncBridge('camera', 'record', ms); },
        stream(viewId)       { bridge.call('camera', 'stream', viewId); },
    },

    // ── Gallery ──
    gallery: {
        pick()               { return _asyncBridge('gallery', 'pick'); },
        pickMultiple()       { return _asyncBridge('gallery', 'pickMultiple'); },
    },

    // ── Microphone ──
    microphone: {
        record(ms = 5000)    { return _asyncBridge('audio', 'record', ms); },
        stop()               { bridge.call('audio', 'stopRecord'); },
    },

    // ── Screen ──
    screen: {
        capture()            { return _asyncBridge('device', 'screenshot'); },
        get brightness()     { return parseFloat(bridge.call('device', 'getBrightness')); },
        set brightness(v)    { bridge.call('device', 'setBrightness', String(v)); },
        keepOn(v = true)     { bridge.call('device', 'wakeLock', v ? 'true' : 'false'); },
        orientation(v)       { bridge.call('device', 'orientation', v); }, // 'portrait'|'landscape'|'auto'
    },

    // ── Haptics ──
    vibrate(pattern = 200)   {
        const p = Array.isArray(pattern) ? pattern.join(',') : String(pattern);
        bridge.call('device', 'vibrate', p);
    },

    // ── Flashlight ──
    flashlight: {
        on()                 { bridge.call('device', 'flashlight', 'on'); },
        off()                { bridge.call('device', 'flashlight', 'off'); },
        toggle()             { bridge.call('device', 'flashlight', 'toggle'); },
    },

    // ── Notifications ──
    notify(title, body, opts = {}) {
        return _asyncBridge('notifications', 'send', JSON.stringify({ title, body, ...opts }));
    },

    // ── Clipboard ──
    clipboard: {
        copy(text)           { bridge.call('clipboard', 'copy', text); },
        paste()              { return bridge.call('clipboard', 'paste'); },
    },

    // ── Bluetooth ──
    bluetooth: {
        scan(timeout = 5000) { return _asyncBridge('bluetooth', 'scan', timeout); },
        connect(id)          { return _asyncBridge('bluetooth', 'connect', id); },
        disconnect(id)       { bridge.call('bluetooth', 'disconnect', id); },
        send(id, data)       { bridge.call('bluetooth', 'send', id, data); },
        onData(id, fn)       { return _watchBridge('bluetooth', 'onData', fn, id); },
    },

    // ── NFC ──
    nfc: {
        read()               { return _asyncBridge('nfc', 'read'); },
        write(tagId, data)   { return _asyncBridge('nfc', 'write', tagId, JSON.stringify(data)); },
    },

    // ── Biometrics ──
    biometric: {
        available()          { return bridge.call('biometric', 'available'); },
        verify(reason = '')  { return _asyncBridge('biometric', 'verify', reason); },
    },

    // ── Secure storage ──
    keystore: {
        save(key, secret)    { bridge.call('keystore', 'save', key, secret); },
        get(key)             { return bridge.call('keystore', 'get', key); },
        delete(key)          { bridge.call('keystore', 'delete', key); },
    },

    // ── SMS / Call ──
    sms: {
        send(number, message){ return _asyncBridge('intents', 'sendSms', number, message); },
    },
    call(number)             { bridge.call('intents', 'dial', number); },

    // ── TTS ──
    tts: {
        speak(text, opts={}) { bridge.call('tts', 'speak', text, JSON.stringify(opts)); },
        stop()               { bridge.call('tts', 'stop'); },
    },
};


// ──────────────────────────────────────────────────────
// §14  AUDIO
// ──────────────────────────────────────────────────────

class Sound {
    constructor(src) {
        this._id = ++_idCounter;
        bridge.call('audio', 'load', this._id, src);
    }
    play()            { bridge.call('audio', 'play',   this._id); return this; }
    pause()           { bridge.call('audio', 'pause',  this._id); return this; }
    stop()            { bridge.call('audio', 'stop',   this._id); return this; }
    set volume(v)     { bridge.call('audio', 'volume', this._id, String(v)); }
    set loop(v)       { bridge.call('audio', 'loop',   this._id, v ? 'true' : 'false'); }
    onEnd(fn)         {
        const cbId = `_csuaAudio_${this._id}`;
        globalThis[cbId] = fn;
        bridge.call('audio', 'onEnd', this._id, cbId);
        return this;
    }
    destroy()         { bridge.call('audio', 'destroy', this._id); }
}


// ──────────────────────────────────────────────────────
// §15  APP LIFECYCLE
// ──────────────────────────────────────────────────────

const app = {
    onPause(fn)      { bridge.call('app', 'on', 'pause',      '_csuaAppPause');    globalThis._csuaAppPause    = fn; },
    onResume(fn)     { bridge.call('app', 'on', 'resume',     '_csuaAppResume');   globalThis._csuaAppResume   = fn; },
    onDestroy(fn)    { bridge.call('app', 'on', 'destroy',    '_csuaAppDestroy');  globalThis._csuaAppDestroy  = fn; },
    onBack(fn)       { bridge.call('app', 'on', 'back',       '_csuaAppBack');     globalThis._csuaAppBack     = fn; },
    onDeepLink(fn)   { bridge.call('app', 'on', 'deeplink',   '_csuaAppDeepLink'); globalThis._csuaAppDeepLink = fn; },
    onMemoryWarning(fn) { bridge.call('app', 'on', 'memory',  '_csuaAppMemory');   globalThis._csuaAppMemory   = fn; },
    exit()           { bridge.call('app', 'exit'); },
    get version()    { return bridge.call('app', 'version'); },
    get package()    { return bridge.call('app', 'package'); },
    devtools()       { bridge.call('app', 'devtools'); },
};


// ──────────────────────────────────────────────────────
// §16  ROUTER  (screen navigation — not URL)
// ──────────────────────────────────────────────────────

const _screens  = {};
let   _current  = null;
let   _history  = [];

const router = {
    define(name, fn)       { _screens[name] = fn; },
    go(name, data = null)  {
        if (!_screens[name]) { console.error(`[csua] Screen "${name}" not defined`); return; }
        _history.push(name);
        _current = name;
        bridge.call('app', 'clearRoot');
        _rootContainer = null;
        _screens[name](data);
    },
    back() {
        if (_history.length < 2) return;
        _history.pop();
        const prev = _history[_history.length - 1];
        this.go(prev);
    },
    get current() { return _current; },
};
const scene = router.define.bind(router);
const go    = router.go.bind(router);


// ──────────────────────────────────────────────────────
// §17  KEYBOARD
// ──────────────────────────────────────────────────────

const keyboard = {
    hide()      { bridge.call('keyboard', 'hide'); },
    show(id)    { bridge.call('keyboard', 'show', id); },
    onShow(fn)  { globalThis._csuaKbShow = fn; bridge.call('keyboard', 'onShow', '_csuaKbShow'); },
    onHide(fn)  { globalThis._csuaKbHide = fn; bridge.call('keyboard', 'onHide', '_csuaKbHide'); },
};


// ──────────────────────────────────────────────────────
// §18  SYSTEM UI  (status bar, nav bar)
// ──────────────────────────────────────────────────────

const statusBar = {
    color(v)        { bridge.call('systemui', 'statusBarColor', v); },
    style(v)        { bridge.call('systemui', 'statusBarStyle', v); }, // 'light'|'dark'
    hide()          { bridge.call('systemui', 'statusBarHide', 'true'); },
    show()          { bridge.call('systemui', 'statusBarHide', 'false'); },
};

const navigationBar = {
    color(v)        { bridge.call('systemui', 'navBarColor', v); },
    hide()          { bridge.call('systemui', 'navBarHide', 'true'); },
    show()          { bridge.call('systemui', 'navBarHide', 'false'); },
};


// ──────────────────────────────────────────────────────
// §19  INTENTS  (open other apps / share)
// ──────────────────────────────────────────────────────

function share(opts = {})     { bridge.call('intents', 'share',   JSON.stringify(opts)); }
function openUrl(url)         { bridge.call('intents', 'openUrl', url); }
function openApp(packageName) { bridge.call('intents', 'openApp', packageName); }
function openSettings()       { bridge.call('intents', 'openSettings'); }
function openMaps(opts = {})  { bridge.call('intents', 'openMaps', JSON.stringify(opts)); }


// ──────────────────────────────────────────────────────
// §20  NATIVE DIALOGS
// ──────────────────────────────────────────────────────

const dialog = {
    alert(title, message)      { return _asyncBridge('dialog', 'alert',      title, message); },
    confirm(title, message)    { return _asyncBridge('dialog', 'confirm',    title, message); },
    prompt(title, hint = '')   { return _asyncBridge('dialog', 'prompt',     title, hint); },
    datePicker(opts = {})      { return _asyncBridge('dialog', 'datePicker', JSON.stringify(opts)); },
    timePicker(opts = {})      { return _asyncBridge('dialog', 'timePicker', JSON.stringify(opts)); },
    colorPicker(initial = '')  { return _asyncBridge('dialog', 'colorPicker',initial); },
    filePicker(opts = {})      { return _asyncBridge('dialog', 'filePicker', JSON.stringify(opts)); },
    actionSheet(title, items)  { return _asyncBridge('dialog', 'actionSheet',title, JSON.stringify(items)); },
};


// ──────────────────────────────────────────────────────
// §21  TOAST  (native Android toast — not a custom div)
// ──────────────────────────────────────────────────────

function toast(message, { duration = 'short', gravity = 'bottom' } = {}) {
    bridge.call('dialog', 'toast', message, duration, gravity);
}


// ──────────────────────────────────────────────────────
// §22  BACKGROUND TASKS
// ──────────────────────────────────────────────────────

const background = {
    task(fn) {
        const cbId = `_csuaBg_${++_idCounter}`;
        globalThis[cbId] = fn;
        return bridge.call('background', 'oneShot', cbId);
    },
    every(interval, fn) {
        const cbId = `_csuaBgEvery_${++_idCounter}`;
        globalThis[cbId] = fn;
        return bridge.call('background', 'periodic', cbId, interval);
    },
    cancel(id) { bridge.call('background', 'cancel', id); },
};


// ──────────────────────────────────────────────────────
// §23  2D GAME ENGINE  (Matter.js, same as csui web)
// ──────────────────────────────────────────────────────

const _MATTER_CDN = 'https://cdnjs.cloudflare.com/ajax/libs/matter-js/0.19.0/matter.min.js';
let _matterPromise = null;

function _loadMatter() {
    if (_matterPromise) return _matterPromise;
    if (globalThis.Matter) return (_matterPromise = Promise.resolve(globalThis.Matter));
    _matterPromise = fetch(_MATTER_CDN)
        .then(r => r.text())
        .then(src => {
            // eval Matter.js into the QuickJS global scope
            (new Function(src))();
            if (!globalThis.Matter) throw new Error('[csua] Matter.js did not expose global');
            return globalThis.Matter;
        });
    return _matterPromise;
}

const engine2d = (() => {
    let _engine  = null;
    const _bodyMap = new Map(); // BaseElement -> Matter body

    return {
        init({ gravity = { x: 0, y: 1 } } = {}) {
            return _loadMatter().then(M => {
                _engine = M.Engine.create();
                _engine.gravity.x = gravity.x;
                _engine.gravity.y = gravity.y;
                M.Runner.run(M.Runner.create(), _engine);
                // sync Matter body positions → Android View positions each frame
                loop(() => {
                    _bodyMap.forEach((body, el) => {
                        bridge.setProp(el._viewId, 'x', Math.round(body.position.x - el._w / 2));
                        bridge.setProp(el._viewId, 'y', Math.round(body.position.y - el._h / 2));
                    });
                });
                return _engine;
            });
        },

        attach(element, { mass = 1, bounce = 0.5, friction = 0.1, isStatic = false } = {}) {
            if (!_engine) { console.warn('[csua] Call engine2d.init() first'); return Promise.resolve(); }
            return _loadMatter().then(M => {
                const w = element._w || 100, h = element._h || 100;
                const x = (element._x || 0) + w / 2;
                const y = (element._y || 0) + h / 2;
                const body = M.Bodies.rectangle(x, y, w, h,
                    { mass, restitution: bounce, friction, isStatic });
                M.World.add(_engine.world, body);
                _bodyMap.set(element, body);
                element._physicsBody = body;
                return body;
            });
        },

        addGround(y, width = 1080, height = 20) {
            return _loadMatter().then(M => {
                const g = M.Bodies.rectangle(width / 2, y, width, height, { isStatic: true });
                M.World.add(_engine.world, g);
                return g;
            });
        },
    };
})();

// element.physics(opts) — convenience method matching csui.js
BaseElement.prototype.physics = function(opts = {}) {
    engine2d.attach(this, opts);
    return this;
};

// ──────────────────────────────────────────────────────
// §24  ANIMATIONS  (native Android property animations)
// ──────────────────────────────────────────────────────

function animate(element, props = {}, { duration = 300, easing = 'ease', delay = 0 } = {}) {
    return new Promise(resolve => {
        const cbId = `_csuaAnim_${++_idCounter}`;
        globalThis[cbId] = () => { delete globalThis[cbId]; resolve(); };
        bridge.call('anim', 'animate', element._viewId, JSON.stringify(props), duration, easing, delay, cbId);
    });
}


// ──────────────────────────────────────────────────────
// §24  INPUT EVENTS  (touch, gesture)
// ──────────────────────────────────────────────────────

function onTouch(element, fn) {
    const cbId = `_csuaTouch_${element._viewId}`;
    globalThis[cbId] = data => fn(JSON.parse(data));
    bridge.call('events', 'onTouch', element._viewId, cbId);
    return () => { delete globalThis[cbId]; bridge.call('events', 'removeTouch', element._viewId, cbId); };
}

function onSwipe(element, fn) {
    const cbId = `_csuaSwipe_${element._viewId}`;
    globalThis[cbId] = data => fn(JSON.parse(data));
    bridge.call('events', 'onSwipe', element._viewId, cbId);
    return () => { delete globalThis[cbId]; bridge.call('events', 'removeSwipe', element._viewId, cbId); };
}

function onPinch(element, fn) {
    const cbId = `_csuaPinch_${element._viewId}`;
    globalThis[cbId] = data => fn(JSON.parse(data));
    bridge.call('events', 'onPinch', element._viewId, cbId);
    return () => { delete globalThis[cbId]; bridge.call('events', 'removePinch', element._viewId, cbId); };
}


// ──────────────────────────────────────────────────────
// §25  PLUGIN SYSTEM  (same as CSUI web)
//      csua.use({ name, install({ BaseElement, Box, register, addProto }) })
// ──────────────────────────────────────────────────────

const _plugins = new Map();

function use(plugin) {
    if (!plugin || typeof plugin.name !== 'string' || typeof plugin.install !== 'function') {
        console.error('[csua] use() expects { name: string, install(api) {} }');
        return;
    }
    if (_plugins.has(plugin.name)) {
        console.warn(`[csua] Plugin "${plugin.name}" already registered — skipping`);
        return;
    }
    plugin.install({
        BaseElement, Box,
        register(name, Cls)       { ctrlscript[name] = Cls; },
        addProto(Target, name, fn){ Target.prototype[name] = fn; },
    });
    _plugins.set(plugin.name, plugin);
}


// ──────────────────────────────────────────────────────
// §26  NAMESPACE EXPORT  (ctrlscript / CS)
// ──────────────────────────────────────────────────────

const ctrlscript = {
    // Views
    Box, ScrollBox, Label, Text, Button, Input, TextArea,
    Image, Canvas, List, Rectangle, Square, Circle, SafeArea,
    // Layout
    group,
    // Loop & timers
    loop, stopLoop, after, every, cancel,
    // Storage
    save, load, remove, clearAll, db, files,
    // Network
    fetch, WebSocket,
    // Permissions
    perm, permission, permissions,
    // Device
    device,
    // Audio
    Sound,
    // App
    app, router, scene, go,
    // UI
    keyboard, statusBar, navigationBar,
    dialog, toast,
    // System
    share, openUrl, openApp, openSettings, openMaps,
    background,
    animate,
    onTouch, onSwipe, onPinch,
    // Game
    engine2d,
    // Plugin
    use,
};

const CS = ctrlscript;

// ── §27 — Browser Compatibility Layer ────────────────────────────────────────
// Stubs so the same app.js runs in both csua (Android) and csui (browser).
// Browser-only APIs are mapped to Android equivalents or logged as no-ops.

// onKey — no physical keyboard on Android usually; stub gracefully
function onKey(key, fn, { up = false } = {}) {
    console.warn(`[csua] onKey('${key}') — no physical keyboard on Android.`);
}

// collides / checkOverlap — pure math, works on both
function collides(a, b) {
    const ar = a.el ? a.el.getBoundingClientRect?.() : a;
    const br = b.el ? b.el.getBoundingClientRect?.() : b;
    if (!ar || !br) return false;
    return !(ar.right < br.left || ar.left > br.right ||
             ar.bottom < br.top || ar.top > br.bottom);
}
const checkOverlap = collides;

// ask — maps to dialog.prompt
function ask(message, defaultVal = '') {
    return dialog.prompt(message, defaultVal);
}

// camera (webcam alias) — maps to Android camera
const camera = {
    snap(opts = {})   { return device.camera.snap(opts); },
    record(opts = {}) { return device.camera.record(opts); },
    pick(opts = {})   { return device.camera.pick(opts); },
};

// sound (lowercase object) — maps to Sound class
const sound = {
    play(src, opts = {}) {
        const s = new Sound(src);
        if (opts.volume !== undefined) s.volume = opts.volume;
        if (opts.loop)                 s.loop   = true;
        s.play();
        return s;
    },
    stop(s) { if (s && s.stop) s.stop(); },
};

// palette — same pure-math function as csui.js
function palette(baseColor, { name = 'color', shades = [50,100,200,300,400,500,600,700,800,900,950] } = {}) {
    function hexToRgb(h) {
        h = h.replace('#','');
        if (h.length === 3) h = h.split('').map(c=>c+c).join('');
        return [parseInt(h.slice(0,2),16), parseInt(h.slice(2,4),16), parseInt(h.slice(4,6),16)];
    }
    function rgbToHsl(r,g,b) {
        r/=255; g/=255; b/=255;
        const max=Math.max(r,g,b), min=Math.min(r,g,b), l=(max+min)/2;
        if (max===min) return [0,0,l];
        const d=max-min, s=l>0.5?d/(2-max-min):d/(max+min);
        const h=max===r?((g-b)/d+(g<b?6:0))/6:max===g?((b-r)/d+2)/6:((r-g)/d+4)/6;
        return [h*360,s,l];
    }
    function hslToHex(h,s,l) {
        h/=360;
        const q=l<0.5?l*(1+s):l+s-l*s, p=2*l-q;
        const hue2rgb=(p,q,t)=>{if(t<0)t+=1;if(t>1)t-=1;return t<1/6?p+(q-p)*6*t:t<1/2?q:t<2/3?p+(q-p)*(2/3-t)*6:p};
        return '#'+[h+1/3,h,h-1/3].map(t=>Math.round(hue2rgb(p,q,t)*255).toString(16).padStart(2,'0')).join('');
    }
    const [r,g,b] = hexToRgb(baseColor);
    const [h,s,baseL] = rgbToHsl(r,g,b);
    const result = {};
    shades.forEach(shade => {
        const t = shade / 1000;
        const l = shade <= 500 ? baseL + (1 - baseL) * (1 - t * 2) : baseL - baseL * (t * 2 - 1);
        result[shade] = hslToHex(h, s, Math.max(0, Math.min(1, l)));
    });
    console.log(`[csua] palette '${name}':`, JSON.stringify(result));
    return result;
}

// UI components — native Android equivalents via dialog/views
class DropdownMenu extends BaseElement {
    constructor(ref, props = {}) {
        super(ref, 'Spinner');
        const { items = [], onSelect } = props;
        bridge.setProp(this._viewId, 'entries', JSON.stringify(items.map(i => i.label || i)));
        if (onSelect) bridge.call('events', 'onItemSelected', String(this._viewId), 'select');
        this._onSelect = onSelect;
        this._attach();
    }
}
class Modal extends BaseElement {
    constructor(ref, props = {}) {
        super(ref, 'LinearLayout');
        const { title = '', content = '', onClose } = props;
        dialog.alert(`${title}\n\n${content}`).then(() => onClose?.());
    }
    open()  {}
    close() {}
}
class Tabs extends BaseElement {
    constructor(ref, props = {}) {
        super(ref, 'TabLayout');
        (props.tabs || []).forEach(t => bridge.call('views', 'addTab', String(this._viewId), t.label || t));
        this._attach();
        if (Object.keys(props).length) this.props = props;
    }
}
class Slider extends BaseElement {
    constructor(ref, props = {}) {
        super(ref, 'SeekBar');
        if (props.min !== undefined)   bridge.setProp(this._viewId, 'min',      props.min);
        if (props.max !== undefined)   bridge.setProp(this._viewId, 'max',      props.max);
        if (props.value !== undefined) bridge.setProp(this._viewId, 'progress', props.value);
        if (props.onChange) bridge.call('events', 'onProgressChanged', String(this._viewId), 'change');
        this._attach();
    }
    get value()  { return bridge.call('views', 'getProp', String(this._viewId), 'progress'); }
    set value(v) { bridge.setProp(this._viewId, 'progress', v); }
}
class ProgressBar extends BaseElement {
    constructor(ref, props = {}) {
        super(ref, 'ProgressBar');
        if (props.value !== undefined) bridge.setProp(this._viewId, 'progress', props.value);
        if (props.max !== undefined)   bridge.setProp(this._viewId, 'max',      props.max);
        this._attach();
    }
    get value()  { return bridge.call('views', 'getProp', String(this._viewId), 'progress'); }
    set value(v) { bridge.setProp(this._viewId, 'progress', v); }
}
class Toggle extends BaseElement {
    constructor(ref, props = {}) {
        super(ref, 'Switch');
        if (props.checked !== undefined) bridge.setProp(this._viewId, 'checked', props.checked);
        if (props.onChange) bridge.call('events', 'onCheckedChanged', String(this._viewId), 'change');
        this._attach();
    }
    get checked()  { return bridge.call('views', 'getProp', String(this._viewId), 'checked'); }
    set checked(v) { bridge.setProp(this._viewId, 'checked', v); }
}
class Accordion extends BaseElement {
    constructor(ref, props = {}) {
        super(ref, 'LinearLayout');
        (props.items || []).forEach(item => {
            const row  = new Button(null, { text: item.header || item.title || '' });
            const body = new Label(null,  { text: item.content || item.body || '' });
            bridge.addChild(this._viewId, row._viewId);
            bridge.addChild(this._viewId, body._viewId);
        });
        this._attach();
    }
}
class Drawer extends BaseElement {
    constructor(ref, props = {}) {
        super(ref, 'DrawerLayout');
        this._attach();
        if (Object.keys(props).length) this.props = props;
    }
    open()  { bridge.call('views', 'openDrawer',  String(this._viewId)); }
    close() { bridge.call('views', 'closeDrawer', String(this._viewId)); }
}
class Chip extends BaseElement {
    constructor(ref, props = {}) {
        super(ref, 'Chip');
        if (props.text) bridge.setProp(this._viewId, 'text', props.text);
        this._attach();
    }
}
class Card extends BaseElement {
    constructor(ref, props = {}) {
        super(ref, 'CardView');
        if (props.title)   bridge.setProp(this._viewId, 'title',   props.title);
        if (props.content) bridge.setProp(this._viewId, 'content', props.content);
        this._attach();
    }
}

// ──────────────────────────────────────────────────────
// §SVG — Vector Graphics (Android)
//   Svg is a BaseElement backed by a native SvgCanvasView (Canvas, no WebView).
//   Shapes are plain JS objects; render() sends JSON to Java for Canvas drawing.
//   Auto-scheduled via microtask — batches rapid .set() calls into one render.
//   API is identical to csui.js (browser).
// ──────────────────────────────────────────────────────

class _SvgShape {
    constructor(parent, type, attrs) {
        this._type  = type;
        this._attrs = Object.assign({}, attrs);
        this._svg   = parent instanceof Svg ? parent : parent._svg;
        parent._addShape(this);
    }
    set(attrs) { Object.assign(this._attrs, attrs); this._svg._schedule(); return this; }
    remove()   { this._svg._shapes = this._svg._shapes.filter(s => s !== this); this._svg._schedule(); }
    _toJson()  { return Object.assign({ type: this._type }, this._attrs); }
}

class SvgGroup {
    constructor(parent, attrs = {}) {
        this._attrs  = Object.assign({}, attrs);
        this._shapes = [];
        this._svg    = parent instanceof Svg ? parent : parent._svg;
        parent._addShape(this);
    }
    _addShape(s) { this._shapes.push(s); this._svg._schedule(); }
    set(attrs)   { Object.assign(this._attrs, attrs); this._svg._schedule(); return this; }
    _toJson() {
        return Object.assign({ type: 'g', children: this._shapes.map(s => s._toJson()) }, this._attrs);
    }
}

class Svg extends BaseElement {
    constructor(containerRef = null, props = {}) {
        if (containerRef !== null && typeof containerRef === 'object' && !Array.isArray(containerRef)) {
            props = containerRef; containerRef = null;
        }
        const { w = 300, h = 200, bg = 'transparent', ...rest } = props;
        super(containerRef, 'SvgView');
        this._shapes  = [];
        this._svgW    = w;
        this._svgH    = h;
        this._svgBg   = bg;
        this._pending = false;
        bridge.setProp(this._viewId, 'width',  _dp(w));
        bridge.setProp(this._viewId, 'height', _dp(h));
        if (Object.keys(rest).length) this.props = rest;
        this._attach();
        this.render();
    }
    _addShape(s) { this._shapes.push(s); this._schedule(); }
    _schedule() {
        if (this._pending) return;
        this._pending = true;
        Promise.resolve().then(() => { this._pending = false; this.render(); });
    }
    render() {
        const json = JSON.stringify({
            shapes: this._shapes.map(s => s._toJson()),
            bg: this._svgBg,
            w:  this._svgW,
            h:  this._svgH,
        });
        bridge.call('views', 'render', this._viewId, json);
    }
    clear() { this._shapes = []; this.render(); }
}

class SvgRect     extends _SvgShape { constructor(p, a = {}) { super(p, 'rect',     a); } }
class SvgCircle   extends _SvgShape { constructor(p, a = {}) { super(p, 'circle',   a); } }
class SvgEllipse  extends _SvgShape { constructor(p, a = {}) { super(p, 'ellipse',  a); } }
class SvgLine     extends _SvgShape { constructor(p, a = {}) { super(p, 'line',     a); } }
class SvgPath     extends _SvgShape { constructor(p, a = {}) { super(p, 'path',     a); } }
class SvgPolygon  extends _SvgShape { constructor(p, a = {}) { super(p, 'polygon',  a); } }
class SvgPolyline extends _SvgShape { constructor(p, a = {}) { super(p, 'polyline', a); } }
class SvgText     extends _SvgShape { constructor(p, a = {}) { super(p, 'text',     a); } }


// Update ctrlscript namespace
Object.assign(ctrlscript, {
    onKey, collides, checkOverlap, ask, camera, sound, palette,
    DropdownMenu, Modal, Tabs, Slider, ProgressBar, Toggle,
    Accordion, Drawer, Chip, Card,
    engine2d,
    Svg, SvgGroup,
    SvgRect, SvgCircle, SvgEllipse, SvgLine,
    SvgPath, SvgPolygon, SvgPolyline, SvgText,
});
use.bind && (ctrlscript.use = use);

// ── Expose everything as globals so app.js needs zero imports ──
Object.assign(globalThis, ctrlscript);
globalThis.ctrlscript = ctrlscript;
globalThis.CS = ctrlscript;
