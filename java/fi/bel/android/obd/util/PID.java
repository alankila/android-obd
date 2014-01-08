package fi.bel.android.obd.util;

import android.content.Context;

/**
 * Created by alankila on 8.1.2014.
 */
public class PID {
    public static class Sensor {
        private final PID pid;

        private final int i;

        public Sensor(PID pid, int i) {
            this.pid = pid;
            this.i = i;
        }

        public PID getPid() {
            return pid;
        }

        public int getIndex() {
            return i;
        }
    }

    private final int code;

    public PID(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public int values() {
        return 1;
    }

    public String[] key(Context context) {
        String pid = String.format("PID%02x", code);
        int id = context.getResources().getIdentifier(pid, "string", context.getPackageName());
        return new String[] { context.getString(id) };
    }

    public String[] unit() {
        switch (code) {
            case 0x04:
            case 0x11:
                return new String[] { "%" };
            case 0x05:
            case 0x0f:
                return new String[] { "C" };
            case 0x06:
            case 0x07:
            case 0x08:
            case 0x09:
                return new String[] { "%" };
            case 0x0a:
            case 0x0b:
                return new String[] { "kPa" };
            case 0x0c:
                return new String[] { "rpm" };
            case 0x0d:
                return new String[] { "km/h" };
            case 0x0e:
                return new String[] { "deg" };
            case 0x10:
                return new String[] { "g/s" };
            case 0x1f:
                return new String[] { "s" };
            case 0x21:
                return new String[] { "km" };
            default:
                return null;
        }
    }

    public String[] stringValue(Context context, String response) {
        return new String[] { String.format("%.2f %s", floatValue(response)[0], unit()[0]) };
    }

    public float[] floatValue(String response) {
        switch (code) {
            case 0x04:
            case 0x11:
                return new float[] { Integer.parseInt(response.substring(0, 2), 16) * 100 / 255.0f };
            case 0x05:
            case 0x0f:
                return new float[] { Integer.parseInt(response.substring(0, 2), 16) - 40 };
            case 0x06:
            case 0x07:
            case 0x08:
            case 0x09:
                return new float[] { (Integer.parseInt(response.substring(0, 2), 16) - 128) * 100 / 128.0f };
            case 0x0a:
                return new float[] { Integer.parseInt(response.substring(0, 2), 16) * 3.0f };
            case 0x0b:
            case 0x0d:
            case 0x1f:
                return new float[] { Integer.parseInt(response.substring(0, 2), 16) };
            case 0x0c:
                return new float[] { Integer.parseInt(response.substring(0, 4), 16) / 4.0f };
            case 0x0e:
                return new float[] { (Integer.parseInt(response.substring(0, 2), 16) - 128) / 2.0f };
            case 0x10:
                return new float[] { Integer.parseInt(response.substring(0, 4), 16) / 100.0f };
            case 0x21:
                return new float[] { Integer.parseInt(response.substring(0, 4), 16) };

            default:
                return null;
        }
    }

    public static PID make(int code) {
        switch (code) {
            case 0x03:
                return new FuelSystem(code);
            case 0x14:
            case 0x15:
            case 0x16:
            case 0x17:
            case 0x18:
            case 0x19:
            case 0x1a:
            case 0x1b:
                return null;
            case 0x13:
                return new Oxygen13(code);
            case 0x1c:
                return new Standard(code);
            case 0x1d:
                return new Oxygen1D(code);

            default:
                return new PID(code);
        }
    }
}
