package fi.bel.android.obd.util;

import android.content.Context;

import fi.bel.android.obd.R;

/**
 * Created by alankila on 8.1.2014.
 */
public class OxygenSensor extends PID {
    private final int bank;

    private final int sensor;

    public OxygenSensor(int code, int bank, int sensor) {
        super(code);
        this.bank = bank;
        this.sensor = sensor;
    }

    @Override
    public int values() {
        return 2;
    }

    @Override
    public String key(Context context, int idx) {
        if (idx == 0) {
            return String.format(context.getString(R.string.PID14_1), bank+1, sensor+1);
        } else {
            return String.format(context.getString(R.string.PID14_2), bank+1, sensor+1);
        }
    }

    @Override
    public String unit(int idx) {
        if (idx == 0) {
            return "V";
        } else {
            return "%";
        }
    }

    @Override
    public String stringValue(Context context, String response, int idx) {
        return String.format("%.2f %s", floatValue(response, idx), unit(idx));
    }

    @Override
    public float floatValue(String response, int idx) {
        if (idx == 0) {
            return Integer.parseInt(response.substring(0, 2), 16) / 200.0f;
        } else {
            return (Integer.parseInt(response.substring(2, 4), 16) - 128) * 100 / 128.0f;
        }
    }
}
