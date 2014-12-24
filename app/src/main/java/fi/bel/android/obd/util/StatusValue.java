package fi.bel.android.obd.util;

import android.content.Context;

/**
 * Visualizes a value by translating the enumerated 8-bit value using
 * PID%02x_%02x as the pattern, where first is code and second is value.
 * When not found, visualizes 0x%02x.
 *
 * Created by alankila on 8.1.2014.
 */
public class StatusValue extends PID {
    public StatusValue(int code) {
        super(code);
    }

    @Override
    public String stringValue(Context context, String response, int idx) {
        int value = Integer.parseInt(response.substring(0, 2), 16);
        String pid = String.format("PID%02x_%02x", getCode(), value);
        int id = context.getResources().getIdentifier(pid, "string", context.getPackageName());
        if (id != 0) {
            return context.getString(id);
        } else {
            return String.format("0x%02x", value);
        }
    }

    @Override
    public float floatValue(String response, int idx) {
        return Float.NaN;
    }
}
