package com.gabiq.youbid.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;

import com.squareup.picasso.Transformation;

public class RoundTransform implements Transformation
{
    private final int radius;
    private final int margin;  // dp

    public RoundTransform()
    {
        this.radius = 0;
        this.margin = 0;
    }

    public RoundTransform(final int radius, final int margin)
    {
        this.radius = radius;
        this.margin = margin;
    }

    @Override
    public Bitmap transform(final Bitmap source)
    {
        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));

        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        int dimension = source.getWidth();
        if (source.getHeight() < dimension) {
            dimension = source.getHeight();
        }
        canvas.drawCircle(margin + source.getWidth()/2, margin + source.getHeight()/2, dimension/2, paint);
//        canvas.drawRoundRect(new RectF(margin, margin, source.getWidth() - margin, source.getHeight() - margin), radius, radius, paint);

        if (source != output) {
            source.recycle();
        }

        return output;
    }

    @Override
    public String key()
    {
        return "round";
    }
}