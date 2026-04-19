package com.ctrlscript.csua;

import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

public class ModuleIntents implements CsuaModule {

    private final Bootstrap _ctx;

    public ModuleIntents(Bootstrap ctx) { _ctx = ctx; }

    @Override
    public Object call(String method, Object... args) {
        switch (method) {
            case "share":        _share(s(args[0]));               return null;
            case "openUrl":      _openUrl(s(args[0]));             return null;
            case "openApp":      _openApp(s(args[0]));             return null;
            case "openSettings": _openSettings();                  return null;
            case "openMaps":     _openMaps(s(args[0]));            return null;
            case "sendSms":      _sendSms(s(args[0]), s(args[1]), s(args[2])); return null;
            case "dial":         _dial(s(args[0]));                return null;
        }
        return null;
    }

    private void _share(String optsJson) {
        _ctx._ui.post(() -> {
            try {
                JSONObject opts = new JSONObject(optsJson);
                Intent intent = new Intent(Intent.ACTION_SEND);
                String text = opts.optString("text", "");
                String url  = opts.optString("url", "");
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, text + (url.isEmpty() ? "" : "\n" + url));
                String title = opts.optString("title", "Share");
                _ctx.startActivity(Intent.createChooser(intent, title));
            } catch (Exception e) { android.util.Log.e("CSUA", "Share error", e); }
        });
    }

    private void _openUrl(String url) {
        _ctx._ui.post(() -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            _ctx.startActivity(intent);
        });
    }

    private void _openApp(String packageName) {
        _ctx._ui.post(() -> {
            Intent intent = _ctx.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) _ctx.startActivity(intent);
            else android.util.Log.w("CSUA", "App not installed: " + packageName);
        });
    }

    private void _openSettings() {
        _ctx._ui.post(() -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", _ctx.getPackageName(), null));
            _ctx.startActivity(intent);
        });
    }

    private void _openMaps(String optsJson) {
        _ctx._ui.post(() -> {
            try {
                JSONObject opts = new JSONObject(optsJson);
                double lat   = opts.optDouble("lat", 0);
                double lng   = opts.optDouble("lng", 0);
                String label = opts.optString("label", "");
                Uri uri = Uri.parse("geo:" + lat + "," + lng + "?q=" + lat + "," + lng +
                    (label.isEmpty() ? "" : "(" + Uri.encode(label) + ")"));
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                _ctx.startActivity(intent);
            } catch (Exception e) { android.util.Log.e("CSUA", "openMaps error", e); }
        });
    }

    private void _sendSms(String cbId, String number, String message) {
        _ctx._ui.post(() -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number));
            intent.putExtra("sms_body", message);
            _ctx.startActivity(intent);
            _ctx.fireCallback(cbId, null, "true");
        });
    }

    private void _dial(String number) {
        _ctx._ui.post(() -> {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
            _ctx.startActivity(intent);
        });
    }

    static String s(Object o) { return o != null ? o.toString() : ""; }
}
