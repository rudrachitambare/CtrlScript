package com.ctrlscript.csua;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ModuleCamera implements CsuaModule {

    private final Bootstrap _ctx;
    private String _pendingCbId;
    private static final int REQ_SNAP   = 9010;
    private static final int REQ_RECORD = 9011;
    private static final int REQ_PICK   = 9012;
    private static final int REQ_PICK_M = 9013;

    private File _lastFile;

    public ModuleCamera(Bootstrap ctx) { _ctx = ctx; }

    @Override
    public Object call(String method, Object... args) {
        switch (method) {
            case "snap":         _snap(s(args[0]));          return null;
            case "record":       _record(s(args[0]), _int(args[1])); return null;
            case "pick":         _pick(s(args[0]));          return null;
            case "pickMultiple": _pickMultiple(s(args[0]));  return null;
            case "stream":       /* live preview — future */  return null;
        }
        return null;
    }

    private void _snap(String cbId) {
        _pendingCbId = cbId;
        _lastFile = _newFile("IMG", ".jpg");
        Uri uri = FileProvider.getUriForFile(_ctx, _ctx.getPackageName() + ".provider", _lastFile);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        _ctx._ui.post(() -> _ctx.startActivityForResult(intent, REQ_SNAP));
    }

    private void _record(String cbId, int maxMs) {
        _pendingCbId = cbId;
        _lastFile = _newFile("VID", ".mp4");
        Uri uri = FileProvider.getUriForFile(_ctx, _ctx.getPackageName() + ".provider", _lastFile);
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, maxMs / 1000);
        _ctx._ui.post(() -> _ctx.startActivityForResult(intent, REQ_RECORD));
    }

    private void _pick(String cbId) {
        _pendingCbId = cbId;
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        _ctx._ui.post(() -> _ctx.startActivityForResult(intent, REQ_PICK));
    }

    private void _pickMultiple(String cbId) {
        _pendingCbId = cbId;
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        _ctx._ui.post(() -> _ctx.startActivityForResult(intent, REQ_PICK_M));
    }

    // Called by Bootstrap.onActivityResult
    public void onResult(int reqCode, int resultCode, Intent data) {
        if (_pendingCbId == null) return;
        if (resultCode != android.app.Activity.RESULT_OK) {
            _ctx.fireCallback(_pendingCbId, "cancelled", null);
            _pendingCbId = null;
            return;
        }
        String path = null;
        if (reqCode == REQ_SNAP || reqCode == REQ_RECORD) {
            path = _lastFile != null ? _lastFile.getAbsolutePath() : null;
        } else if (reqCode == REQ_PICK && data != null) {
            path = _uriToPath(data.getData());
        } else if (reqCode == REQ_PICK_M && data != null) {
            org.json.JSONArray arr = new org.json.JSONArray();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++)
                    arr.put(_uriToPath(data.getClipData().getItemAt(i).getUri()));
            } else if (data.getData() != null) {
                arr.put(_uriToPath(data.getData()));
            }
            _ctx.fireCallback(_pendingCbId, null, arr.toString());
            _pendingCbId = null;
            return;
        }
        _ctx.fireCallback(_pendingCbId, path == null ? "no_result" : null,
                          path != null ? "\"" + path + "\"" : null);
        _pendingCbId = null;
    }

    private String _uriToPath(Uri uri) {
        if (uri == null) return null;
        String[] proj = { MediaStore.Images.Media.DATA };
        android.database.Cursor cursor = _ctx.getContentResolver().query(uri, proj, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
            cursor.close();
            return path;
        }
        return uri.getPath();
    }

    private File _newFile(String prefix, String ext) {
        String ts   = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir    = _ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir == null) dir = _ctx.getFilesDir();
        return new File(dir, prefix + "_" + ts + ext);
    }

    static String s(Object o) { return o != null ? o.toString() : ""; }
    static int _int(Object o) { try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 10000; } }
}
