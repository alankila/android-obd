package fi.bel.android.obd.util;

import android.content.Context;

/**
 * Created by alankila on 8.1.2014.
 */
public class Standard extends PID {
    public Standard(int code) {
        super(code);
    }

    public String[] value(Context context, String response) {
        int value = Integer.parseInt(response.substring(4, 6), 16);
        String pid = String.format("PID1c_%02x", value);
        int id = context.getResources().getIdentifier(pid, "string", context.getPackageName());
        return new String[] { context.getString(id) };
    }
}
