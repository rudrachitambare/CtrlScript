package com.ctrlscript.csua;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ModuleViews implements CsuaModule {

    private final Bootstrap _ctx;
    public ModuleViews(Bootstrap ctx) { _ctx = ctx; }

    // ── Factory ──────────────────────────────────────────

    public static View create(Bootstrap ctx, String type) {
        switch (type) {
            case "LinearLayout":  return new LinearLayout(ctx);
            case "ScrollView":    { ScrollView s = new ScrollView(ctx); LinearLayout inner = new LinearLayout(ctx); inner.setOrientation(LinearLayout.VERTICAL); s.addView(inner); return s; }
            case "TextView":      return new TextView(ctx);
            case "Button":        return new android.widget.Button(ctx);
            case "EditText":      return new EditText(ctx);
            case "ImageView":     return new ImageView(ctx);
            case "CanvasView":    return new CsuaCanvasView(ctx);
            case "ShapeView":     return new ShapeView(ctx);
            case "SafeAreaView":  return new LinearLayout(ctx);  // insets handled via WindowInsets
            case "RecyclerView":  { RecyclerView rv = new RecyclerView(ctx); rv.setLayoutManager(new LinearLayoutManager(ctx)); return rv; }
            case "SvgView":       return new SvgCanvasView(ctx);
            default:              return new LinearLayout(ctx);
        }
    }

    // ── Prop setter ──────────────────────────────────────

    public static void setProp(Bootstrap ctx, View v, String key, Object val) {
        String s = val != null ? val.toString() : "";
        switch (key) {
            // layout
            case "width":  _setDim(v, key, s); break;
            case "height": _setDim(v, key, s); break;
            case "padding":       { int p = _px(ctx, s); v.setPadding(p,p,p,p); break; }
            case "paddingTop":    v.setPadding(v.getPaddingLeft(), _px(ctx,s), v.getPaddingRight(), v.getPaddingBottom()); break;
            case "paddingBottom": v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), _px(ctx,s)); break;
            case "paddingLeft":   v.setPadding(_px(ctx,s), v.getPaddingTop(), v.getPaddingRight(), v.getPaddingBottom()); break;
            case "paddingRight":  v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), _px(ctx,s), v.getPaddingBottom()); break;
            case "margin": {
                if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    int m = _px(ctx, s);
                    ((ViewGroup.MarginLayoutParams)v.getLayoutParams()).setMargins(m,m,m,m);
                }
                break;
            }
            case "orientation":
                if (v instanceof LinearLayout)
                    ((LinearLayout)v).setOrientation("horizontal".equals(s) ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
                break;
            case "gravity":
                if (v instanceof LinearLayout) ((LinearLayout)v).setGravity(_gravity(s));
                else if (v instanceof TextView) ((TextView)v).setGravity(_gravity(s));
                break;
            // appearance
            case "backgroundColor": v.setBackgroundColor(_color(s)); break;
            case "cornerRadius": {
                GradientDrawable gd = new GradientDrawable();
                gd.setCornerRadius(_px(ctx, s));
                if (v.getBackground() instanceof GradientDrawable) {
                    GradientDrawable old = (GradientDrawable) v.getBackground();
                    gd.setColor(old.getColor() != null ? old.getColor().getDefaultColor() : Color.TRANSPARENT);
                }
                v.setBackground(gd);
                break;
            }
            case "alpha":    v.setAlpha(Float.parseFloat(s)); break;
            case "elevation": v.setElevation(_px(ctx, s)); break;
            case "visibility": v.setVisibility("gone".equals(s) ? View.GONE : "invisible".equals(s) ? View.INVISIBLE : View.VISIBLE); break;
            // text
            case "text":      if (v instanceof TextView) ((TextView)v).setText(s); break;
            case "hint":      if (v instanceof EditText) ((EditText)v).setHint(s); break;
            case "textColor": if (v instanceof TextView) ((TextView)v).setTextColor(_color(s)); break;
            case "textSize":  if (v instanceof TextView) ((TextView)v).setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, _num(s)); break;
            case "textStyle":
                if (v instanceof TextView) {
                    int style = "bold".equals(s) ? Typeface.BOLD : "italic".equals(s) ? Typeface.ITALIC : Typeface.NORMAL;
                    ((TextView)v).setTypeface(null, style);
                }
                break;
            // image
            case "src":
                if (v instanceof ImageView) {
                    ImageView iv = (ImageView) v;
                    if (s.startsWith("http")) {
                        // async image loading via network module
                        ctx._modules.get("network").call("loadImage", iv, s);
                    } else {
                        int resId = ctx.getResources().getIdentifier(s, "drawable", ctx.getPackageName());
                        if (resId != 0) iv.setImageResource(resId);
                    }
                }
                break;
            case "scaleType":
                if (v instanceof ImageView) ((ImageView)v).setScaleType(_scaleType(s));
                break;
            // input
            case "inputType":
                if (v instanceof EditText) {
                    switch (s) {
                        case "password": ((EditText)v).setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD); break;
                        case "number":   ((EditText)v).setInputType(android.text.InputType.TYPE_CLASS_NUMBER); break;
                        case "email":    ((EditText)v).setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS); break;
                        case "phone":    ((EditText)v).setInputType(android.text.InputType.TYPE_CLASS_PHONE); break;
                    }
                }
                break;
            case "multiline":
                if (v instanceof EditText && "true".equals(s))
                    ((EditText)v).setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                break;
            // events
            case "onClick": {
                String cbId = s;
                v.setOnClickListener(view -> ctx.fireCallback(cbId, null, null));
                break;
            }
            case "onLongClick": {
                String cbId = s;
                v.setOnLongClickListener(view -> { ctx.fireCallback(cbId, null, null); return true; });
                break;
            }
            case "onChange":
                if (v instanceof EditText) {
                    String cbId = s;
                    ((EditText)v).addTextChangedListener(new android.text.TextWatcher() {
                        public void afterTextChanged(android.text.Editable e) {
                            ctx.fireRawCallback(cbId, "[\"" + e.toString().replace("\"","\\\"") + "\"]");
                        }
                        public void beforeTextChanged(CharSequence c, int a, int b, int cc) {}
                        public void onTextChanged(CharSequence c, int a, int b, int count) {}
                    });
                }
                break;
            // shape
            case "shapeType":
            case "shapeColor":
                if (v instanceof ShapeView) ((ShapeView)v).setShapeProp(key, s);
                break;
        }
    }

    // ── Module call (canvas, list, anim, events, remove) ─

    @Override
    public Object call(String method, Object... args) {
        switch (method) {
            case "remove": {
                int id = _int(args[0]);
                View v = _ctx._views.get(id);
                if (v != null && v.getParent() instanceof ViewGroup)
                    _ctx._ui.post(() -> ((ViewGroup)v.getParent()).removeView(v));
                return null;
            }
            case "render": {
                int id = _int(args[0]);
                String json = args[1] != null ? args[1].toString() : "{}";
                View v = _ctx._views.get(id);
                if (v instanceof SvgCanvasView)
                    _ctx._ui.post(() -> ((SvgCanvasView) v).setShapes(json));
                return null;
            }
            case "getProp": {
                int id = _int(args[0]);
                String key = args[1].toString();
                View v = _ctx._views.get(id);
                if (v instanceof TextView && "text".equals(key)) return ((TextView)v).getText().toString();
                return null;
            }
            case "focus": { int id=_int(args[0]); View v=_ctx._views.get(id); if(v!=null) _ctx._ui.post(v::requestFocus); return null; }
            case "blur":  { int id=_int(args[0]); View v=_ctx._views.get(id); if(v!=null) _ctx._ui.post(v::clearFocus);   return null; }
            // Canvas operations
            case "drawRect":
            case "drawCircle":
            case "drawLine":
            case "drawText":
            case "clear":
            case "invalidate": {
                int id = _int(args[0]);
                View v = _ctx._views.get(id);
                if (v instanceof CsuaCanvasView) ((CsuaCanvasView)v).handleCommand(method, args);
                return null;
            }
            // List
            case "setData": {
                int id = _int(args[0]);
                View v = _ctx._views.get(id);
                if (v instanceof RecyclerView) {
                    // TODO: wire up adapter
                }
                return null;
            }
            // Animate
            case "animate": {
                int id = _int(args[0]);
                View v = _ctx._views.get(id);
                if (v == null) return null;
                String propsJson = args[1].toString();
                int dur = _int(args[2]);
                String cbId = args[5].toString();
                _ctx._ui.post(() -> {
                    try {
                        org.json.JSONObject p = new org.json.JSONObject(propsJson);
                        android.animation.AnimatorSet set = new android.animation.AnimatorSet();
                        java.util.List<android.animation.Animator> anims = new java.util.ArrayList<>();
                        if (p.has("alpha"))       anims.add(android.animation.ObjectAnimator.ofFloat(v, "alpha",       (float)p.getDouble("alpha")));
                        if (p.has("translationX")) anims.add(android.animation.ObjectAnimator.ofFloat(v, "translationX", (float)p.getDouble("translationX")));
                        if (p.has("translationY")) anims.add(android.animation.ObjectAnimator.ofFloat(v, "translationY", (float)p.getDouble("translationY")));
                        if (p.has("scaleX"))      anims.add(android.animation.ObjectAnimator.ofFloat(v, "scaleX",      (float)p.getDouble("scaleX")));
                        if (p.has("scaleY"))      anims.add(android.animation.ObjectAnimator.ofFloat(v, "scaleY",      (float)p.getDouble("scaleY")));
                        if (p.has("rotation"))    anims.add(android.animation.ObjectAnimator.ofFloat(v, "rotation",    (float)p.getDouble("rotation")));
                        set.playTogether(anims);
                        set.setDuration(dur);
                        set.addListener(new android.animation.AnimatorListenerAdapter() {
                            public void onAnimationEnd(android.animation.Animator a) {
                                ctx.fireCallback(cbId, null, null);
                            }
                        });
                        set.start();
                    } catch (Exception e) { ctx.fireCallback(cbId, e.getMessage(), null); }
                });
                return null;
            }
        }
        return null;
    }

    // ── Helpers ──────────────────────────────────────────

    static int _px(Context ctx, String dp) {
        String num = dp.replace("dp","").replace("sp","").replace("px","").trim();
        float n;
        try { n = Float.parseFloat(num); } catch (Exception e) { return 0; }
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round(n * density);
    }

    static float _num(String s) {
        try { return Float.parseFloat(s.replaceAll("[^0-9.]","")); } catch (Exception e) { return 14f; }
    }

    static int _int(Object o) {
        if (o instanceof Integer) return (Integer) o;
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
    }

    static int _color(String s) {
        try { return Color.parseColor(s); } catch (Exception e) { return Color.TRANSPARENT; }
    }

    static int _gravity(String s) {
        switch (s) {
            case "center":            return android.view.Gravity.CENTER;
            case "center_horizontal": return android.view.Gravity.CENTER_HORIZONTAL;
            case "center_vertical":   return android.view.Gravity.CENTER_VERTICAL;
            case "start":             return android.view.Gravity.START;
            case "end":               return android.view.Gravity.END;
            case "top":               return android.view.Gravity.TOP;
            case "bottom":            return android.view.Gravity.BOTTOM;
            default:                  return android.view.Gravity.NO_GRAVITY;
        }
    }

    static ImageView.ScaleType _scaleType(String s) {
        switch (s) {
            case "fill":        return ImageView.ScaleType.FIT_XY;
            case "contain":     return ImageView.ScaleType.FIT_CENTER;
            case "cover":       return ImageView.ScaleType.CENTER_CROP;
            case "center":      return ImageView.ScaleType.CENTER;
            default:            return ImageView.ScaleType.FIT_CENTER;
        }
    }

    static void _setDim(View v, String key, String s) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null) lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int val = "match".equals(s) || "100%".equals(s)
            ? ViewGroup.LayoutParams.MATCH_PARENT
            : "wrap".equals(s) ? ViewGroup.LayoutParams.WRAP_CONTENT
            : _px(v.getContext(), s);
        if ("width".equals(key))  lp.width  = val;
        else                       lp.height = val;
        v.setLayoutParams(lp);
    }
}
