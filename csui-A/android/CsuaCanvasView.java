package com.ctrlscript.csua;

import android.content.Context;
import android.graphics.*;
import android.view.View;

import java.util.*;

public class CsuaCanvasView extends View {

    private final List<DrawCommand> _commands = new ArrayList<>();
    private final Paint _paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CsuaCanvasView(Context ctx) { super(ctx); }

    public void handleCommand(String method, Object[] args) {
        _commands.add(new DrawCommand(method, args));
        post(this::invalidate);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (DrawCommand cmd : _commands) {
            try { _exec(canvas, cmd); } catch (Exception ignored) {}
        }
    }

    private void _exec(Canvas canvas, DrawCommand cmd) {
        switch (cmd.method) {
            case "clear":
                _commands.clear();
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                break;
            case "drawRect": {
                // args: [viewId, x, y, w, h, color]
                float x = _f(cmd.args[1]), y = _f(cmd.args[2]),
                      w = _f(cmd.args[3]), h = _f(cmd.args[4]);
                _paint.setColor(_color(s(cmd.args[5])));
                _paint.setStyle(Paint.Style.FILL);
                canvas.drawRect(x, y, x + w, y + h, _paint);
                break;
            }
            case "drawCircle": {
                float x = _f(cmd.args[1]), y = _f(cmd.args[2]), r = _f(cmd.args[3]);
                _paint.setColor(_color(s(cmd.args[4])));
                _paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(x, y, r, _paint);
                break;
            }
            case "drawLine": {
                float x1 = _f(cmd.args[1]), y1 = _f(cmd.args[2]),
                      x2 = _f(cmd.args[3]), y2 = _f(cmd.args[4]);
                _paint.setColor(_color(s(cmd.args[5])));
                _paint.setStrokeWidth(_f(cmd.args[6]));
                _paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(x1, y1, x2, y2, _paint);
                break;
            }
            case "drawText": {
                String text = s(cmd.args[1]);
                float x = _f(cmd.args[2]), y = _f(cmd.args[3]);
                _paint.setColor(_color(s(cmd.args[4])));
                _paint.setTextSize(_f(cmd.args[5]));
                _paint.setStyle(Paint.Style.FILL);
                canvas.drawText(text, x, y, _paint);
                break;
            }
        }
    }

    static class DrawCommand {
        final String method;
        final Object[] args;
        DrawCommand(String m, Object[] a) { method = m; args = a; }
    }

    static float _f(Object o) { try { return Float.parseFloat(o.toString()); } catch (Exception e) { return 0; } }
    static int _color(String s) { try { return Color.parseColor(s); } catch (Exception e) { return Color.WHITE; } }
    static String s(Object o) { return o != null ? o.toString() : ""; }
}
