package uk.ac.manchester.tornado.drivers.spirv.levelzero;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

public class SPIRVUtils {

    public static byte[] readSPIRVbinary(String filename) throws IOException {
        ArrayList<Byte> byteFile = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(filename)) {
            byte b;
            while ((b = (byte) fis.read()) != -1) {
                byteFile.add(b);
            }
        }

        byte[] byteArray = new byte[byteFile.size()];
        for (int i = 0; i < byteArray.length; i++) {
            byteArray[i] = byteFile.get(i);
        }
        return byteArray;
    }
}
