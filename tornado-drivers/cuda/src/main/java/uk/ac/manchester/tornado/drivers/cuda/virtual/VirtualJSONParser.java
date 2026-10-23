/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package uk.ac.manchester.tornado.drivers.cuda.virtual;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.cuda.enums.CUDADeviceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.VIRTUAL_DEVICE_FILE;

public class VirtualJSONParser {

    private static final Pattern pattern =  Pattern.compile(" |\",|\"|\t|\\r|]|\\[");

    private enum JsonKey {
        deviceName,
        doubleFPSupport,
        maxWorkItemSizes,
        deviceAddressBits,
        deviceType,
        deviceExtensions,
        availableProcessors;
    }

    public static VirtualDeviceDescriptor getDeviceDescriptor() {
        String json = readVirtualDeviceJson().replace("\r", "");
        HashMap<JsonKey, String> jsonEntries = new HashMap<>();
        for (String line : json.split("\n")) {
            Matcher matcher = pattern.matcher(line);
            String[] keyValue = matcher.replaceAll("").split(":");
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
        CUDADeviceType deviceType = (CUDADeviceType) getEntryForKey(JsonKey.deviceType, jsonEntries);
        String deviceExtensions = (String) getEntryForKey(JsonKey.deviceExtensions, jsonEntries);
        int availableProcessors = (int) getEntryForKey(JsonKey.availableProcessors, jsonEntries);

        return new VirtualDeviceDescriptor(deviceName, doubleFPSupport, maxWorkItemSizes, deviceAddressBits, deviceType, deviceExtensions, availableProcessors);
    }

    private static Object getEntryForKey(JsonKey jsonKey, Map<JsonKey, String> jsonEntries) {
        switch (jsonKey) {
            case deviceName:
            case deviceExtensions:
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
            case availableProcessors:
                return Integer.parseInt(jsonEntries.get(jsonKey));
            case deviceType:
                return CUDADeviceType.valueOf(jsonEntries.get(jsonKey));
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
