package fi.bel.android.obd.util;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by alankila on 8.1.2014.
 */
public class Oxygen13 extends PID {
    public Oxygen13(int code) {
        super(0x13);
    }

    @Override
    public String[] unit() {
        return new String[] { null };
    }

    @Override
    public String[] stringValue(Context context, String response) {
        int present = Integer.parseInt(response.substring(0, 2), 16);

        String result = "";
        for (int i = 0; i < 8; i += 1) {
            if ((present & (1 << 1)) != 0) {
                result += String.format("B%dS%d ", (i >> 2) + 1, (i & 3) + 1);
            }
        }
        return new String[] { result };
    }

    @Override
    public float[] floatValue(String response) {
        return new float[] { Float.NaN };
    }
}
