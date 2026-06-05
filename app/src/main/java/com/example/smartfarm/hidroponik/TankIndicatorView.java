package com.example.smartfarm.hidroponik;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.example.smartfarm.R; // Pastikan R ini merujuk ke package aplikasi Anda

public class TankIndicatorView extends View {

    private String tankLabel = "";
    private int tankWaterColor = Color.BLUE;
    private int tankWaterColorLight = Color.CYAN;
    private int tankBorderColor = Color.DKGRAY;
    private int tankPercentage = 0;

    private Paint borderPaint;
    private Paint waterPaint;
    private Paint textPaint;
    private Paint textBgPaint;
    private Path path;

    public TankIndicatorView(Context context) {
        super(context);
        init(context, null);
    }

    public TankIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public TankIndicatorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(6f);

        waterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        waterPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(38f);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);

        textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textBgPaint.setColor(Color.parseColor("#66000000")); // Background semi-transparan untuk teks %
        textBgPaint.setStyle(Paint.Style.FILL);

        path = new Path();

        if (attrs != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.TankIndicatorView, 0, 0);
            try {
                tankLabel = typedArray.getString(R.styleable.TankIndicatorView_tankLabel);
                if (tankLabel == null) tankLabel = "";
                tankWaterColor = typedArray.getColor(R.styleable.TankIndicatorView_tankWaterColor, Color.BLUE);
                tankWaterColorLight = typedArray.getColor(R.styleable.TankIndicatorView_tankWaterColorLight, Color.CYAN);
                tankBorderColor = typedArray.getColor(R.styleable.TankIndicatorView_tankBorderColor, Color.DKGRAY);
                tankPercentage = typedArray.getInt(R.styleable.TankIndicatorView_tankPercentage, 0);
            } finally {
                typedArray.recycle();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float strokeWidth = borderPaint.getStrokeWidth();
        float inset = strokeWidth / 2;
        
        // Sesuaikan ukuran dengan stroke width agar tidak terpotong (clipped) di tepi
        float w = getWidth() - strokeWidth;
        float h = getHeight() - strokeWidth;
        
        canvas.save();
        canvas.translate(inset, inset);

        float ovalHeight = h * 0.08f; // Efek 3D kelengkungan tabung

        borderPaint.setColor(tankBorderColor);
        waterPaint.setColor(tankWaterColor);

        // 1. Gambar Air di Dalam Tanki
        if (tankPercentage > 0) {
            float maxWaterHeight = h - (ovalHeight * 2);
            float waterLevelHeight = h - ovalHeight - (maxWaterHeight * (tankPercentage / 100f));

            path.reset();
            path.moveTo(0f, h - ovalHeight);
            path.lineTo(0f, waterLevelHeight);

            // Membuat permukaan air sedikit bergelombang static di preview
            path.quadTo(w / 4f, waterLevelHeight - 10f, w / 2f, waterLevelHeight);
            path.quadTo(3f * w / 4f, waterLevelHeight + 10f, w, waterLevelHeight);

            path.lineTo(w, h - ovalHeight);

            RectF bottomOvalWater = new RectF(0f, h - (ovalHeight * 2), w, h);
            path.arcTo(bottomOvalWater, 0f, 180f);
            path.close();

            canvas.drawPath(path, waterPaint);

            // Permukaan Atas Air (Efek 3D Oval)
            waterPaint.setColor(tankWaterColorLight);
            RectF topOvalWater = new RectF(0f, waterLevelHeight - ovalHeight, w, waterLevelHeight + ovalHeight);
            canvas.drawOval(topOvalWater, waterPaint);
        }

        // 2. Gambar Struktur Utama Tabung (Tanki Silinder)
        RectF topOval = new RectF(0f, 0f, w, ovalHeight * 2);
        RectF bottomOval = new RectF(0f, h - (ovalHeight * 2), w, h);

        // Garis dinding kiri & kanan serta lengkungan bawah
        canvas.drawArc(bottomOval, 0f, 180f, false, borderPaint);
        canvas.drawLine(0f, ovalHeight, 0f, h - ovalHeight, borderPaint);
        canvas.drawLine(w, ovalHeight, w, h - ovalHeight, borderPaint);

        // Lingkaran atas tanki terbuka
        canvas.drawOval(topOval, borderPaint);

        // 3. Gambar Teks Persentase di Tengah-tengah
        String text = tankPercentage + "%";
        float textWidth = textPaint.measureText(text);
        float textX = w / 2;
        float textY = (h / 2) - ((textPaint.descent() + textPaint.ascent()) / 2);

        // Background box agar teks persentase mudah dibaca
        RectF bgRect = new RectF(textX - (textWidth / 2) - 12f, textY + textPaint.ascent() - 8f, textX + (textWidth / 2) + 12f, textY + textPaint.descent() + 8f);
        canvas.drawRoundRect(bgRect, 8f, 8f, textBgPaint);

        canvas.drawText(text, textX, textY, textPaint);

        // 4. Gambar Label Tanki (jika ada)
        if (tankLabel != null && !tankLabel.isEmpty()) {
            Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            labelPaint.setColor(tankBorderColor);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setTextSize(24f);
            labelPaint.setTypeface(Typeface.DEFAULT_BOLD);
            // Gambar di bagian bawah tabung, sedikit di atas garis lengkung bawah
            canvas.drawText(tankLabel, w / 2, h - (ovalHeight * 2.5f), labelPaint);
        }

        canvas.restore();
    }

    public void setPercentage(int percentage) {
        this.tankPercentage = Math.max(0, Math.min(percentage, 100));
        invalidate();
    }
}