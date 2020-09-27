package uk.ac.manchester.tornado.drivers.opencl.virtual;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.VIRTUAL_DEVICE_FILE;

public class VirtualJSONParser {

    private enum JsonKey {
        deviceName,
        doubleFPSupport,
        maxWorkItemSizes,
        deviceAddressBits,
        deviceType
        ;

        JsonKey() {
        }
    }

    public static VirtualDeviceDescriptor getDeviceDescriptor() {
        String json = readVirtualDeviceJson();
        HashMap<JsonKey, String> jsonEntries = new HashMap<>();
        for (String line : json.split("\n")) {
            String[] keyValue = line.replaceAll(" |\",|\"|\t|]|\\[", "").split(":");
            String key = keyValue[0];
            String value = keyValue.length > 1 ? keyValue[1] : null;
            if (value != null) {
                value = value.charAt(value.length() - 1) == ',' ? value.substring(0, value.length() - 1) : value;
                jsonEntries.put(JsonKey.valueOf(key), value);
            }
        }

        String deviceName = (String) getEntryForKey(JsonKey.deviceName, jsonEntries);
        boolean doubleFPSupport = (boolean) getEntryForKey(JsonKey.doubleFPSupport, jsonEntries);
        long[] maxWorkItemSizes = (long[]) getEntryForKey(JsonKey.maxWorkItemSizes, jsonEntries);
        int deviceAddressBits = (int) getEntryForKey(JsonKey.deviceAddressBits, jsonEntries);
        OCLDeviceType deviceType = (OCLDeviceType) getEntryForKey(JsonKey.deviceType, jsonEntries);

        return new VirtualDeviceDescriptor(deviceName, doubleFPSupport, maxWorkItemSizes, deviceAddressBits, deviceType);
    }

    private static Object getEntryForKey(JsonKey jsonKey, Map<JsonKey, String> jsonEntries) {
        switch (jsonKey) {
            case deviceName:
                return jsonEntries.get(jsonKey);
            case doubleFPSupport:
                return Boolean.parseBoolean(jsonEntries.get(jsonKey));
            case maxWorkItemSizes:
                long[] values = new long[3];
                String[] numbers = jsonEntries.get(jsonKey).split(",");
                values[0] = Long.parseLong(numbers[0]);
                values[1] = Long.parseLong(numbers[1]);
                values[2] = Long.parseLong(numbers[2]);
                return values;
            case deviceAddressBits:
                return Integer.parseInt(jsonEntries.get(jsonKey));
            case deviceType:
                return OCLDeviceType.valueOf(jsonEntries.get(jsonKey));
        }
        throw new RuntimeException("Virtual device JSON parser failed ! Unknown json key: " + jsonKey.name());
    }

    private static String readVirtualDeviceJson() {
        Path path = Paths.get(VIRTUAL_DEVICE_FILE);
        TornadoInternalError.guarantee(path.toFile().exists(), "Virtual device file does not exist: %s", VIRTUAL_DEVICE_FILE);

        try {
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to read from %s", VIRTUAL_DEVICE_FILE), e);
        }
    }
}
