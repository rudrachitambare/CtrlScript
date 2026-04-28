package com.ctrlscript.csua;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Bootstrap — entry point for every CSUA app.
 * Loads QuickJS, implements BridgeInterface, evaluates csua.js + app.js.
 * Users never touch this file.
 */
public class Bootstrap extends Activity implements BridgeInterface {

    private static final String TAG = "CSUA";

    // QuickJS context pointer (native)
    private long _jsContext;

    // Lazy module registry
    private final Map<String, CsuaModule> _modules = new HashMap<>();

    // Main thread handler for UI operations
    final Handler _ui = new Handler(Looper.getMainLooper());

    // Root layout that fills the screen
    LinearLayout _root;

    // View registry — JS view ID → Android View
    final Map<Integer, View> _views = new HashMap<>();
    private int _viewCounter = 0;

    // Hot reload
    private BroadcastReceiver _reloadReceiver;
    private FileObserver       _hotWatcher;

    // ── Activity lifecycle ───────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        _root = new LinearLayout(this);
        _root.setOrientation(LinearLayout.VERTICAL);
        _root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(_root);
        _views.put(0, _root);

        _registerModules();
        _startJS();
        _setupDevGesture();
    }

    @Override protected void onPause()   { super.onPause();   _fireLifecycle("_csuaAppPause");   }
    @Override protected void onResume()  { super.onResume();  _fireLifecycle("_csuaAppResume");  }
    @Override protected void onDestroy() {
        super.onDestroy();
        _fireLifecycle("_csuaAppDestroy");
        if (_hotWatcher    != null) _hotWatcher.stopWatching();
        if (_reloadReceiver != null) try { unregisterReceiver(_reloadReceiver); } catch (Exception ignored) {}
        QuickJS.destroy(_jsContext);
    }

    @Override
    public void onBackPressed() {
        Object handled = QuickJS.eval(_jsContext,
            "if(typeof _csuaAppBack==='function'){_csuaAppBack();true;}else{false;}", "<back>");
        if (!"true".equals(handled)) super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ModulePermissions mp = (ModulePermissions) _modules.get("permissions");
        if (mp != null) mp.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ModuleCamera mc = (ModuleCamera) _modules.get("camera");
        if (mc != null) mc.onActivityResult(requestCode, resultCode, data);
        ModuleDialog md = (ModuleDialog) _modules.get("dialog");
        if (md != null) md.onActivityResult(requestCode, resultCode, data);
    }

    // ── BridgeInterface ──────────────────────────────────
    // These 4 methods are called directly from C (quickjs_jni.c)
    // via the BridgeInterface JNI method IDs.

    @Override
    public int createView(String type) {
        int[] result = { 0 };
        CountDownLatch latch = new CountDownLatch(1);
        _ui.post(() -> {
            View v = ModuleViews.create(this, type);
            if (v != null) {
                int id = ++_viewCounter;
                _views.put(id, v);
                result[0] = id;
            }
            latch.countDown();
        });
        try { latch.await(); } catch (InterruptedException ignored) {}
        return result[0];
    }

    @Override
    public void setProp(int viewId, String key, Object value) {
        _ui.post(() -> {
            View v = _views.get(viewId);
            if (v != null) ModuleViews.setProp(this, v, key, value);
        });
    }

    @Override
    public void addChild(int parentId, int childId) {
        _ui.post(() -> {
            View parent = _views.get(parentId);
            View child  = _views.get(childId);
            if (parent instanceof ViewGroup && child != null) {
                ((ViewGroup) parent).addView(child);
            }
        });
    }

    @Override
    public Object call(String module, String method, Object[] args) {
        CsuaModule m = _modules.get(module);
        if (m == null) {
            Log.w(TAG, "Unknown module: " + module);
            return null;
        }
        try {
            return m.call(method, args);
        } catch (Exception e) {
            Log.e(TAG, "Module error [" + module + "." + method + "]", e);
            return null;
        }
    }

    // ── Module registration ──────────────────────────────

    private void _registerModules() {
        _modules.put("views",         new ModuleViews(this));
        _modules.put("storage",       new ModuleStorage(this));
        _modules.put("db",            new ModuleStorage(this));
        _modules.put("files",         new ModuleStorage(this));
        _modules.put("network",       new ModuleNetwork(this));
        _modules.put("permissions",   new ModulePermissions(this));
        _modules.put("camera",        new ModuleCamera(this));
        _modules.put("gallery",       new ModuleCamera(this));
        _modules.put("sensors",       new ModuleSensors(this));
        _modules.put("audio",         new ModuleAudio(this));
        _modules.put("tts",           new ModuleAudio(this));
        _modules.put("device",        new ModuleDevice(this));
        _modules.put("bluetooth",     new ModuleBluetooth(this));
        _modules.put("nfc",           new ModuleNFC(this));
        _modules.put("biometric",     new ModuleBiometric(this));
        _modules.put("keystore",      new ModuleBiometric(this));
        _modules.put("background",    new ModuleBackground(this));
        _modules.put("dialog",        new ModuleDialog(this));
        _modules.put("notifications", new ModuleNotifications(this));
        _modules.put("intents",       new ModuleIntents(this));
        _modules.put("clipboard",     new ModuleDevice(this));
        _modules.put("systemui",      new ModuleDevice(this));
        _modules.put("keyboard",      new ModuleDevice(this));
        _modules.put("canvas",        new ModuleViews(this));
        _modules.put("list",          new ModuleViews(this));
        _modules.put("anim",          new ModuleViews(this));
        _modules.put("events",        new ModuleViews(this));
        _modules.put("app",           new ModuleApp(this));
    }

    // ── JS startup ──────────────────────────────────────

    private void _startJS() {
        new Thread(() -> {
            try {
                _jsContext = QuickJS.createContext();
                QuickJS.injectBridge(_jsContext, this);
                String csua = _readAsset("csua.js");
                String app  = _readAsset("app.js");
                QuickJS.eval(_jsContext, csua, "csua.js");
                QuickJS.eval(_jsContext, app,  "app.js");
                _ui.post(this::_startHotReload);
            } catch (Exception e) {
                Log.e(TAG, "JS startup failed", e);
                _ui.post(() -> _showError(e.getMessage()));
            }
        }, "csua-js").start();
    }

    // ── Public accessors (used by modules) ───────────────

    /** Returns the native QuickJS context pointer. Used by ModuleApp. */
    public long _jsCtx() { return _jsContext; }

    // ── JS callback helpers ──────────────────────────────

    public void fireCallback(String cbId, String err, String result) {
        String script = err != null
            ? String.format("if(typeof %s==='function'){%s('%s',null)}", cbId, cbId, err.replace("'", "\\'"))
            : String.format("if(typeof %s==='function'){%s(null,%s)}", cbId, cbId,
                result != null ? "'" + result.replace("'", "\\'") + "'" : "undefined");
        runOnJSThread(() -> QuickJS.eval(_jsContext, script, "<callback>"));
    }

    public void fireRawCallback(String cbId, String jsonArgs) {
        String script = String.format(
            "if(typeof %s==='function'){var _a=%s;%s.apply(null,Array.isArray(_a)?_a:[_a])}",
            cbId, jsonArgs, cbId);
        runOnJSThread(() -> QuickJS.eval(_jsContext, script, "<callback>"));
    }

    public void runOnJSThread(Runnable r) {
        new Thread(r, "csua-cb").start();
    }

    // ── Hot reload ───────────────────────────────────────
    // Activated automatically after JS starts.
    // Triggers: (1) adb push app.js /sdcard/csua/app.js  →  FileObserver
    //           (2) adb shell am broadcast -a com.csua.RELOAD  →  BroadcastReceiver

    private void _startHotReload() {
        File hotDir = new File(android.os.Environment.getExternalStorageDirectory(), "csua");
        hotDir.mkdirs();

        // FileObserver — fires when ADB finishes pushing a file
        _hotWatcher = _makeFileObserver(hotDir);
        _hotWatcher.startWatching();

        // BroadcastReceiver — explicit signal from CLI (or dev overlay "Reload JS" button)
        _reloadReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) { _reloadAppJS(); }
        };
        ContextCompat.registerReceiver(this, _reloadReceiver,
            new IntentFilter("com.csua.RELOAD"), ContextCompat.RECEIVER_EXPORTED);

        Log.d(TAG, "Hot reload active — watching /sdcard/csua/");
    }

    @SuppressWarnings("deprecation")
    private FileObserver _makeFileObserver(File dir) {
        if (Build.VERSION.SDK_INT >= 29) {
            return new FileObserver(dir, FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
                @Override public void onEvent(int event, String path) {
                    if ("app.js".equals(path) || "csua.js".equals(path)) _reloadAppJS();
                }
            };
        } else {
            return new FileObserver(dir.getAbsolutePath(),
                    FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
                @Override public void onEvent(int event, String path) {
                    if ("app.js".equals(path) || "csua.js".equals(path)) _reloadAppJS();
                }
            };
        }
    }

    public void _reloadAppJS() {
        runOnJSThread(() -> {
            try {
                String csua = _readAsset("csua.js");
                String app  = _readAsset("app.js");
                QuickJS.eval(_jsContext, csua, "csua.js");
                QuickJS.eval(_jsContext, app,  "app.js");
                Log.d(TAG, "Hot reload OK");
            } catch (Exception e) {
                Log.e(TAG, "Hot reload failed", e);
                _ui.post(() -> _showError("Hot reload: " + e.getMessage()));
            }
        });
    }

    // ── Internals ────────────────────────────────────────

    private void _fireLifecycle(String fn) {
        runOnJSThread(() -> QuickJS.eval(_jsContext,
            "if(typeof " + fn + "==='function'){" + fn + "()}", "<lifecycle>"));
    }

    // ── Dev tools ────────────────────────────────────────
    // 5-finger tap anywhere → opens dev overlay (debug builds only)

    private void _setupDevGesture() {
        _root.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_POINTER_DOWN
                    && event.getPointerCount() == 5) {
                _ui.post(this::_showDevOverlay);
            }
            return false; // don't consume — pass through to children
        });
    }

    public void _showDevOverlay() {
        // Remove existing overlay if present
        View existing = _root.findViewWithTag("_csuaDevOverlay");
        if (existing != null) { _root.removeView(existing); return; }

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setTag("_csuaDevOverlay");
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xEE111827);
        panel.setPadding(24, 60, 24, 24);

        // Title
        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("CSUA Dev Tools");
        title.setTextColor(0xFF60A5FA);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(title);

        // Info rows
        String[] info = {
            "Package: " + getPackageName(),
            "Version: " + _appVersion(),
            "JS Thread: csua-js",
            "QuickJS ctx: 0x" + Long.toHexString(_jsContext),
            "Views allocated: " + _viewCounter,
            "Modules loaded: " + _modules.size(),
        };
        for (String line : info) {
            android.widget.TextView tv = new android.widget.TextView(this);
            tv.setText(line);
            tv.setTextColor(0xFFD1D5DB);
            tv.setTextSize(12);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setPadding(0, 4, 0, 4);
            panel.addView(tv);
        }

        // Reload button
        android.widget.Button reload = new android.widget.Button(this);
        reload.setText("Reload JS");
        reload.setTextColor(0xFFFFFFFF);
        reload.setBackgroundColor(0xFF2563EB);
        reload.setPadding(16, 12, 16, 12);
        reload.setOnClickListener(x -> {
            _root.removeView(scroll);
            _reloadAppJS();
        });
        panel.addView(reload);

        // Eval box
        android.widget.EditText evalBox = new android.widget.EditText(this);
        evalBox.setHint("Eval JS expression…");
        evalBox.setTextColor(0xFFFFFFFF);
        evalBox.setHintTextColor(0xFF6B7280);
        evalBox.setBackgroundColor(0xFF1F2937);
        evalBox.setPadding(12, 10, 12, 10);
        evalBox.setTypeface(android.graphics.Typeface.MONOSPACE);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = 12;
        evalBox.setLayoutParams(ep);
        panel.addView(evalBox);

        android.widget.TextView evalOut = new android.widget.TextView(this);
        evalOut.setTextColor(0xFF34D399);
        evalOut.setTextSize(11);
        evalOut.setTypeface(android.graphics.Typeface.MONOSPACE);
        evalOut.setPadding(0, 6, 0, 0);
        panel.addView(evalOut);

        android.widget.Button evalBtn = new android.widget.Button(this);
        evalBtn.setText("Run");
        evalBtn.setBackgroundColor(0xFF065F46);
        evalBtn.setTextColor(0xFFFFFFFF);
        evalBtn.setOnClickListener(x -> {
            String expr = evalBox.getText().toString().trim();
            if (expr.isEmpty()) return;
            runOnJSThread(() -> {
                Object res = QuickJS.eval(_jsContext, expr, "<devtools>");
                _ui.post(() -> evalOut.setText("→ " + res));
            });
        });
        panel.addView(evalBtn);

        // Close button
        android.widget.Button close = new android.widget.Button(this);
        close.setText("Close");
        close.setBackgroundColor(0xFF374151);
        close.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = 8;
        close.setLayoutParams(cp);
        close.setOnClickListener(x -> _root.removeView(scroll));
        panel.addView(close);

        scroll.addView(panel);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        _root.addView(scroll, lp);
    }

    private String _appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) { return "?"; }
    }

    private void _showError(String message) {
        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackgroundColor(0xFFCC0000);
        overlay.setPadding(32, 80, 32, 32);

        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("CSUA Error");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        android.widget.TextView msg = new android.widget.TextView(this);
        msg.setText(message);
        msg.setTextColor(0xFFFFDDDD);
        msg.setTextSize(13);
        msg.setTypeface(android.graphics.Typeface.MONOSPACE);
        msg.setPadding(0, 16, 0, 0);

        overlay.addView(title);
        overlay.addView(msg);
        _root.addView(overlay);
    }

    private String _readAsset(String filename) throws Exception {
        // Hot reload: check /sdcard/csua/ first
        java.io.File devFile = new java.io.File(
            android.os.Environment.getExternalStorageDirectory(), "csua/" + filename);
        if (devFile.exists()) {
            return new String(java.nio.file.Files.readAllBytes(devFile.toPath()), StandardCharsets.UTF_8);
        }
        try (InputStream is = getAssets().open(filename)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
