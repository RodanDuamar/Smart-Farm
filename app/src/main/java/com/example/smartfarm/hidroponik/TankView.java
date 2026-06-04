package com.example.smartfarm.hidroponik;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import com.example.smartfarm.R;

/**
 * TankView - Custom View untuk menampilkan indikator kapasitas tanki
 * dengan model tabung, persentase, dan animasi air (wave).
 */
public class TankView extends View {

    // --- Paint objects ---
    private Paint tankBodyPaint;
    private Paint tankStrokePaint;
    private Paint waterPaint;
    private Paint waterHighlightPaint;
    private Paint wavePaint;
    private Paint percentTextPaint;
    private Paint labelTextPaint;
    private Paint tickMarkPaint;
    private Paint bubblePaint;
    private Paint capPaint;
    private Paint capStrokePaint;
    private Paint glowPaint;

    // --- Data ---
    private int percent = 75; // 0 - 100
    private String label = "Vitamin A";

    // --- Colors ---
    private int waterColor = 0xFF4CAF50;        // Primary water color
    private int waterColorLight = 0xFF81C784;    // Lighter shade for gradient
    private int waterColorDark = 0xFF2E7D32;     // Darker shade for depth
    private int tankBodyColor = 0xFFF5F9F5;      // Tank body background
    private int tankStrokeColor = 0xFFBDBDBD;     // Tank border/stroke
    private int textColor = 0xFF1B1F1B;           // Percentage text color
    private int labelColor = 0xFF558B2F;          // Label text color

    // --- Wave animation ---
    private float waveOffset = 0f;
    private float waveOffset2 = 0f;
    private ValueAnimator waveAnimator;

    // --- Bubble animation ---
    private float[] bubbleX = new float[6];
    private float[] bubbleY = new float[6];
    private float[] bubbleRadius = new float[6];
    private float[] bubbleSpeed = new float[6];

    // --- Dimensions ---
    private float cornerRadius;
    private float strokeWidth;
    private float capHeight;

    // --- Paths & Rects ---
    private Path waterPath = new Path();
    private Path wavePath = new Path();
    private RectF tankRect = new RectF();
    private RectF capRect = new RectF();

    // --- Animated percent for smooth transitions ---
    private float animatedPercent = 0f;
    private ValueAnimator percentAnimator;

    public TankView(Context context) {
        super(context);
        init(context, null);
    }

    public TankView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public TankView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        // Read custom attributes if available
        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.TankView);
            percent = ta.getInt(R.styleable.TankView_tankPercent, 75);
            label = ta.getString(R.styleable.TankView_tankLabel);
            if (label == null) label = "Vitamin";
            waterColor = ta.getColor(R.styleable.TankView_tankWaterColor, 0xFF4CAF50);
            waterColorLight = ta.getColor(R.styleable.TankView_tankWaterColorLight, lightenColor(waterColor, 0.3f));
            waterColorDark = ta.getColor(R.styleable.TankView_tankWaterColorDark, darkenColor(waterColor, 0.3f));
            ta.recycle();
        }

        animatedPercent = percent;

        float density = context.getResources().getDisplayMetrics().density;
        cornerRadius = 24 * density;
        strokeWidth = 2.5f * density;
        capHeight = 18 * density;

        // Tank body paint
        tankBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tankBodyPaint.setColor(tankBodyColor);
        tankBodyPaint.setStyle(Paint.Style.FILL);

        // Tank stroke paint
        tankStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tankStrokePaint.setColor(tankStrokeColor);
        tankStrokePaint.setStyle(Paint.Style.STROKE);
        tankStrokePaint.setStrokeWidth(strokeWidth);

        // Water paint
        waterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        waterPaint.setStyle(Paint.Style.FILL);

        // Water highlight paint (for the glossy overlay)
        waterHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        waterHighlightPaint.setStyle(Paint.Style.FILL);

        // Wave paint (lighter wave overlay)
        wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint.setStyle(Paint.Style.FILL);

        // Percentage text
        percentTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        percentTextPaint.setColor(textColor);
        percentTextPaint.setTextSize(22 * density);
        percentTextPaint.setTextAlign(Paint.Align.CENTER);
        percentTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        // Label text
        labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelTextPaint.setColor(labelColor);
        labelTextPaint.setTextSize(12 * density);
        labelTextPaint.setTextAlign(Paint.Align.CENTER);
        labelTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        // Tick marks for scale
        tickMarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickMarkPaint.setColor(0x40000000);
        tickMarkPaint.setStrokeWidth(1 * density);

        // Bubble paint
        bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bubblePaint.setStyle(Paint.Style.FILL);

        // Cap paint (top of the tube)
        capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        capPaint.setStyle(Paint.Style.FILL);
        capPaint.setColor(0xFFE0E0E0);

        capStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        capStrokePaint.setStyle(Paint.Style.STROKE);
        capStrokePaint.setStrokeWidth(strokeWidth);
        capStrokePaint.setColor(0xFFBDBDBD);

        // Glow paint for the water surface
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.FILL);

        // Initialize bubbles
        initBubbles();

        // Start wave animation
        startWaveAnimation();
    }

    private void initBubbles() {
        for (int i = 0; i < bubbleX.length; i++) {
            bubbleX[i] = (float) (Math.random());
            bubbleY[i] = (float) (Math.random());
            bubbleRadius[i] = (float) (2 + Math.random() * 4);
            bubbleSpeed[i] = (float) (0.002 + Math.random() * 0.005);
        }
    }

    private void startWaveAnimation() {
        waveAnimator = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        waveAnimator.setDuration(3000);
        waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        waveAnimator.setInterpolator(new LinearInterpolator());
        waveAnimator.addUpdateListener(animation -> {
            waveOffset = (float) animation.getAnimatedValue();
            waveOffset2 = waveOffset * 0.7f + 1.2f;

            // Update bubbles
            for (int i = 0; i < bubbleY.length; i++) {
                bubbleY[i] -= bubbleSpeed[i];
                if (bubbleY[i] < 0) {
                    bubbleY[i] = 1f;
                    bubbleX[i] = (float) Math.random();
                    bubbleRadius[i] = (float) (2 + Math.random() * 4);
                }
            }

            invalidate();
        });
        waveAnimator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int defaultWidth = (int) (110 * density);
        int defaultHeight = (int) (220 * density);

        int width = resolveSize(defaultWidth, widthMeasureSpec);
        int height = resolveSize(defaultHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float density = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();

        float padding = 16 * density;
        float labelHeight = 30 * density;
        float percentDisplayHeight = 32 * density;

        // Tank area calculation
        float tankLeft = padding;
        float tankTop = padding + capHeight;
        float tankRight = w - padding;
        float tankBottom = h - padding - labelHeight - percentDisplayHeight;
        float tankWidth = tankRight - tankLeft;
        float tankHeight = tankBottom - tankTop;

        // --- Draw the cap (top of the tube) ---
        float capWidth = tankWidth * 0.5f;
        float capLeft = tankLeft + (tankWidth - capWidth) / 2f;
        float capRight = capLeft + capWidth;
        float capTop = tankTop - capHeight;
        float capBottom = tankTop + cornerRadius * 0.3f;
        capRect.set(capLeft, capTop, capRight, capBottom);
        float capCorner = 8 * density;

        // Cap gradient
        capPaint.setShader(new LinearGradient(capLeft, capTop, capRight, capTop,
                0xFFE8E8E8, 0xFFD0D0D0, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(capRect, capCorner, capCorner, capPaint);
        canvas.drawRoundRect(capRect, capCorner, capCorner, capStrokePaint);
        capPaint.setShader(null);

        // --- Draw the tank body (glass tube) ---
        tankRect.set(tankLeft, tankTop, tankRight, tankBottom);

        // Glass body gradient (subtle light reflection)
        LinearGradient glassGradient = new LinearGradient(
                tankLeft, tankTop, tankRight, tankTop,
                new int[]{0x20FFFFFF, 0x08FFFFFF, 0x10000000, 0x05FFFFFF},
                new float[]{0f, 0.3f, 0.7f, 1f},
                Shader.TileMode.CLAMP
        );

        tankBodyPaint.setColor(0xFFF8FAF8);
        canvas.drawRoundRect(tankRect, cornerRadius, cornerRadius, tankBodyPaint);

        // --- Draw tick marks (scale lines) ---
        for (int i = 0; i <= 10; i++) {
            float tickY = tankBottom - (tankHeight * i / 10f);
            float tickLength = (i % 5 == 0) ? 12 * density : 6 * density;
            tickMarkPaint.setAlpha(i % 5 == 0 ? 60 : 30);
            canvas.drawLine(tankLeft + 4 * density, tickY, tankLeft + tickLength, tickY, tickMarkPaint);
            canvas.drawLine(tankRight - tickLength, tickY, tankRight - 4 * density, tickY, tickMarkPaint);
        }

        // --- Draw water with wave animation ---
        float waterLevel = tankHeight * (animatedPercent / 100f);
        float waterTop = tankBottom - waterLevel;

        if (animatedPercent > 0) {
            // Save canvas and clip to tank shape
            canvas.save();
            Path clipPath = new Path();
            clipPath.addRoundRect(tankRect, cornerRadius, cornerRadius, Path.Direction.CW);
            canvas.clipPath(clipPath);

            // Main water fill with gradient
            LinearGradient waterGradient = new LinearGradient(
                    tankLeft, waterTop, tankLeft, tankBottom,
                    new int[]{waterColorLight, waterColor, waterColorDark},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            );
            waterPaint.setShader(waterGradient);

            // Draw primary wave
            waterPath.reset();
            float waveAmplitude = 5 * density;
            waterPath.moveTo(tankLeft, tankBottom);

            for (float x = tankLeft; x <= tankRight; x += 2) {
                float normalizedX = (x - tankLeft) / tankWidth;
                float y = waterTop + (float) (
                        Math.sin(normalizedX * 2 * Math.PI * 2 + waveOffset) * waveAmplitude +
                        Math.sin(normalizedX * 2 * Math.PI * 3 + waveOffset * 1.3f) * waveAmplitude * 0.4f
                );
                waterPath.lineTo(x, y);
            }

            waterPath.lineTo(tankRight, tankBottom);
            waterPath.close();
            canvas.drawPath(waterPath, waterPaint);
            waterPaint.setShader(null);

            // Draw secondary wave (lighter overlay for depth)
            wavePath.reset();
            float wave2Amplitude = 3 * density;
            wavePath.moveTo(tankLeft, tankBottom);

            for (float x = tankLeft; x <= tankRight; x += 2) {
                float normalizedX = (x - tankLeft) / tankWidth;
                float y = waterTop + (float) (
                        Math.sin(normalizedX * 2 * Math.PI * 1.5 + waveOffset2) * wave2Amplitude +
                        Math.sin(normalizedX * 2 * Math.PI * 2.5 + waveOffset2 * 0.8f) * wave2Amplitude * 0.5f
                ) + wave2Amplitude;
                wavePath.lineTo(x, y);
            }

            wavePath.lineTo(tankRight, tankBottom);
            wavePath.close();

            wavePaint.setColor(setAlpha(waterColorLight, 80));
            canvas.drawPath(wavePath, wavePaint);

            // Draw glossy highlight on left side of water
            float highlightWidth = tankWidth * 0.12f;
            RectF highlightRect = new RectF(
                    tankLeft + tankWidth * 0.15f,
                    waterTop + 8 * density,
                    tankLeft + tankWidth * 0.15f + highlightWidth,
                    tankBottom - 8 * density
            );
            waterHighlightPaint.setShader(new LinearGradient(
                    highlightRect.left, highlightRect.top,
                    highlightRect.right, highlightRect.top,
                    0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
            ));
            canvas.drawRoundRect(highlightRect, highlightWidth / 2, highlightWidth / 2, waterHighlightPaint);
            waterHighlightPaint.setShader(null);

            // Draw bubbles
            for (int i = 0; i < bubbleX.length; i++) {
                float bx = tankLeft + bubbleX[i] * tankWidth;
                float by = waterTop + (1f - bubbleY[i]) * waterLevel;

                if (by > waterTop && by < tankBottom) {
                    float br = bubbleRadius[i] * density;
                    bubblePaint.setColor(setAlpha(0xFFFFFFFF, (int) (40 + bubbleY[i] * 40)));
                    canvas.drawCircle(bx, by, br, bubblePaint);

                    // Bubble highlight
                    bubblePaint.setColor(setAlpha(0xFFFFFFFF, (int) (20 + bubbleY[i] * 30)));
                    canvas.drawCircle(bx - br * 0.25f, by - br * 0.25f, br * 0.4f, bubblePaint);
                }
            }

            // Glow at water surface
            float glowHeight = 6 * density;
            RectF glowRect = new RectF(tankLeft, waterTop - glowHeight / 2, tankRight, waterTop + glowHeight / 2);
            glowPaint.setShader(new LinearGradient(
                    tankLeft, glowRect.top, tankLeft, glowRect.bottom,
                    0x00FFFFFF, setAlpha(waterColorLight, 50), Shader.TileMode.CLAMP
            ));
            canvas.drawRect(glowRect, glowPaint);
            glowPaint.setShader(null);

            canvas.restore();
        }

        // --- Draw tank border (glass outline) ---
        tankStrokePaint.setColor(0xFFCCCCCC);
        canvas.drawRoundRect(tankRect, cornerRadius, cornerRadius, tankStrokePaint);

        // Glass reflection line (left edge)
        Paint reflectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        reflectionPaint.setStyle(Paint.Style.STROKE);
        reflectionPaint.setStrokeWidth(2 * density);
        reflectionPaint.setShader(new LinearGradient(
                tankLeft + 6 * density, tankTop + cornerRadius,
                tankLeft + 6 * density, tankBottom - cornerRadius,
                new int[]{0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawLine(
                tankLeft + 8 * density, tankTop + cornerRadius + 10 * density,
                tankLeft + 8 * density, tankBottom - cornerRadius - 10 * density,
                reflectionPaint
        );

        // --- Draw percentage text ---
        float percentY = tankBottom + percentDisplayHeight * 0.75f;
        String percentStr = (int) animatedPercent + "%";

        // Choose text color based on percent level
        if (animatedPercent > 50) {
            percentTextPaint.setColor(waterColorDark);
        } else if (animatedPercent > 20) {
            percentTextPaint.setColor(0xFFF57C00); // Orange warning
        } else {
            percentTextPaint.setColor(0xFFF44336); // Red danger
        }

        canvas.drawText(percentStr, w / 2f, percentY, percentTextPaint);

        // --- Draw label ---
        float labelY = percentY + labelHeight * 0.65f;
        labelTextPaint.setColor(labelColor);
        canvas.drawText(label, w / 2f, labelY, labelTextPaint);
    }

    // --- Public API ---

    /**
     * Set the tank percentage (0-100) with smooth animation.
     */
    public void setPercent(int newPercent) {
        newPercent = Math.max(0, Math.min(100, newPercent));
        this.percent = newPercent;

        if (percentAnimator != null && percentAnimator.isRunning()) {
            percentAnimator.cancel();
        }

        percentAnimator = ValueAnimator.ofFloat(animatedPercent, newPercent);
        percentAnimator.setDuration(800);
        percentAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        percentAnimator.addUpdateListener(animation -> {
            animatedPercent = (float) animation.getAnimatedValue();
            invalidate();
        });
        percentAnimator.start();
    }

    /**
     * Set the tank percentage immediately without animation.
     */
    public void setPercentImmediate(int newPercent) {
        newPercent = Math.max(0, Math.min(100, newPercent));
        this.percent = newPercent;
        this.animatedPercent = newPercent;
        invalidate();
    }

    /**
     * Get the current percent value.
     */
    public int getPercent() {
        return percent;
    }

    /**
     * Set the label below the tank.
     */
    public void setLabel(String label) {
        this.label = label;
        invalidate();
    }

    /**
     * Set the water color scheme.
     */
    public void setWaterColor(int primary) {
        this.waterColor = primary;
        this.waterColorLight = lightenColor(primary, 0.3f);
        this.waterColorDark = darkenColor(primary, 0.3f);
        invalidate();
    }

    /**
     * Set all three water colors explicitly.
     */
    public void setWaterColors(int light, int primary, int dark) {
        this.waterColorLight = light;
        this.waterColor = primary;
        this.waterColorDark = dark;
        invalidate();
    }

    // --- Lifecycle ---

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (waveAnimator != null && !waveAnimator.isRunning()) {
            waveAnimator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (waveAnimator != null) {
            waveAnimator.cancel();
        }
        if (percentAnimator != null) {
            percentAnimator.cancel();
        }
    }

    // --- Color utility methods ---

    private static int lightenColor(int color, float factor) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        r = (int) (r + (255 - r) * factor);
        g = (int) (g + (255 - g) * factor);
        b = (int) (b + (255 - b) * factor);
        return Color.argb(Color.alpha(color), Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
    }

    private static int darkenColor(int color, float factor) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        r = (int) (r * (1 - factor));
        g = (int) (g * (1 - factor));
        b = (int) (b * (1 - factor));
        return Color.argb(Color.alpha(color), Math.max(r, 0), Math.max(g, 0), Math.max(b, 0));
    }

    private static int setAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
