package fi.bel.android.obd.util;

import android.content.Context;

/**
 * Created by alankila on 8.1.2014.
 */
public class FuelSystem extends PID {
    public FuelSystem(int code) {
        super(code);
    }

    public String[] value(Context context, String response) {
        int value = Integer.parseInt(response.substring(0, 2), 16);
        String pid = String.format("PID03_%02x", value);
        int id = context.getResources().getIdentifier(pid, "string", context.getPackageName());
        return new String[] { context.getString(id) };
    }

    @Override
    public float[] floatValue(String response) {
        return new float[] { Float.NaN };
    }
}
