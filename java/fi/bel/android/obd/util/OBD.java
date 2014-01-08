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
}
