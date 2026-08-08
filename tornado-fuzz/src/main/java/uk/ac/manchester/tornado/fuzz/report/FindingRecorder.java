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
package uk.ac.manchester.tornado.fuzz.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import uk.ac.manchester.tornado.fuzz.CaseResult;
import uk.ac.manchester.tornado.fuzz.FuzzConfig;

/**
 * Appends a one-line JSON summary per finding to {@code findings.jsonl} in the
 * output directory. Every line is replayable via {@code --params seed=<seed>}.
 */
public final class FindingRecorder {

    private final Path jsonl;

    public FindingRecorder(Path outDir) throws IOException {
        Files.createDirectories(outDir);
        this.jsonl = outDir.resolve("findings.jsonl");
    }

    public synchronized void record(FuzzConfig cfg, CaseResult result) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"seed\":").append(cfg.seed);
        sb.append(",\"kind\":\"").append(result.status).append('"');
        sb.append(",\"template\":\"").append(cfg.templateId).append('"');
        if (result.diff != null) {
            sb.append(",\"firstBadIndex\":").append(result.diff.firstBadIndex);
            sb.append(",\"mismatchCount\":").append(result.diff.mismatchCount);
            sb.append(",\"expected\":\"").append(escape(result.diff.expected)).append('"');
            sb.append(",\"actual\":\"").append(escape(result.diff.actual)).append('"');
        }
        if (result.error != null) {
            sb.append(",\"exceptionClass\":\"").append(result.error.getClass().getName()).append('"');
            sb.append(",\"exceptionMessage\":\"").append(escape(String.valueOf(result.error.getMessage()))).append('"');
        }
        sb.append(",\"config\":").append(cfg.toJson());
        sb.append("}\n");
        Files.writeString(jsonl, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String escape(String s) {
        if (s == null) {
            return "null";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t");
    }
}
