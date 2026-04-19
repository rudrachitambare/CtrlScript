package com.ctrlscript.csua;

import android.app.*;
import android.content.*;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONObject;

public class ModuleNotifications implements CsuaModule {

    private static final String CHANNEL_ID   = "csua_default";
    private static final String CHANNEL_NAME = "App Notifications";
    private final Bootstrap _ctx;
    private int _notifId = 1000;

    public ModuleNotifications(Bootstrap ctx) {
        _ctx = ctx;
        _createChannel();
    }

    @Override
    public Object call(String method, Object... args) {
        switch (method) {
            case "send": _send(s(args[0]), s(args[1])); return null;
        }
        return null;
    }

    private void _send(String cbId, String optsJson) {
        new Thread(() -> {
            try {
                JSONObject opts   = new JSONObject(optsJson);
                String title      = opts.optString("title", "");
                String body       = opts.optString("body",  "");
                int    badge      = opts.optInt("badge", 0);
                String smallIcon  = opts.optString("icon", "");

                int iconRes = android.R.drawable.ic_dialog_info;
                if (!smallIcon.isEmpty()) {
                    int r = _ctx.getResources().getIdentifier(smallIcon, "drawable", _ctx.getPackageName());
                    if (r != 0) iconRes = r;
                }

                NotificationCompat.Builder builder = new NotificationCompat.Builder(_ctx, CHANNEL_ID)
                    .setSmallIcon(iconRes)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);

                // Tap to open app
                Intent intent = _ctx.getPackageManager().getLaunchIntentForPackage(_ctx.getPackageName());
                if (intent != null) {
                    PendingIntent pi = PendingIntent.getActivity(_ctx, 0, intent,
                        Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
                    builder.setContentIntent(pi);
                }

                NotificationManagerCompat.from(_ctx).notify(_notifId++, builder.build());
                _ctx.fireCallback(cbId, null, "true");
            } catch (Exception e) { _ctx.fireCallback(cbId, e.getMessage(), null); }
        }, "csua-notif").start();
    }

    private void _createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager nm = _ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    static String s(Object o) { return o != null ? o.toString() : ""; }
}
