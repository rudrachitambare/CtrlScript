package com.ctrlscript.csua;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;

import org.json.JSONArray;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class ModuleStorage implements CsuaModule {

    private final Bootstrap _ctx;
    private SharedPreferences _prefs;
    private SQLiteDatabase _db;

    public ModuleStorage(Bootstrap ctx) {
        _ctx = ctx;
        _prefs = ctx.getSharedPreferences("csua_store", android.content.Context.MODE_PRIVATE);
    }

    @Override
    public Object call(String method, Object... args) {
        switch (method) {
            // ── SharedPreferences ──
            case "set":    _prefs.edit().putString(s(args[0]), s(args[1])).apply(); return null;
            case "get":    return _prefs.getString(s(args[0]), null);
            case "remove": _prefs.edit().remove(s(args[0])).apply(); return null;
            case "clear":  _prefs.edit().clear().apply(); return null;

            // ── SQLite ──
            case "run":   return _dbRun(s(args[0]),   args.length > 1 ? s(args[1]) : "[]");
            case "query": return _dbQuery(s(args[0]), args.length > 1 ? s(args[1]) : "[]");
            case "beginTransaction": _db().beginTransaction(); return null;
            case "commit":           _db().setTransactionSuccessful(); _db().endTransaction(); return null;
            case "rollback":         _db().endTransaction(); return null;

            // ── Files ──
            case "read":   return _read(s(args[0]));
            case "write":  _write(s(args[0]), s(args[1]), false); return null;
            case "append": _write(s(args[0]), s(args[1]), true);  return null;
            case "delete": new File(_resolve(s(args[0]))).delete(); return null;
            case "exists": return String.valueOf(new File(_resolve(s(args[0]))).exists());
            case "list":   return _list(s(args[0]));
            case "mkdir":  new File(_resolve(s(args[0]))).mkdirs(); return null;
        }
        return null;
    }

    // ── SQLite helpers ───────────────────────────────────

    private SQLiteDatabase _db() {
        if (_db == null) {
            _db = new SQLiteOpenHelper(_ctx, "csua.db", null, 1) {
                public void onCreate(SQLiteDatabase db) {}
                public void onUpgrade(SQLiteDatabase db, int o, int n) {}
            }.getWritableDatabase();
        }
        return _db;
    }

    private Object _dbRun(String sql, String paramsJson) {
        try {
            JSONArray params = new JSONArray(paramsJson);
            String[] bindings = new String[params.length()];
            for (int i = 0; i < params.length(); i++) bindings[i] = params.getString(i);
            _db().execSQL(sql, bindings);
        } catch (Exception e) { android.util.Log.e("CSUA", "db.run error", e); }
        return null;
    }

    private String _dbQuery(String sql, String paramsJson) {
        try {
            JSONArray params = new JSONArray(paramsJson);
            String[] bindings = new String[params.length()];
            for (int i = 0; i < params.length(); i++) bindings[i] = params.getString(i);
            Cursor c = _db().rawQuery(sql, bindings);
            JSONArray rows = new JSONArray();
            while (c.moveToNext()) {
                org.json.JSONObject row = new org.json.JSONObject();
                for (int i = 0; i < c.getColumnCount(); i++)
                    row.put(c.getColumnName(i), c.getString(i));
                rows.put(row);
            }
            c.close();
            return rows.toString();
        } catch (Exception e) { android.util.Log.e("CSUA", "db.query error", e); return "[]"; }
    }

    // ── File helpers ─────────────────────────────────────

    private String _resolve(String path) {
        if (path.startsWith("/")) return path;
        return _ctx.getFilesDir().getAbsolutePath() + "/" + path;
    }

    private String _read(String path) {
        try {
            File f = new File(_resolve(path));
            if (!f.exists()) return null;
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            fis.read(buf);
            fis.close();
            return new String(buf, StandardCharsets.UTF_8);
        } catch (Exception e) { return null; }
    }

    private void _write(String path, String data, boolean append) {
        try {
            File f = new File(_resolve(path));
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(f, append);
            fos.write(data.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Exception e) { android.util.Log.e("CSUA", "files.write error", e); }
    }

    private String _list(String dir) {
        try {
            File f = new File(_resolve(dir));
            String[] names = f.list();
            if (names == null) return "[]";
            return new JSONArray(java.util.Arrays.asList(names)).toString();
        } catch (Exception e) { return "[]"; }
    }

    static String s(Object o) { return o != null ? o.toString() : ""; }
}
