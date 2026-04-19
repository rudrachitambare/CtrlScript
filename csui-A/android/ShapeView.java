package com.ctrlscript.csua;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

public class ShapeView extends View {

    private String _type  = "rect";
    private int    _color = Color.WHITE;
    private float  _radius = 0;
    private final Paint _paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ShapeView(Context ctx) { super(ctx); }

    public void setShapeProp(String key, String value) {
        switch (key) {
            case "shapeType":  _type   = value; break;
            case "shapeColor": try { _color  = Color.parseColor(value); } catch (Exception ignored) {} break;
            case "cornerRadius": try { _radius = Float.parseFloat(value.replace("dp","").replace("px","").trim()); } catch (Exception ignored) {} break;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        _paint.setColor(_color);
        float w = getWidth(), h = getHeight();
        switch (_type) {
            case "oval": canvas.drawOval(0, 0, w, h, _paint); break;
            default:
                if (_radius > 0) canvas.drawRoundRect(0, 0, w, h, _radius, _radius, _paint);
                else             canvas.drawRect(0, 0, w, h, _paint);
        }
    }
}
