package com.ctrlscript.csua;

public class ModuleApp implements CsuaModule {

    private final Bootstrap _ctx;

    public ModuleApp(Bootstrap ctx) { _ctx = ctx; }

    @Override
    public Object call(String method, Object... args) {
        switch (method) {
            case "on": _on(s(args[0]), s(args[1])); return null;
            case "exit": _ctx._ui.post(() -> { _ctx.finish(); android.os.Process.killProcess(android.os.Process.myPid()); }); return null;
            case "version": return _version();
            case "package": return _ctx.getPackageName();
            case "clearRoot": _ctx._ui.post(() -> _ctx._root.removeAllViews()); return null;
            case "requestFrame": {
                String cbName = s(args[0]);
                _ctx._ui.post(() -> _ctx.runOnJSThread(() ->
                    QuickJS.eval(_ctx._jsCtx(), "if(typeof " + cbName + "==='function'){" + cbName + "()}", "<frame>")));
                return null;
            }
        }
        return null;
    }

    private void _on(String event, String cbId) {
        // Callback IDs are registered in JS — Bootstrap fires them via lifecycle methods
        // This is a no-op here; Bootstrap.java already fires the global JS function names directly
    }

    private String _version() {
        try { return _ctx.getPackageManager().getPackageInfo(_ctx.getPackageName(), 0).versionName; }
        catch (Exception e) { return "1.0.0"; }
    }

    static String s(Object o) { return o != null ? o.toString() : ""; }
}
