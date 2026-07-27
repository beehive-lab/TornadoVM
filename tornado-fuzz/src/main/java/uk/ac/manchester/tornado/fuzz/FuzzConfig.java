/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.fuzz;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The fully-resolved description of a single fuzz case. Everything needed to
 * replay and to write into a finding bundle. Populated by the harness and the
 * chosen kernel template.
 */
public final class FuzzConfig {

    public final long seed;
    public final int phase;

    public String templateId;
    public ElemType elemType;
    public int size;
    public int localSize;
    public DataGen.Profile dataProfile;

    /** Template-specific choices (op, math function, etc.) recorded for the report. */
    public final Map<String, Object> params = new LinkedHashMap<>();

    public FuzzConfig(long seed, int phase) {
        this.seed = seed;
        this.phase = phase;
    }

    public void put(String key, Object value) {
        params.put(key, value);
    }

    /** Minimal hand-rolled JSON (no external deps on the harness classpath). */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"seed\":").append(seed);
        sb.append(",\"phase\":").append(phase);
        sb.append(",\"templateId\":").append(quote(templateId));
        sb.append(",\"elemType\":").append(quote(elemType == null ? null : elemType.name()));
        sb.append(",\"size\":").append(size);
        sb.append(",\"localSize\":").append(localSize);
        sb.append(",\"dataProfile\":").append(quote(dataProfile == null ? null : dataProfile.name()));
        sb.append(",\"params\":{");
        boolean first = true;
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(e.getKey())).append(':');
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append(quote(String.valueOf(v)));
            }
        }
        sb.append("}}");
        return sb.toString();
    }

    private static String quote(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
