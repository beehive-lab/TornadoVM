/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.tc.selftest;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** The self-check's report as JSON (no library: a few strings, numbers and lists). */
final class Json {

    private Json() {
    }

    static String of(List<SelfTest.DeviceReport> reports) {
        return "{\n  \"tool\": \"tornado-selftest\",\n  \"java\": " + str(System.getProperty("java.version")) + ",\n  \"os\": "
                + str(System.getProperty("os.name") + " " + System.getProperty("os.arch")) + ",\n  \"devices\": [\n"
                + reports.stream().map(Json::device).collect(Collectors.joining(",\n")) + "\n  ]\n}\n";
    }

    private static String device(SelfTest.DeviceReport r) {
        var caps = r.caps();
        var capsJson = caps == null ? "null"
                : String.format(Locale.ROOT,
                        "{\"dialect\": %s, \"computeCapability\": %d, \"nvrtcVersion\": %d, \"fp64\": %b, \"fp16\": %b, \"warpSize\": %d, "
                                + "\"localMemoryBytes\": %d, \"maxWorkGroupSize\": %d, \"mma\": %b, \"fp8Mma\": %b, \"deviceLaunch\": %b}",
                        str(caps.dialect().name()), caps.computeCapability(), caps.nvrtcVersion(), caps.fp64(), caps.fp16(), caps.warpSize(), caps.localMemoryBytes(), caps.maxWorkGroupSize(),
                        caps.mma(), caps.fp8Mma(), caps.deviceLaunch());
        var results = r.results().stream()
                .map(x -> String.format(Locale.ROOT, "        {\"check\": %s, \"status\": %s, \"ms\": %.1f, \"detail\": %s}", str(x.check().id()), str(x.status().name()), x.millis(), str(x.detail())))
                .collect(Collectors.joining(",\n"));
        return "    {\"device\": " + str(r.id()) + ", \"name\": " + str(r.name()) + ", \"target\": " + capsJson + ",\n      \"results\": [\n" + results + "\n      ]}";
    }

    private static String str(String s) {
        if (s == null) {
            return "null";
        }
        var sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
