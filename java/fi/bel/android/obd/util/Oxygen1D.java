package fi.bel.android.obd.util;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by alankila on 8.1.2014.
 */
public class Oxygen1D extends PID {
    public Oxygen1D(int code) {
        super(0x1d);
    }

    @Override
    public String unit() {
        return null;
    }

    @Override
    public String stringValue(Context context, String response, int idx) {
        int present = Integer.parseInt(response.substring(0, 2), 16);

        String result = "";
        for (int i = 0; i < 8; i += 1) {
            if ((present & (1 << 1)) != 0) {
                result += String.format("B%dS%d ", (i >> 1) + 1, (i & 1) + 1);
            }
        }
        return result;
    }

    @Override
    public float floatValue(String response, int idx) {
        return Float.NaN;
    }
}
