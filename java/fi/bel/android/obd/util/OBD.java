package fi.bel.android.obd.util;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OBD {
    protected static final Charset UTF8 = Charset.forName("UTF_8");

    protected static Map<String, List<String>> faultHeaderMap;

    protected static Map<String, String> faultMap;

    public static Map<String, List<String>> getFaultHeaderMap(Context context) {
        if (faultHeaderMap == null) {
            fillInFaultMaps(context);
        }
        return faultHeaderMap;
    }

    public static Map<String, String> getFaultMap(Context context) {
        if (faultMap == null) {
            fillInFaultMaps(context);
        }
        return faultMap;
    }

    private static void fillInFaultMaps(Context context) {
        faultHeaderMap = new LinkedHashMap<>();
        faultMap = new HashMap<>();
        try {
            InputStream stream = context.getAssets().open("codes.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(stream, UTF8));
            String line;
            List<String> activeHeader = null;
            boolean header = true;
            while (null != (line = br.readLine())) {
                line = line.trim();
                if (header) {
                    faultHeaderMap.put(line, activeHeader = new ArrayList<String>());
                    header = false;
                    continue;
                }
                if (line.isEmpty()) {
                    header = true;
                    continue;
                }
                String[] code = line.split("\\s", 2);
                faultMap.put(code[0], code[1]);
                activeHeader.add(code[0]);
            }
            stream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* XXX: uninterpreted stuff we might want to add later: 03, 12, 13, 1c, 1d, 1e */
    public static String unit(String pid) {
        switch (pid) {
            case "04":
            case "11":
                return "%";
            case "05":
            case "0f":
                return "C";
            case "06":
            case "07":
            case "08":
            case "09":
                return "%";
            case "0a":
            case "0b":
                return "kPa";
            case "0c":
                return "rpm";
            case "0d":
                return "km/h";
            case "0e":
                return "deg";
            case "10":
                return "g/s";
            case "14_1":
            case "15_1":
            case "16_1":
            case "17_1":
            case "18_1":
            case "19_1":
            case "1a_1":
            case "1b_1":
                return "V";
            case "14_2":
            case "15_2":
            case "16_2":
            case "17_2":
            case "18_2":
            case "19_2":
            case "1a_2":
            case "1b_2":
                return "%";
            case "1f":
                return "s";
            case "21":
                return "km";
            default:
                return null;
        }
    }

    public static float convert(String pid, String response) {
        switch (pid) {
            case "04":
            case "11":
                return Integer.parseInt(response.substring(4, 6), 16) * 100 / 255.0f;
            case "05":
            case "0f":
                return Integer.parseInt(response.substring(4, 6), 16) - 40;
            case "06":
            case "07":
            case "08":
            case "09":
                return (Integer.parseInt(response.substring(4, 6), 16) - 128) * 100 / 128.0f;
            case "0a":
                return Integer.parseInt(response.substring(4, 6), 16) * 3.0f;
            case "0b":
            case "0d":
            case "1f":
                return Integer.parseInt(response.substring(4, 6), 16);
            case "0c":
                return Integer.parseInt(response.substring(4, 8), 16) / 4.0f;
            case "0e":
                return (Integer.parseInt(response.substring(4, 6), 16) - 128) / 2.0f;
            case "10":
                return Integer.parseInt(response.substring(4, 8), 16) / 100.0f;
            case "14_1":
            case "15_1":
            case "16_1":
            case "17_1":
            case "18_1":
            case "19_1":
            case "1a_1":
            case "1b_1":
                return Integer.parseInt(response.substring(4, 6), 16) / 200.0f;
            case "14_2":
            case "15_2":
            case "16_2":
            case "17_2":
            case "18_2":
            case "19_2":
            case "1a_2":
            case "1b_2":
                return (Integer.parseInt(response.substring(6, 8), 16) - 128) * 100 / 128.0f;
            case "21":
                return Integer.parseInt(response.substring(4, 8), 16);

            default:
                return 0;
        }
    }
}
