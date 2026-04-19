package com.ctrlscript.csua;

import android.content.Context;
import android.hardware.*;
import android.location.*;
import android.os.Bundle;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ModuleSensors implements CsuaModule {

    private final Bootstrap _ctx;
    private final SensorManager _sm;
    private final LocationManager _lm;
    private final Map<String, SensorEventListener> _listeners = new HashMap<>();
    private final Map<String, LocationListener>    _locListeners = new HashMap<>();

    public ModuleSensors(Bootstrap ctx) {
        _ctx = ctx;
        _sm  = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        _lm  = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public Object call(String method, Object... args) {
        String cbId = s(args[0]);
        switch (method) {
            case "getLocation":      _getLocation(cbId);                            return null;
            case "watchLocation":    _watchLocation(cbId);                          return null;
            case "watchLocationStop":_stopLocation(cbId);                           return null;
            case "tilt":             _watchSensor(cbId, Sensor.TYPE_ROTATION_VECTOR, "tilt");         return null;
            case "tiltStop":         _stopSensor(cbId);                             return null;
            case "accelerometer":    _watchSensor(cbId, Sensor.TYPE_ACCELEROMETER,  "accelerometer"); return null;
            case "accelerometerStop":_stopSensor(cbId);                             return null;
            case "gyroscope":        _watchSensor(cbId, Sensor.TYPE_GYROSCOPE,      "gyroscope");     return null;
            case "gyroscopeStop":    _stopSensor(cbId);                             return null;
            case "compass":          _watchSensor(cbId, Sensor.TYPE_MAGNETIC_FIELD, "compass");       return null;
            case "compassStop":      _stopSensor(cbId);                             return null;
            case "proximity":        _watchSensor(cbId, Sensor.TYPE_PROXIMITY,      "proximity");     return null;
            case "proximityStop":    _stopSensor(cbId);                             return null;
            case "light":            _watchSensor(cbId, Sensor.TYPE_LIGHT,          "light");         return null;
            case "lightStop":        _stopSensor(cbId);                             return null;
            case "pressure":         _watchSensor(cbId, Sensor.TYPE_PRESSURE,       "pressure");      return null;
            case "pressureStop":     _stopSensor(cbId);                             return null;
            case "pedometer":        _watchSensor(cbId, Sensor.TYPE_STEP_COUNTER,   "pedometer");     return null;
            case "pedometerStop":    _stopSensor(cbId);                             return null;
        }
        return null;
    }

    // ── Location ─────────────────────────────────────────

    private void _getLocation(String cbId) {
        try {
            String provider = _lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            Location last = _lm.getLastKnownLocation(provider);
            if (last != null) { _ctx.fireCallback(cbId, null, _locJson(last)); return; }
            // Request one update
            LocationListener ll = new LocationListener() {
                public void onLocationChanged(Location loc) {
                    _ctx.fireCallback(cbId, null, _locJson(loc));
                    _ctx._ui.post(() -> _lm.removeUpdates(this));
                }
                public void onStatusChanged(String p, int s, Bundle b) {}
                public void onProviderEnabled(String p) {}
                public void onProviderDisabled(String p) {}
            };
            _ctx._ui.post(() -> {
                try { _lm.requestSingleUpdate(provider, ll, _ctx.getMainLooper()); }
                catch (SecurityException e) { _ctx.fireCallback(cbId, "permission_denied", null); }
            });
        } catch (Exception e) { _ctx.fireCallback(cbId, e.getMessage(), null); }
    }

    private void _watchLocation(String cbId) {
        LocationListener ll = new LocationListener() {
            public void onLocationChanged(Location loc) { _ctx.fireCallback(cbId, null, _locJson(loc)); }
            public void onStatusChanged(String p, int s, Bundle b) {}
            public void onProviderEnabled(String p) {}
            public void onProviderDisabled(String p) {}
        };
        _locListeners.put(cbId, ll);
        _ctx._ui.post(() -> {
            try {
                String provider = _lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
                _lm.requestLocationUpdates(provider, 1000, 1, ll, _ctx.getMainLooper());
            } catch (SecurityException e) { _ctx.fireCallback(cbId, "permission_denied", null); }
        });
    }

    private void _stopLocation(String cbId) {
        LocationListener ll = _locListeners.remove(cbId);
        if (ll != null) _ctx._ui.post(() -> _lm.removeUpdates(ll));
    }

    private String _locJson(Location loc) {
        try {
            return new JSONObject()
                .put("lat",      loc.getLatitude())
                .put("lng",      loc.getLongitude())
                .put("accuracy", loc.getAccuracy())
                .put("altitude", loc.getAltitude())
                .put("speed",    loc.getSpeed())
                .toString();
        } catch (Exception e) { return "{}"; }
    }

    // ── Sensors ──────────────────────────────────────────

    private void _watchSensor(String cbId, int type, String kind) {
        Sensor sensor = _sm.getDefaultSensor(type);
        if (sensor == null) { _ctx.fireCallback(cbId, "sensor_unavailable", null); return; }
        SensorEventListener listener = new SensorEventListener() {
            public void onSensorChanged(SensorEvent e) {
                _ctx.fireRawCallback(cbId, "[" + _sensorJson(kind, e) + "]");
            }
            public void onAccuracyChanged(Sensor s, int a) {}
        };
        _listeners.put(cbId, listener);
        _sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI);
    }

    private void _stopSensor(String cbId) {
        SensorEventListener l = _listeners.remove(cbId);
        if (l != null) _sm.unregisterListener(l);
    }

    private String _sensorJson(String kind, SensorEvent e) {
        try {
            JSONObject j = new JSONObject();
            switch (kind) {
                case "tilt":
                    j.put("alpha", e.values[0]);
                    j.put("beta",  e.values[1]);
                    j.put("gamma", e.values[2]);
                    break;
                case "accelerometer":
                case "gyroscope":
                    j.put("x", e.values[0]);
                    j.put("y", e.values[1]);
                    j.put("z", e.values[2]);
                    break;
                case "compass":
                    j.put("heading", e.values[0]);
                    j.put("x", e.values[0]);
                    j.put("y", e.values[1]);
                    j.put("z", e.values[2]);
                    break;
                case "proximity":
                    j.put("distance", e.values[0]);
                    j.put("near",     e.values[0] < 5);
                    break;
                case "light":
                    j.put("lux", e.values[0]);
                    break;
                case "pressure":
                    j.put("hPa", e.values[0]);
                    break;
                case "pedometer":
                    j.put("steps", (long) e.values[0]);
                    break;
            }
            return j.toString();
        } catch (Exception ex) { return "{}"; }
    }

    static String s(Object o) { return o != null ? o.toString() : ""; }
}
