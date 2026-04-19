package com.ctrlscript.csua;

import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.camera2.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;

import org.json.JSONObject;

public class ModuleDevice implements CsuaModule {

    private final Bootstrap _ctx;
    private PowerManager.WakeLock _wakeLock;
    private String _flashCameraId;

    public ModuleDevice(Bootstrap ctx) { _ctx = ctx; }

    @Override
    public Object call(String method, Object... args) {
        switch (method) {
            // ── Info ──
            case "info": return _info();

            // ── Vibration ──
            case "vibrate": _vibrate(s(args[0])); return null;

            // ── Flashlight ──
            case "flashlight": _flashlight(s(args[0])); return null;

            // ── Screen ──
            case "screenshot":   return null; // TODO: requires MediaProjection API
            case "getBrightness": return String.valueOf(_getBrightness());
            case "setBrightness": _setBrightness(Float.parseFloat(s(args[0]))); return null;
            case "wakeLock":      _wakeLock("true".equals(s(args[0]))); return null;
            case "orientation":   _orientation(s(args[0])); return null;

            // ── Clipboard ──
            case "copy":  _copy(s(args[0]));  return null;
            case "paste": return _paste();

            // ── System UI ──
            case "statusBarColor":  _statusBarColor(s(args[0]));            return null;
            case "statusBarStyle":  _statusBarStyle(s(args[0]));            return null;
            case "statusBarHide":   _statusBarHide("true".equals(s(args[0]))); return null;
            case "navBarColor":     _navBarColor(s(args[0]));               return null;
            case "navBarHide":      _navBarHide("true".equals(s(args[0]))); return null;

            // ── Keyboard ──
            case "hide": _hideKeyboard(); return null;
            case "show": _showKeyboard(_int(args[0])); return null;
            case "onShow": _kbOnShow(s(args[0])); return null;
            case "onHide": _kbOnHide(s(args[0])); return null;

            // ── App ──
            case "version": return _ctx.getPackageManager().getLaunchIntentForPackage(_ctx.getPackageName()) != null
                ? _appVersion() : "1.0.0";
            case "package": return _ctx.getPackageName();
        }
        return null;
    }

    // ── Info ─────────────────────────────────────────────

    private String _info() {
        try {
            DisplayMetrics dm = _ctx.getResources().getDisplayMetrics();
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent bat = _ctx.registerReceiver(null, ifilter);
            int level   = bat != null ? bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
            int scale   = bat != null ? bat.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : 1;
            int status  = bat != null ? bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1) : -1;
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                            || status == BatteryManager.BATTERY_STATUS_FULL;

            ConnectivityManager cm = (ConnectivityManager) _ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            String netType = ni == null ? "none"
                : ni.getType() == ConnectivityManager.TYPE_WIFI ? "wifi" : "cellular";

            return new JSONObject()
                .put("model",     Build.MODEL)
                .put("brand",     Build.BRAND)
                .put("android",   Build.VERSION.SDK_INT)
                .put("version",   Build.VERSION.RELEASE)
                .put("screen",    new JSONObject()
                    .put("w",   dm.widthPixels)
                    .put("h",   dm.heightPixels)
                    .put("dpi", (int) dm.densityDpi))
                .put("battery",   new JSONObject()
                    .put("level",    level >= 0 ? Math.round(level * 100f / scale) : -1)
                    .put("charging", charging))
                .put("network",   new JSONObject()
                    .put("type",      netType)
                    .put("connected", ni != null && ni.isConnected()))
                .put("language",  _ctx.getResources().getConfiguration().locale.toString())
                .put("timezone",  java.util.TimeZone.getDefault().getID())
                .toString();
        } catch (Exception e) { return "{}"; }
    }

    // ── Vibration ────────────────────────────────────────

    private void _vibrate(String pattern) {
        Vibrator v = (Vibrator) _ctx.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (pattern.contains(",")) {
            String[] parts = pattern.split(",");
            long[] timings = new long[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try { timings[i] = Long.parseLong(parts[i].trim()); } catch (Exception e) { timings[i] = 0; }
            }
            if (Build.VERSION.SDK_INT >= 26)
                v.vibrate(VibrationEffect.createWaveform(timings, -1));
            else v.vibrate(timings, -1);
        } else {
            long ms = Long.parseLong(pattern.trim());
            if (Build.VERSION.SDK_INT >= 26)
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        }
    }

    // ── Flashlight ───────────────────────────────────────

    private void _flashlight(String action) {
        try {
            CameraManager cm = (CameraManager) _ctx.getSystemService(Context.CAMERA_SERVICE);
            if (_flashCameraId == null) {
                for (String id : cm.getCameraIdList()) {
                    CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                    Boolean flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (Boolean.TRUE.equals(flash)) { _flashCameraId = id; break; }
                }
            }
            if (_flashCameraId == null) return;
            boolean on = "on".equals(action) || ("toggle".equals(action));
            cm.setTorchMode(_flashCameraId, on);
        } catch (Exception e) { android.util.Log.e("CSUA", "Flashlight error", e); }
    }

    // ── Screen ───────────────────────────────────────────

    private float _getBrightness() {
        try { return Settings.System.getInt(_ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f; }
        catch (Exception e) { return -1; }
    }

    private void _setBrightness(float v) {
        _ctx._ui.post(() -> {
            Window w = _ctx.getWindow();
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.screenBrightness = Math.max(0f, Math.min(1f, v));
            w.setAttributes(lp);
        });
    }

    private void _wakeLock(boolean on) {
        if (on) {
            if (_wakeLock == null) {
                PowerManager pm = (PowerManager) _ctx.getSystemService(Context.POWER_SERVICE);
                _wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "csua:wakelock");
            }
            if (!_wakeLock.isHeld()) _wakeLock.acquire();
        } else {
            if (_wakeLock != null && _wakeLock.isHeld()) _wakeLock.release();
        }
    }

    private void _orientation(String o) {
        int req;
        switch (o) {
            case "portrait":  req = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;  break;
            case "landscape": req = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; break;
            default:          req = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
        _ctx._ui.post(() -> _ctx.setRequestedOrientation(req));
    }

    // ── Clipboard ────────────────────────────────────────

    private void _copy(String text) {
        _ctx._ui.post(() -> {
            ClipboardManager cm = (ClipboardManager) _ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("csua", text));
        });
    }

    private String _paste() {
        ClipboardManager cm = (ClipboardManager) _ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm.hasPrimaryClip() && cm.getPrimaryClip() != null) {
            ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
            return item != null ? item.coerceToText(_ctx).toString() : "";
        }
        return "";
    }

    // ── System UI ────────────────────────────────────────

    private void _statusBarColor(String color) {
        _ctx._ui.post(() -> {
            if (Build.VERSION.SDK_INT >= 21)
                _ctx.getWindow().setStatusBarColor(android.graphics.Color.parseColor(color));
        });
    }

    private void _statusBarStyle(String style) {
        _ctx._ui.post(() -> {
            View decor = _ctx.getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if ("light".equals(style)) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decor.setSystemUiVisibility(flags);
        });
    }

    private void _statusBarHide(boolean hide) {
        _ctx._ui.post(() -> {
            View decor = _ctx.getWindow().getDecorView();
            int flags = hide
                ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                : 0;
            decor.setSystemUiVisibility(flags);
        });
    }

    private void _navBarColor(String color) {
        _ctx._ui.post(() -> {
            if (Build.VERSION.SDK_INT >= 21)
                _ctx.getWindow().setNavigationBarColor(android.graphics.Color.parseColor(color));
        });
    }

    private void _navBarHide(boolean hide) {
        _ctx._ui.post(() -> {
            View decor = _ctx.getWindow().getDecorView();
            int flags = hide
                ? View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                : 0;
            decor.setSystemUiVisibility(flags);
        });
    }

    // ── Keyboard ─────────────────────────────────────────

    private void _hideKeyboard() {
        _ctx._ui.post(() -> {
            InputMethodManager imm = (InputMethodManager) _ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            View focus = _ctx.getCurrentFocus();
            if (imm != null && focus != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        });
    }

    private void _showKeyboard(int viewId) {
        _ctx._ui.post(() -> {
            View v = _ctx._views.get(viewId);
            if (v == null) return;
            v.requestFocus();
            InputMethodManager imm = (InputMethodManager) _ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void _kbOnShow(String cbId) {
        // ViewTreeObserver keyboard detection
        _ctx._ui.post(() -> {
            View root = _ctx._root;
            root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                android.graphics.Rect r = new android.graphics.Rect();
                root.getWindowVisibleDisplayFrame(r);
                int screenH = root.getRootView().getHeight();
                int keyH = screenH - r.bottom;
                if (keyH > screenH * 0.15) _ctx.fireCallback(cbId, null, String.valueOf(keyH));
            });
        });
    }

    private void _kbOnHide(String cbId) {
        _ctx._ui.post(() -> {
            View root = _ctx._root;
            root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                android.graphics.Rect r = new android.graphics.Rect();
                root.getWindowVisibleDisplayFrame(r);
                int screenH = root.getRootView().getHeight();
                int keyH = screenH - r.bottom;
                if (keyH < screenH * 0.15) _ctx.fireCallback(cbId, null, null);
            });
        });
    }

    private String _appVersion() {
        try { return _ctx.getPackageManager().getPackageInfo(_ctx.getPackageName(), 0).versionName; }
        catch (Exception e) { return "1.0.0"; }
    }

    static String s(Object o) { return o != null ? o.toString() : ""; }
    static int _int(Object o) { try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; } }
}
