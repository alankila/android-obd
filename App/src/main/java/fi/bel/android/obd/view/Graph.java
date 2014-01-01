package fi.bel.android.obd.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.SurfaceView;

import java.util.Map;
import java.util.TreeMap;

public class Graph extends SurfaceView {
    protected static final long WINDOW_MS = 1000 * 60 * 30;

    protected static final Paint BG = new Paint();
    protected static final Paint BORDER = new Paint();
    protected static final Paint GRID = new Paint();
    protected static final Paint PEN = new Paint();
    static {
        BG.setARGB(0xff, 0, 0, 0);
        BORDER.setARGB(0xff, 0xff, 0xff, 0xff);
        BORDER.setTextSize(8);
        GRID.setARGB(0x20, 0xff, 0xff, 0xff);
        PEN.setARGB(0xff, 0, 0xff, 0);
        PEN.setStrokeWidth(2.0f);
    }

    protected Map<Long, Float> series = new TreeMap<>();

    protected float min = Float.MAX_VALUE, max = Float.MIN_VALUE;

    public Graph(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();

        /* Blank */
        canvas.drawRect(0, 0, width, height, BG);

        /* Draw frame */
        canvas.drawLine(0, 0, width - 1, 0, BORDER);
        canvas.drawLine(width - 1, 0, width - 1, height - 1, BORDER);
        canvas.drawLine(width - 1, height - 1, 0, height - 1, BORDER);
        canvas.drawLine(0, height-1, 0, 0, BORDER);

        /* Time grid */
        for (int i = -30; i <= 0; i += 5) {
            float x = width - 1 + (i * 60 * 1000) / (float) WINDOW_MS;
            canvas.drawLine(x, 1, x, height - 2, GRID);
            canvas.drawText(String.format("%d min", i), x, height - 1, BORDER);
        }

        /* Value grid */
        float diff = max - min;
        float scale = (float) Math.pow(10, Math.log(diff) / Math.log(10) - 1);
        float pos = Math.round(min * scale) / scale;
        for (int i = 0; i < 5; i += 1) {
            float y = (pos - min) / (max - min) * (height - 1);
            canvas.drawLine(1, y, width - 2, y, GRID);
            canvas.drawText(String.format("%g", pos), 0, y, BORDER);
            pos += diff / 5;
        }

        /* Value series */
        long now = System.currentTimeMillis();
        float x1 = Float.NaN, y1 = Float.NaN;
        for (Map.Entry<Long, Float> entry : series.entrySet()) {
            long time = entry.getKey();
            float value = entry.getValue();

            float x2 = width - 1 + (time - now) / (float) WINDOW_MS;
            float y2 = (value - min) / (max - min) * (height - 1);

            if (x1 != Float.NaN) {
                canvas.drawLine(x1, y1, x2, y2, PEN);
            }
            x1 = x2;
            y1 = y2;
        }
    }

    /**
     * Add a point to the value series
     *
     * @param time
     * @param value
     */
    public void addPoint(long time, float value) {
        series.put(time, value);
        if (value - 1 < min) {
            min = value - 1;
        }
        if (value + 1 > max) {
            max = value + 1;
        }
    }
}
