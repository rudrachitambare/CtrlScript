package com.ctrlscript.csua;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.json.*;
import java.util.*;
import java.util.regex.*;

/**
 * Canvas-backed SVG renderer.
 * Receives a JSON blob from JS (via bridge.call('views','render',...))
 * and draws all shapes using Android Canvas primitives.
 * No WebView — pure Canvas.
 */
public class SvgCanvasView extends View {

    private JSONArray _shapes   = new JSONArray();
    private int       _bgColor  = Color.TRANSPARENT;
    private float     _vpW      = 300f;
    private float     _vpH      = 200f;

    public SvgCanvasView(Context ctx) {
        super(ctx);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setShapes(String json) {
        try {
            JSONObject data = new JSONObject(json);
            _shapes  = data.optJSONArray("shapes");
            if (_shapes == null) _shapes = new JSONArray();
            _bgColor = _color(data.optString("bg", "transparent"));
            _vpW     = (float) data.optDouble("w", 300);
            _vpH     = (float) data.optDouble("h", 200);
        } catch (Exception ignored) {
            _shapes = new JSONArray();
        }
        invalidate();
    }

    // ── Draw ────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (_bgColor != Color.TRANSPARENT) canvas.drawColor(_bgColor);
        float sx = _vpW > 0 ? getWidth()  / _vpW : 1f;
        float sy = _vpH > 0 ? getHeight() / _vpH : 1f;
        for (int i = 0; i < _shapes.length(); i++) {
            try { _shape(canvas, _shapes.getJSONObject(i), sx, sy); }
            catch (Exception ignored) {}
        }
    }

    private void _shape(Canvas canvas, JSONObject s, float sx, float sy) {
        try {
            switch (s.optString("type", "")) {
                case "rect":     _rect(canvas, s, sx, sy);             break;
                case "circle":   _circle(canvas, s, sx, sy);           break;
                case "ellipse":  _ellipse(canvas, s, sx, sy);          break;
                case "line":     _line(canvas, s, sx, sy);             break;
                case "path":     _path(canvas, s, sx, sy);             break;
                case "text":     _text(canvas, s, sx, sy);             break;
                case "polygon":  _poly(canvas, s, sx, sy, true);       break;
                case "polyline": _poly(canvas, s, sx, sy, false);      break;
                case "g":        _group(canvas, s, sx, sy);            break;
            }
        } catch (Exception ignored) {}
    }

    private void _rect(Canvas canvas, JSONObject s, float sx, float sy) {
        float x  = _f(s, "x",      0) * sx;
        float y  = _f(s, "y",      0) * sy;
        float w  = _f(s, "width",  0) * sx;
        float h  = _f(s, "height", 0) * sy;
        float rx = _f(s, "rx",     0) * sx;
        float ry = _f(s, "ry",    rx) * sy;
        RectF r  = new RectF(x, y, x + w, y + h);
        float op = _f(s, "opacity", 1f);

        String fill = s.optString("fill", "");
        if (!fill.isEmpty() && !"none".equals(fill)) {
            Paint p = _paint(fill, Paint.Style.FILL, 0, op);
            canvas.drawRoundRect(r, rx, ry, p);
        }
        String stroke = s.optString("stroke", "");
        if (!stroke.isEmpty() && !"none".equals(stroke)) {
            float sw = _f(s, "strokeWidth", 1f) * Math.min(sx, sy);
            canvas.drawRoundRect(r, rx, ry, _paint(stroke, Paint.Style.STROKE, sw, op));
        }
    }

    private void _circle(Canvas canvas, JSONObject s, float sx, float sy) {
        float cx = _f(s, "cx", 0) * sx;
        float cy = _f(s, "cy", 0) * sy;
        float r  = _f(s, "r",  0) * Math.min(sx, sy);
        float op = _f(s, "opacity", 1f);

        String fill = s.optString("fill", "");
        if (!fill.isEmpty() && !"none".equals(fill))
            canvas.drawCircle(cx, cy, r, _paint(fill, Paint.Style.FILL, 0, op));
        String stroke = s.optString("stroke", "");
        if (!stroke.isEmpty() && !"none".equals(stroke)) {
            float sw = _f(s, "strokeWidth", 1f) * Math.min(sx, sy);
            canvas.drawCircle(cx, cy, r, _paint(stroke, Paint.Style.STROKE, sw, op));
        }
    }

    private void _ellipse(Canvas canvas, JSONObject s, float sx, float sy) {
        float cx = _f(s, "cx", 0) * sx;
        float cy = _f(s, "cy", 0) * sy;
        float rx = _f(s, "rx", 0) * sx;
        float ry = _f(s, "ry", 0) * sy;
        RectF oval = new RectF(cx - rx, cy - ry, cx + rx, cy + ry);
        float op = _f(s, "opacity", 1f);

        String fill = s.optString("fill", "");
        if (!fill.isEmpty() && !"none".equals(fill))
            canvas.drawOval(oval, _paint(fill, Paint.Style.FILL, 0, op));
        String stroke = s.optString("stroke", "");
        if (!stroke.isEmpty() && !"none".equals(stroke)) {
            float sw = _f(s, "strokeWidth", 1f) * Math.min(sx, sy);
            canvas.drawOval(oval, _paint(stroke, Paint.Style.STROKE, sw, op));
        }
    }

    private void _line(Canvas canvas, JSONObject s, float sx, float sy) {
        float x1 = _f(s, "x1", 0) * sx;
        float y1 = _f(s, "y1", 0) * sy;
        float x2 = _f(s, "x2", 0) * sx;
        float y2 = _f(s, "y2", 0) * sy;
        String stroke = s.optString("stroke", "#000000");
        if ("none".equals(stroke)) return;
        float sw = _f(s, "strokeWidth", 1f) * Math.min(sx, sy);
        float op = _f(s, "opacity", 1f);
        canvas.drawLine(x1, y1, x2, y2, _paint(stroke, Paint.Style.STROKE, sw, op));
    }

    private void _poly(Canvas canvas, JSONObject s, float sx, float sy, boolean close) {
        String pts = s.optString("points", "").trim();
        if (pts.isEmpty()) return;
        String[] tok = pts.split("[\\s,]+");
        if (tok.length < 4) return;
        Path path = new Path();
        try {
            path.moveTo(Float.parseFloat(tok[0]) * sx, Float.parseFloat(tok[1]) * sy);
            for (int i = 2; i + 1 < tok.length; i += 2)
                path.lineTo(Float.parseFloat(tok[i]) * sx, Float.parseFloat(tok[i + 1]) * sy);
        } catch (NumberFormatException ignored) { return; }
        if (close) path.close();
        float op = _f(s, "opacity", 1f);

        String fill = s.optString("fill", "");
        if (!fill.isEmpty() && !"none".equals(fill))
            canvas.drawPath(path, _paint(fill, Paint.Style.FILL, 0, op));
        String stroke = s.optString("stroke", "");
        if (!stroke.isEmpty() && !"none".equals(stroke)) {
            float sw = _f(s, "strokeWidth", 1f) * Math.min(sx, sy);
            canvas.drawPath(path, _paint(stroke, Paint.Style.STROKE, sw, op));
        }
    }

    private void _path(Canvas canvas, JSONObject s, float sx, float sy) {
        String d = s.optString("d", "");
        if (d.isEmpty()) return;
        Path path = _parsePath(d, sx, sy);
        float op = _f(s, "opacity", 1f);

        String fill = s.optString("fill", "");
        if (!fill.isEmpty() && !"none".equals(fill))
            canvas.drawPath(path, _paint(fill, Paint.Style.FILL, 0, op));
        String stroke = s.optString("stroke", "");
        if (!stroke.isEmpty() && !"none".equals(stroke)) {
            float sw = _f(s, "strokeWidth", 1f) * Math.min(sx, sy);
            canvas.drawPath(path, _paint(stroke, Paint.Style.STROKE, sw, op));
        }
    }

    private void _text(Canvas canvas, JSONObject s, float sx, float sy) {
        String text = s.optString("text", "");
        if (text.isEmpty()) return;
        float x  = _f(s, "x", 0) * sx;
        float y  = _f(s, "y", 0) * sy;
        float fs = _f(s, "fontSize", 16f) * Math.min(sx, sy);
        float op = _f(s, "opacity", 1f);
        String fill   = s.optString("fill", "#000000");
        String anchor = s.optString("textAnchor", "start");

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(_color(fill));
        p.setAlpha((int)(p.getAlpha() * op));
        p.setTextSize(fs);
        if (s.optBoolean("bold"))   p.setTypeface(Typeface.DEFAULT_BOLD);
        if (s.optBoolean("italic")) p.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
        switch (anchor) {
            case "middle": p.setTextAlign(Paint.Align.CENTER); break;
            case "end":    p.setTextAlign(Paint.Align.RIGHT);  break;
            default:       p.setTextAlign(Paint.Align.LEFT);   break;
        }
        canvas.drawText(text, x, y, p);
    }

    private void _group(Canvas canvas, JSONObject s, float sx, float sy) {
        JSONArray children = s.optJSONArray("children");
        if (children == null) return;
        int saved = canvas.save();
        String transform = s.optString("transform", "");
        if (!transform.isEmpty()) _applyTransform(canvas, transform, sx, sy);
        for (int i = 0; i < children.length(); i++) {
            try { _shape(canvas, children.getJSONObject(i), sx, sy); }
            catch (Exception ignored) {}
        }
        canvas.restoreToCount(saved);
    }

    // ── SVG path parser (M L H V C Q A Z and lowercase) ──

    private Path _parsePath(String d, float sx, float sy) {
        Path path = new Path();
        Pattern pat = Pattern.compile("[MmLlHhVvCcQqAaZz]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");
        Matcher m = pat.matcher(d);
        List<String> tokens = new ArrayList<>();
        while (m.find()) tokens.add(m.group());

        float cx = 0, cy = 0, startX = 0, startY = 0;
        char cmd = 'M';
        int i = 0;

        while (i < tokens.size()) {
            String t = tokens.get(i);
            if (t.matches("[MmLlHhVvCcQqAaZz]")) { cmd = t.charAt(0); i++; }
            if (i >= tokens.size() && Character.toUpperCase(cmd) != 'Z') break;
            boolean rel = Character.isLowerCase(cmd);
            try {
                switch (Character.toUpperCase(cmd)) {
                    case 'M': {
                        float x = _tok(tokens, i++) * sx, y = _tok(tokens, i++) * sy;
                        if (rel) { x += cx; y += cy; }
                        path.moveTo(x, y); cx = x; cy = y; startX = x; startY = y;
                        cmd = rel ? 'l' : 'L'; break;
                    }
                    case 'L': {
                        float x = _tok(tokens, i++) * sx, y = _tok(tokens, i++) * sy;
                        if (rel) { x += cx; y += cy; }
                        path.lineTo(x, y); cx = x; cy = y; break;
                    }
                    case 'H': {
                        float x = _tok(tokens, i++) * sx;
                        if (rel) x += cx;
                        path.lineTo(x, cy); cx = x; break;
                    }
                    case 'V': {
                        float y = _tok(tokens, i++) * sy;
                        if (rel) y += cy;
                        path.lineTo(cx, y); cy = y; break;
                    }
                    case 'C': {
                        float x1 = _tok(tokens,i++)*sx, y1 = _tok(tokens,i++)*sy;
                        float x2 = _tok(tokens,i++)*sx, y2 = _tok(tokens,i++)*sy;
                        float x  = _tok(tokens,i++)*sx, y  = _tok(tokens,i++)*sy;
                        if (rel) { x1+=cx; y1+=cy; x2+=cx; y2+=cy; x+=cx; y+=cy; }
                        path.cubicTo(x1,y1,x2,y2,x,y); cx=x; cy=y; break;
                    }
                    case 'Q': {
                        float x1 = _tok(tokens,i++)*sx, y1 = _tok(tokens,i++)*sy;
                        float x  = _tok(tokens,i++)*sx, y  = _tok(tokens,i++)*sy;
                        if (rel) { x1+=cx; y1+=cy; x+=cx; y+=cy; }
                        path.quadTo(x1,y1,x,y); cx=x; cy=y; break;
                    }
                    case 'A': {
                        float rx   = _tok(tokens,i++)*sx, ry = _tok(tokens,i++)*sy;
                        float rot  = _tok(tokens,i++);
                        boolean lg = _tok(tokens,i++) != 0;
                        boolean sw = _tok(tokens,i++) != 0;
                        float x    = _tok(tokens,i++)*sx, y = _tok(tokens,i++)*sy;
                        if (rel) { x+=cx; y+=cy; }
                        _arcTo(path, cx, cy, x, y, rx, ry, rot, lg, sw);
                        cx=x; cy=y; break;
                    }
                    case 'Z': {
                        path.close(); cx=startX; cy=startY; i++; break;
                    }
                    default: i++; break;
                }
            } catch (Exception ignored) { i++; }
        }
        return path;
    }

    private void _arcTo(Path path, float x1, float y1, float x2, float y2,
                        float rx, float ry, float phi, boolean largeArc, boolean sweep) {
        if (x1 == x2 && y1 == y2) return;
        if (rx == 0 || ry == 0) { path.lineTo(x2, y2); return; }
        double pRad = Math.toRadians(phi);
        double cp = Math.cos(pRad), sp = Math.sin(pRad);
        double dx = (x1-x2)/2, dy = (y1-y2)/2;
        double x1p = cp*dx + sp*dy, y1p = -sp*dx + cp*dy;
        double rx2 = rx*rx, ry2 = ry*ry, x1p2 = x1p*x1p, y1p2 = y1p*y1p;
        double lam = x1p2/rx2 + y1p2/ry2;
        if (lam > 1) { double s = Math.sqrt(lam); rx *= s; ry *= s; rx2 = rx*rx; ry2 = ry*ry; }
        double num = rx2*ry2 - rx2*y1p2 - ry2*x1p2;
        double den = rx2*y1p2 + ry2*x1p2;
        double sq  = (num/den < 0) ? 0 : Math.sqrt(num/den);
        if (largeArc == sweep) sq = -sq;
        double cxp = sq*rx*y1p/ry, cyp = -sq*ry*x1p/rx;
        double cX = cp*cxp - sp*cyp + (x1+x2)/2;
        double cY = sp*cxp + cp*cyp + (y1+y2)/2;
        double ux = (x1p-cxp)/rx, uy = (y1p-cyp)/ry;
        double vx = (-x1p-cxp)/rx, vy = (-y1p-cyp)/ry;
        double start = Math.toDegrees(Math.atan2(uy, ux));
        double dth   = Math.toDegrees(Math.atan2(vy, vx) - Math.atan2(uy, ux));
        if (!sweep && dth > 0) dth -= 360;
        if (sweep  && dth < 0) dth += 360;
        RectF oval = new RectF((float)(cX-rx),(float)(cY-ry),(float)(cX+rx),(float)(cY+ry));
        path.arcTo(oval, (float)start, (float)dth);
    }

    // ── Transform parser ────────────────────────────────

    private void _applyTransform(Canvas canvas, String transform, float sx, float sy) {
        Pattern p = Pattern.compile("(translate|rotate|scale|matrix)\\(([^)]+)\\)");
        Matcher m = p.matcher(transform);
        while (m.find()) {
            String fn = m.group(1);
            String[] a = m.group(2).trim().split("[\\s,]+");
            try {
                switch (fn) {
                    case "translate":
                        canvas.translate(_f(a,0)*sx, a.length>1 ? _f(a,1)*sy : 0); break;
                    case "rotate":
                        if (a.length >= 3) canvas.rotate(_f(a,0), _f(a,1)*sx, _f(a,2)*sy);
                        else canvas.rotate(_f(a,0)); break;
                    case "scale":
                        canvas.scale(_f(a,0), a.length>1 ? _f(a,1) : _f(a,0)); break;
                    case "matrix": {
                        Matrix mat = new Matrix();
                        mat.setValues(new float[]{
                            _f(a,0), _f(a,2), _f(a,4)*sx,
                            _f(a,1), _f(a,3), _f(a,5)*sy,
                            0,       0,        1
                        });
                        canvas.concat(mat);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    // ── Helpers ─────────────────────────────────────────

    private Paint _paint(String color, Paint.Style style, float sw, float opacity) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(_color(color));
        p.setStyle(style);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        if (style == Paint.Style.STROKE) p.setStrokeWidth(sw);
        if (opacity < 1f) p.setAlpha(Math.max(0, Math.min(255, (int)(p.getAlpha() * opacity))));
        return p;
    }

    private int _color(String s) {
        if (s == null || s.isEmpty() || "transparent".equals(s) || "none".equals(s))
            return Color.TRANSPARENT;
        try { return Color.parseColor(s); } catch (Exception e) { return Color.TRANSPARENT; }
    }

    private float _f(JSONObject s, String key, float def) {
        return (float) s.optDouble(key, def);
    }

    private float _tok(List<String> t, int i) {
        return Float.parseFloat(t.get(i));
    }

    private float _f(String[] a, int i) {
        return i < a.length ? Float.parseFloat(a[i]) : 0f;
    }
}
