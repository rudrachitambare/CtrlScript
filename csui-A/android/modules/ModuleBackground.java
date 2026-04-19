package com.ctrlscript.csua;

import androidx.work.*;

import java.util.concurrent.TimeUnit;

public class ModuleBackground implements CsuaModule {

    private final Bootstrap _ctx;

    public ModuleBackground(Bootstrap ctx) { _ctx = ctx; }

    @Override
    public Object call(String method, Object... args) {
        switch (method) {
            case "oneShot":  return _oneShot(s(args[0]));
            case "periodic": return _periodic(s(args[0]), s(args[1]));
            case "cancel":   _cancel(s(args[0])); return null;
        }
        return null;
    }

    private String _oneShot(String cbId) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(CsuaWorker.class)
            .setInputData(new Data.Builder().putString("cbId", cbId).build())
            .build();
        WorkManager.getInstance(_ctx).enqueue(req);
        return req.getId().toString();
    }

    private String _periodic(String cbId, String interval) {
        long minutes = _parseInterval(interval);
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(CsuaWorker.class, minutes, TimeUnit.MINUTES)
            .setInputData(new Data.Builder().putString("cbId", cbId).build())
            .build();
        WorkManager.getInstance(_ctx).enqueue(req);
        return req.getId().toString();
    }

    private void _cancel(String id) {
        try { WorkManager.getInstance(_ctx).cancelWorkById(java.util.UUID.fromString(id)); }
        catch (Exception ignored) {}
    }

    private long _parseInterval(String s) {
        // "15m" → 15, "1h" → 60, "30s" → minimum 15m (WorkManager constraint)
        if (s.endsWith("h")) try { return Long.parseLong(s.replace("h","")) * 60; } catch (Exception e) {}
        if (s.endsWith("m")) try { return Math.max(15, Long.parseLong(s.replace("m",""))); } catch (Exception e) {}
        return 15;
    }

    // WorkManager Worker — fires JS callback when task runs
    public static class CsuaWorker extends Worker {
        public CsuaWorker(android.content.Context ctx, WorkerParameters params) { super(ctx, params); }

        public Result doWork() {
            String cbId = getInputData().getString("cbId");
            // CsuaWorker runs in background process — for now, just log
            // Full implementation: send broadcast back to Bootstrap activity
            android.util.Log.d("CSUA", "Background task: " + cbId);
            return Result.success();
        }
    }

    static String s(Object o) { return o != null ? o.toString() : ""; }
}
