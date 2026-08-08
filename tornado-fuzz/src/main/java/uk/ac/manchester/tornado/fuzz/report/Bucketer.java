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

import java.util.ArrayList;
import java.util.List;

import uk.ac.manchester.tornado.fuzz.CaseResult;
import uk.ac.manchester.tornado.fuzz.FuzzConfig;

/**
 * Collapses many findings into a small number of root-cause buckets so a run's
 * output is triageable. Two findings share a bucket when they are the same kind
 * of defect: an exception is keyed by its class + normalized message; a mismatch
 * is keyed by the template and the set of fragile operators involved (for
 * generated kernels) or the specific op/intrinsic (for Phase 1 templates). Only
 * the first finding per bucket needs a full debug bundle.
 */
public final class Bucketer {

    /** Fragile constructs whose presence characterizes a generated-expression finding. */
    private static final String[] OP_TOKENS = { "(long)", "(int)", "<<", ">>", "/", "%", "*", "&", "|", "^", "~" };

    private Bucketer() {
    }

    public static String key(FuzzConfig cfg, CaseResult result) {
        return switch (result.status) {
            case EXCEPTION -> "EXC:" + (result.error == null ? "?" : result.error.getClass().getSimpleName()) + ":" + normalize(result.error == null ? "" : result.error.getMessage());
            case MISMATCH -> mismatchKey(cfg);
            default -> "PASS";
        };
    }

    private static String mismatchKey(FuzzConfig cfg) {
        String template = String.valueOf(cfg.templateId);
        if ("gen-expr".equals(template)) {
            String expr = String.valueOf(cfg.params.get("expr"));
            List<String> ops = new ArrayList<>();
            for (String tok : OP_TOKENS) {
                if (expr.contains(tok)) {
                    ops.add(tok);
                }
            }
            return "MM:gen-expr:" + String.join(",", ops);
        }
        Object detail = cfg.params.containsKey("op") ? cfg.params.get("op") : cfg.params.get("fn");
        return "MM:" + template + ":" + detail;
    }

    /** Strip generated class names and numbers so equivalent messages collapse. */
    private static String normalize(String message) {
        if (message == null) {
            return "";
        }
        String m = message.replaceAll("G\\d+", "G#").replaceAll("\\d+", "#");
        m = m.replaceAll("\\s+", " ").trim();
        return m.length() > 120 ? m.substring(0, 120) : m;
    }
}
