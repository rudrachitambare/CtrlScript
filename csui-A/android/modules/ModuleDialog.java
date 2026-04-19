package com.ctrlscript.csua;

import android.app.*;
import android.content.*;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

public class ModuleDialog implements CsuaModule {

    private final Bootstrap _ctx;

    public ModuleDialog(Bootstrap ctx) { _ctx = ctx; }

    @Override
    public Object call(String method, Object... args) {
        String cbId = s(args[0]);
        switch (method) {
            case "alert":       _alert(cbId, s(args[1]), s(args[2]));        return null;
            case "confirm":     _confirm(cbId, s(args[1]), s(args[2]));      return null;
            case "prompt":      _prompt(cbId, s(args[1]), s(args[2]));       return null;
            case "datePicker":  _datePicker(cbId, s(args[1]));               return null;
            case "timePicker":  _timePicker(cbId, s(args[1]));               return null;
            case "colorPicker": _colorPicker(cbId, s(args[1]));              return null;
            case "filePicker":  _filePicker(cbId, s(args[1]));               return null;
            case "actionSheet": _actionSheet(cbId, s(args[1]), s(args[2]));  return null;
            case "toast":       _toast(s(args[0]), s(args[1]), s(args[2]));  return null;
        }
        return null;
    }

    private void _alert(String cbId, String title, String message) {
        _ctx._ui.post(() -> new AlertDialog.Builder(_ctx)
            .setTitle(title).setMessage(message)
            .setPositiveButton("OK", (d, w) -> _ctx.fireCallback(cbId, null, "true"))
            .setOnCancelListener(d -> _ctx.fireCallback(cbId, null, "false"))
            .show());
    }

    private void _confirm(String cbId, String title, String message) {
        _ctx._ui.post(() -> new AlertDialog.Builder(_ctx)
            .setTitle(title).setMessage(message)
            .setPositiveButton("OK",     (d, w) -> _ctx.fireCallback(cbId, null, "true"))
            .setNegativeButton("Cancel", (d, w) -> _ctx.fireCallback(cbId, null, "false"))
            .setOnCancelListener(d -> _ctx.fireCallback(cbId, null, "false"))
            .show());
    }

    private void _prompt(String cbId, String title, String hint) {
        _ctx._ui.post(() -> {
            EditText input = new EditText(_ctx);
            input.setHint(hint);
            new AlertDialog.Builder(_ctx)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("OK",     (d, w) -> _ctx.fireCallback(cbId, null, "\"" + input.getText().toString().replace("\"","\\\"") + "\""))
                .setNegativeButton("Cancel", (d, w) -> _ctx.fireCallback(cbId, null, "null"))
                .setOnCancelListener(d -> _ctx.fireCallback(cbId, null, "null"))
                .show();
        });
    }

    private void _datePicker(String cbId, String optsJson) {
        _ctx._ui.post(() -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            DatePickerDialog d = new DatePickerDialog(_ctx,
                (view, y, m, day) -> {
                    try {
                        _ctx.fireCallback(cbId, null, new JSONObject()
                            .put("year", y).put("month", m + 1).put("day", day)
                            .put("iso", String.format("%04d-%02d-%02d", y, m+1, day))
                            .toString());
                    } catch (Exception e) { _ctx.fireCallback(cbId, e.getMessage(), null); }
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH));
            d.setOnCancelListener(v -> _ctx.fireCallback(cbId, "cancelled", null));
            d.show();
        });
    }

    private void _timePicker(String cbId, String optsJson) {
        _ctx._ui.post(() -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            TimePickerDialog d = new TimePickerDialog(_ctx,
                (view, h, min) -> {
                    try {
                        _ctx.fireCallback(cbId, null, new JSONObject()
                            .put("hour", h).put("minute", min)
                            .put("formatted", String.format("%02d:%02d", h, min))
                            .toString());
                    } catch (Exception e) { _ctx.fireCallback(cbId, e.getMessage(), null); }
                },
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE), true);
            d.setOnCancelListener(v -> _ctx.fireCallback(cbId, "cancelled", null));
            d.show();
        });
    }

    private void _colorPicker(String cbId, String initial) {
        // Android has no built-in color picker — show a simple hex input dialog
        _ctx._ui.post(() -> {
            EditText input = new EditText(_ctx);
            input.setHint("#3b82f6");
            if (!initial.isEmpty()) input.setText(initial);
            new AlertDialog.Builder(_ctx)
                .setTitle("Pick a color")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> _ctx.fireCallback(cbId, null, "\"" + input.getText().toString() + "\""))
                .setNegativeButton("Cancel", (d, w) -> _ctx.fireCallback(cbId, "cancelled", null))
                .show();
        });
    }

    private void _filePicker(String cbId, String optsJson) {
        _ctx._ui.post(() -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            try {
                JSONObject opts = new JSONObject(optsJson);
                intent.setType(opts.optString("type", "*/*"));
            } catch (Exception ignored) {}
            // Result handled via onActivityResult in Bootstrap
            _ctx.startActivityForResult(Intent.createChooser(intent, "Select file"), 9020);
            // Store cbId for result
            _ctx._views.put(-9020, null); // placeholder to pass cbId
            _fileCbId = cbId;
        });
    }
    String _fileCbId;

    private void _actionSheet(String cbId, String title, String itemsJson) {
        _ctx._ui.post(() -> {
            try {
                JSONArray items = new JSONArray(itemsJson);
                String[] labels = new String[items.length()];
                for (int i = 0; i < items.length(); i++) labels[i] = items.getString(i);
                new AlertDialog.Builder(_ctx)
                    .setTitle(title.isEmpty() ? null : title)
                    .setItems(labels, (d, which) -> _ctx.fireCallback(cbId, null, String.valueOf(which)))
                    .setOnCancelListener(d -> _ctx.fireCallback(cbId, "cancelled", null))
                    .show();
            } catch (Exception e) { _ctx.fireCallback(cbId, e.getMessage(), null); }
        });
    }

    private void _toast(String message, String duration, String gravity) {
        _ctx._ui.post(() -> {
            Toast t = Toast.makeText(_ctx, message,
                "long".equals(duration) ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
            if ("top".equals(gravity)) t.setGravity(android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL, 0, 100);
            t.show();
        });
    }

    static String s(Object o) { return o != null ? o.toString() : ""; }
}
