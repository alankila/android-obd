package fi.bel.android.obd.util;

import android.content.Context;

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
    public String[] unit() {
        return new String[] { getUnit1(), getUnit2() };
    }

    private String getUnit1() {
        return "V";
    }

    private String getUnit2() {
        return "%";
    }

    @Override
    public String[] stringValue(Context context, String response) {
        return new String[] {
                String.format("%.2f %s", getValue1(response), getUnit1()),
                String.format("%.2f %s", getValue2(response), getUnit2())
        };
    }

    @Override
    public float[] floatValue(String response) {
        return new float[] { getValue1(response), getValue2(response) };
    }

    private float getValue1(String response) {
        return Integer.parseInt(response.substring(0, 2), 16) / 200.0f;
    }

    private float getValue2(String response) {
        return (Integer.parseInt(response.substring(2, 4), 16) - 128) * 100 / 128.0f;
    }
}
