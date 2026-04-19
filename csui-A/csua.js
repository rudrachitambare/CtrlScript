// ──────────────────────────────────────────────────────
// CSUA — CtrlScript Android Runtime
// Same syntax as CSUI (web), runs on real Android Views
// via QuickJS + Java bridge (no WebView)
//
// Usage in app.js:
//   import { Box, Label, Button, device, perm } from './csua.js'
// ──────────────────────────────────────────────────────

'use strict';

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

export class BaseElement {
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
    set w(v)         { bridge.setProp(this._viewId, 'width',           _dp(v)); }
    set h(v)         { bridge.setProp(this._viewId, 'height',          _dp(v)); }
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

export class Box extends BaseElement {
    constructor(containerRef = null, props = {}) {
        if (containerRef !== null && typeof containerRef === 'object' && !Array.isArray(containerRef)) {
            props = containerRef; containerRef = null;
        }
        super(containerRef, 'LinearLayout');
        this._viewId = bridge.createView('LinearLayout');

        if (typeof containerRef === 'string' && !_namedContainers[containerRef]) {
            _namedContainers[containerRef] = this;
        }
        if (!_rootContainer) _rootContainer = this;

        if (Object.keys(props).length) this.props = props;
        this._attach();
    }
}

export class ScrollBox extends BaseElement {
    constructor(containerRef = null, props = {}) {
        super(containerRef, 'ScrollView');
        if (Object.keys(props).length) this.props = props;
        this._attach();
    }
}

export class Label extends BaseElement {
    constructor(containerRef = null, props = {}) {
        super(containerRef, 'TextView');
        if (typeof props === 'string') { bridge.setProp(this._viewId, 'text', props); }
        else if (Object.keys(props).length) this.props = props;
        this._attach();
    }
}
export const Text = Label;

export class Button extends BaseElement {
    constructor(containerRef = null, props = {}) {
        super(containerRef, 'Button');
        if (Object.keys(props).length) this.props = props;
        this._attach();
    }
}

export class Input extends BaseElement {
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
export const TextArea = Input;

export class Image extends BaseElement {
    constructor(containerRef = null, props = {}) {
        super(containerRef, 'ImageView');
        if (Object.keys(props).length) this.props = props;
        this._attach();
    }
    set src(v) { bridge.setProp(this._viewId, 'src', String(v)); }
}

export class Canvas extends BaseElement {
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

export class List extends BaseElement {
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
export class Rectangle extends BaseElement {
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
export const Square = Rectangle;

export class Circle extends BaseElement {
    constructor(containerRef = null, props = {}) {
        const { color = '#fff', ...rest } = props;
        super(containerRef, 'ShapeView');
        bridge.setProp(this._viewId, 'shapeType',  'oval');
        bridge.setProp(this._viewId, 'shapeColor', color);
        if (Object.keys(rest).length) this.props = rest;
        this._attach();
    }
}

export class SafeArea extends BaseElement {
    constructor(containerRef = null, props = {}) {
        super(containerRef, 'SafeAreaView');
        if (Object.keys(props).length) this.props = props;
        this._attach();
    }
}


// ──────────────────────────────────────────────────────
// §7  LAYOUT HELPER
// ──────────────────────────────────────────────────────

export function group(containerRef, props = {}, ...children) {
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

export function loop(fn) {
    _loops.add(fn);
    if (!_loopRunning) {
        _loopRunning = true;
        bridge.call('app', 'requestFrame', '_csuaTick');
    }
    return () => _loops.delete(fn);
}

export function stopLoop(fn) { _loops.delete(fn); }


// ──────────────────────────────────────────────────────
// §9  TIMERS
// ──────────────────────────────────────────────────────

export function after(ms, fn)  { return setTimeout(fn, ms); }
export function every(ms, fn)  { return setInterval(fn, ms); }
export function cancel(id)     { clearTimeout(id); clearInterval(id); }


// ──────────────────────────────────────────────────────
// §10  STORAGE
//      save / load  →  SharedPreferences (simple key/value)
//      db           →  SQLite
//      files        →  File system
// ──────────────────────────────────────────────────────

export function save(key, value) {
    bridge.call('storage', 'set', key, JSON.stringify(value));
}

export function load(key, fallback = null) {
    const raw = bridge.call('storage', 'get', key);
    if (raw === null || raw === undefined) return fallback;
    try { return JSON.parse(raw); } catch { return raw; }
}

export function remove(key) { bridge.call('storage', 'remove', key); }
export function clearAll()  { bridge.call('storage', 'clear'); }

export const db = {
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

export const files = {
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

export function fetch(url, opts = {}) {
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

export class WebSocket {
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

export const perm = {
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
export const permission  = perm;
export const permissions = perm;


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

export const device = {
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

export class Sound {
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

export const app = {
    onPause(fn)      { bridge.call('app', 'on', 'pause',      '_csuaAppPause');    globalThis._csuaAppPause    = fn; },
    onResume(fn)     { bridge.call('app', 'on', 'resume',     '_csuaAppResume');   globalThis._csuaAppResume   = fn; },
    onDestroy(fn)    { bridge.call('app', 'on', 'destroy',    '_csuaAppDestroy');  globalThis._csuaAppDestroy  = fn; },
    onBack(fn)       { bridge.call('app', 'on', 'back',       '_csuaAppBack');     globalThis._csuaAppBack     = fn; },
    onDeepLink(fn)   { bridge.call('app', 'on', 'deeplink',   '_csuaAppDeepLink'); globalThis._csuaAppDeepLink = fn; },
    onMemoryWarning(fn) { bridge.call('app', 'on', 'memory',  '_csuaAppMemory');   globalThis._csuaAppMemory   = fn; },
    exit()           { bridge.call('app', 'exit'); },
    get version()    { return bridge.call('app', 'version'); },
    get package()    { return bridge.call('app', 'package'); },
};


// ──────────────────────────────────────────────────────
// §16  ROUTER  (screen navigation — not URL)
// ──────────────────────────────────────────────────────

const _screens  = {};
let   _current  = null;
let   _history  = [];

export const router = {
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
export const scene = router.define.bind(router);
export const go    = router.go.bind(router);


// ──────────────────────────────────────────────────────
// §17  KEYBOARD
// ──────────────────────────────────────────────────────

export const keyboard = {
    hide()      { bridge.call('keyboard', 'hide'); },
    show(id)    { bridge.call('keyboard', 'show', id); },
    onShow(fn)  { globalThis._csuaKbShow = fn; bridge.call('keyboard', 'onShow', '_csuaKbShow'); },
    onHide(fn)  { globalThis._csuaKbHide = fn; bridge.call('keyboard', 'onHide', '_csuaKbHide'); },
};


// ──────────────────────────────────────────────────────
// §18  SYSTEM UI  (status bar, nav bar)
// ──────────────────────────────────────────────────────

export const statusBar = {
    color(v)        { bridge.call('systemui', 'statusBarColor', v); },
    style(v)        { bridge.call('systemui', 'statusBarStyle', v); }, // 'light'|'dark'
    hide()          { bridge.call('systemui', 'statusBarHide', 'true'); },
    show()          { bridge.call('systemui', 'statusBarHide', 'false'); },
};

export const navigationBar = {
    color(v)        { bridge.call('systemui', 'navBarColor', v); },
    hide()          { bridge.call('systemui', 'navBarHide', 'true'); },
    show()          { bridge.call('systemui', 'navBarHide', 'false'); },
};


// ──────────────────────────────────────────────────────
// §19  INTENTS  (open other apps / share)
// ──────────────────────────────────────────────────────

export function share(opts = {})     { bridge.call('intents', 'share',   JSON.stringify(opts)); }
export function openUrl(url)         { bridge.call('intents', 'openUrl', url); }
export function openApp(packageName) { bridge.call('intents', 'openApp', packageName); }
export function openSettings()       { bridge.call('intents', 'openSettings'); }
export function openMaps(opts = {})  { bridge.call('intents', 'openMaps', JSON.stringify(opts)); }


// ──────────────────────────────────────────────────────
// §20  NATIVE DIALOGS
// ──────────────────────────────────────────────────────

export const dialog = {
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

export function toast(message, { duration = 'short', gravity = 'bottom' } = {}) {
    bridge.call('dialog', 'toast', message, duration, gravity);
}


// ──────────────────────────────────────────────────────
// §22  BACKGROUND TASKS
// ──────────────────────────────────────────────────────

export const background = {
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
// §23  ANIMATIONS  (native Android property animations)
// ──────────────────────────────────────────────────────

export function animate(element, props = {}, { duration = 300, easing = 'ease', delay = 0 } = {}) {
    return new Promise(resolve => {
        const cbId = `_csuaAnim_${++_idCounter}`;
        globalThis[cbId] = () => { delete globalThis[cbId]; resolve(); };
        bridge.call('anim', 'animate', element._viewId, JSON.stringify(props), duration, easing, delay, cbId);
    });
}


// ──────────────────────────────────────────────────────
// §24  INPUT EVENTS  (touch, gesture)
// ──────────────────────────────────────────────────────

export function onTouch(element, fn) {
    const cbId = `_csuaTouch_${element._viewId}`;
    globalThis[cbId] = data => fn(JSON.parse(data));
    bridge.call('events', 'onTouch', element._viewId, cbId);
    return () => { delete globalThis[cbId]; bridge.call('events', 'removeTouch', element._viewId, cbId); };
}

export function onSwipe(element, fn) {
    const cbId = `_csuaSwipe_${element._viewId}`;
    globalThis[cbId] = data => fn(JSON.parse(data));
    bridge.call('events', 'onSwipe', element._viewId, cbId);
    return () => { delete globalThis[cbId]; bridge.call('events', 'removeSwipe', element._viewId, cbId); };
}

export function onPinch(element, fn) {
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

export function use(plugin) {
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

export const ctrlscript = {
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
    // Plugin
    use,
};

export const CS = ctrlscript;
use.bind && (ctrlscript.use = use);
