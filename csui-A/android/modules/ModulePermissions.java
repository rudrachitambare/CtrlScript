package com.ctrlscript.csua;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class ModulePermissions implements CsuaModule {

    private final Bootstrap _ctx;

    // Pending request: cbId waiting for result
    private String _pendingCbId;
    private String[] _pendingPerms;
    private static final int REQ_CODE = 9001;

    // Friendly name → Android manifest permission
    private static final Map<String, String> _map = new HashMap<>();
    static {
        _map.put("camera",       Manifest.permission.CAMERA);
        _map.put("mic",          Manifest.permission.RECORD_AUDIO);
        _map.put("microphone",   Manifest.permission.RECORD_AUDIO);
        _map.put("location",     Manifest.permission.ACCESS_FINE_LOCATION);
        _map.put("locationCoarse", Manifest.permission.ACCESS_COARSE_LOCATION);
        _map.put("storage",      Manifest.permission.READ_EXTERNAL_STORAGE);
        _map.put("write",        Manifest.permission.WRITE_EXTERNAL_STORAGE);
        _map.put("contacts",     Manifest.permission.READ_CONTACTS);
        _map.put("writeContacts",Manifest.permission.WRITE_CONTACTS);
        _map.put("calendar",     Manifest.permission.READ_CALENDAR);
        _map.put("sms",          Manifest.permission.SEND_SMS);
        _map.put("readSms",      Manifest.permission.RECEIVE_SMS);
        _map.put("phone",        Manifest.permission.CALL_PHONE);
        _map.put("readPhone",    Manifest.permission.READ_PHONE_STATE);
        _map.put("bluetooth",    Manifest.permission.BLUETOOTH);
        _map.put("bluetoothScan",Manifest.permission.BLUETOOTH_SCAN);
        _map.put("nfc",          Manifest.permission.NFC);
        _map.put("body",         Manifest.permission.BODY_SENSORS);
        _map.put("activity",     Build.VERSION.SDK_INT >= 29 ? Manifest.permission.ACTIVITY_RECOGNITION : Manifest.permission.BODY_SENSORS);
        _map.put("notifications", Build.VERSION.SDK_INT >= 33 ? Manifest.permission.POST_NOTIFICATIONS : "");
    }

    public ModulePermissions(Bootstrap ctx) { _ctx = ctx; }

    @Override
    public Object call(String method, Object... args) {
        switch (method) {
            case "request":     _request(s(args[0]), s(args[1])); return null;
            case "check":       return _check(s(args[0]));
            case "openSettings":
                android.content.Intent i = new android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", _ctx.getPackageName(), null));
                _ctx.startActivity(i);
                return null;
        }
        return null;
    }

    private void _request(String cbId, String permsJson) {
        try {
            JSONArray arr = new JSONArray(permsJson);
            List<String> manifest = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String friendly = arr.getString(i);
                String mp = _map.get(friendly);
                if (mp != null && !mp.isEmpty()) manifest.add(mp);
            }
            String[] toRequest = manifest.stream()
                .filter(p -> ContextCompat.checkSelfPermission(_ctx, p) != PackageManager.PERMISSION_GRANTED)
                .toArray(String[]::new);

            if (toRequest.length == 0) {
                // All already granted
                JSONObject results = new JSONObject();
                for (int i = 0; i < arr.length(); i++) results.put(arr.getString(i), "granted");
                _ctx.fireCallback(cbId, null, results.toString());
                return;
            }

            _pendingCbId  = cbId;
            _pendingPerms = manifest.toArray(new String[0]);
            _ctx._ui.post(() -> ActivityCompat.requestPermissions(_ctx, toRequest, REQ_CODE));

        } catch (Exception e) { _ctx.fireCallback(cbId, e.getMessage(), null); }
    }

    // Called by Bootstrap.onRequestPermissionsResult
    public void onResult(int reqCode, String[] perms, int[] results) {
        if (reqCode != REQ_CODE || _pendingCbId == null) return;
        try {
            JSONObject out = new JSONObject();
            for (int i = 0; i < perms.length; i++) {
                String friendly = _friendly(perms[i]);
                out.put(friendly, results[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied");
            }
            _ctx.fireCallback(_pendingCbId, null, out.toString());
        } catch (Exception e) { _ctx.fireCallback(_pendingCbId, e.getMessage(), null); }
        _pendingCbId = null;
    }

    private String _check(String friendly) {
        String mp = _map.get(friendly);
        if (mp == null || mp.isEmpty()) return "granted";
        int state = ContextCompat.checkSelfPermission(_ctx, mp);
        if (state == PackageManager.PERMISSION_GRANTED) return "granted";
        if (ActivityCompat.shouldShowRequestPermissionRationale(_ctx, mp)) return "denied";
        return "never_asked";
    }

    private String _friendly(String manifestPerm) {
        for (Map.Entry<String, String> e : _map.entrySet())
            if (e.getValue().equals(manifestPerm)) return e.getKey();
        return manifestPerm;
    }

    static String s(Object o) { return o != null ? o.toString() : ""; }
}
