package com.ctrlscript.csua;

import android.bluetooth.*;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.*;

public class ModuleBluetooth implements CsuaModule {

    private final Bootstrap _ctx;
    private BluetoothAdapter _adapter;
    private final Map<String, BluetoothSocket> _sockets = new HashMap<>();
    private final Map<String, Thread> _readThreads = new HashMap<>();

    public ModuleBluetooth(Bootstrap ctx) {
        _ctx = ctx;
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        _adapter = bm != null ? bm.getAdapter() : null;
    }

    @Override
    public Object call(String method, Object... args) {
        switch (method) {
            case "scan":       _scan(s(args[0]), _int(args[1]));           return null;
            case "connect":    _connect(s(args[0]), s(args[1]));           return null;
            case "disconnect": _disconnect(s(args[1]));                    return null;
            case "send":       _send(s(args[1]), s(args[2]));              return null;
            case "onData":     _onData(s(args[0]), s(args[2]));            return null;
        }
        return null;
    }

    private void _scan(String cbId, int timeoutMs) {
        if (_adapter == null || !_adapter.isEnabled()) {
            _ctx.fireCallback(cbId, "bluetooth_disabled", null); return;
        }
        new Thread(() -> {
            try {
                Set<BluetoothDevice> bonded = _adapter.getBondedDevices();
                JSONArray result = new JSONArray();
                for (BluetoothDevice d : bonded) {
                    result.put(new JSONObject()
                        .put("id",   d.getAddress())
                        .put("name", d.getName() != null ? d.getName() : "Unknown")
                        .put("bonded", true));
                }
                // Discovery requires a BroadcastReceiver — simplified: return bonded devices
                Thread.sleep(Math.min(timeoutMs, 2000));
                _ctx.fireCallback(cbId, null, result.toString());
            } catch (Exception e) { _ctx.fireCallback(cbId, e.getMessage(), null); }
        }, "csua-bt-scan").start();
    }

    private void _connect(String cbId, String address) {
        new Thread(() -> {
            try {
                BluetoothDevice device = _adapter.getRemoteDevice(address);
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(
                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                socket.connect();
                _sockets.put(address, socket);
                _ctx.fireCallback(cbId, null, "\"" + address + "\"");
            } catch (Exception e) { _ctx.fireCallback(cbId, e.getMessage(), null); }
        }, "csua-bt-connect").start();
    }

    private void _disconnect(String address) {
        BluetoothSocket s = _sockets.remove(address);
        if (s != null) try { s.close(); } catch (Exception ignored) {}
        Thread t = _readThreads.remove(address);
        if (t != null) t.interrupt();
    }

    private void _send(String address, String data) {
        BluetoothSocket s = _sockets.get(address);
        if (s == null) return;
        new Thread(() -> {
            try { s.getOutputStream().write(data.getBytes()); }
            catch (Exception e) { android.util.Log.e("CSUA", "BT send error", e); }
        }, "csua-bt-send").start();
    }

    private void _onData(String cbId, String address) {
        BluetoothSocket s = _sockets.get(address);
        if (s == null) return;
        Thread t = new Thread(() -> {
            try {
                InputStream is = s.getInputStream();
                byte[] buf = new byte[1024];
                int len;
                while ((len = is.read(buf)) > 0) {
                    String data = new String(buf, 0, len);
                    _ctx.fireRawCallback(cbId, "[\"" + data.replace("\"","\\\"") + "\"]");
                }
            } catch (Exception e) { android.util.Log.e("CSUA", "BT read error", e); }
        }, "csua-bt-read");
        t.start();
        _readThreads.put(address, t);
    }

    static String s(Object o) { return o != null ? o.toString() : ""; }
    static int _int(Object o) { try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 5000; } }
}
