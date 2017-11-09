package ht.albrec.runningdata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Util {
    public static String readInput(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }

        byte[] buffer = new byte[256];
        int read;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((read = is.read(buffer)) > 0) {
            baos.write(buffer, 0, read);
        }
        byte[] result = baos.toByteArray();
        return result.length == 0 ? null : new String(result, "UTF-8");
    }
}
