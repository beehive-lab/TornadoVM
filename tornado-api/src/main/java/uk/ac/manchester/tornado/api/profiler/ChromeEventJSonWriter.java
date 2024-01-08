/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.profiler;

import java.io.File;

public class ChromeEventJSonWriter extends JSonWriter<ChromeEventJSonWriter> {
    ContentWriter NO_ARGS = null;

    ChromeEventJSonWriter() {
        super();
        objectStart();
        arrayStart("traceEvents");
        object(() -> {
            object("args", () -> kv("name", "Tornado"));
            kv("ph", "M");
            pidAndTid();
            kv("name", "tornadovm");
            kv("sort_index", 1);
        });
    }

    JSonWriter pidAndTid() {
        return kv("pid", 0).kv("tid", Thread.currentThread().getId());
    }

    JSonWriter common(String phase, String name, String category) {
        return kv("ph", phase).kv("name", name).kv("cat", category).pidAndTid();
    }

    public JSonWriter x(String name, String category, long startNs, long endNs, ContentWriter cw) {
        return compact().object(() -> {
            common("X", name, category);
            ns("ts", startNs);
            nsd("dur", endNs - startNs);
            if (cw != NO_ARGS) {
                object("args", () -> {
                    nonCompact();
                    cw.write();
                });
            } else {
                nonCompact();
            }
        });
    }

    JSonWriter b(String name, String category, long startNs) {
        return common("B", name, category).ns("ts", startNs);
    }

    JSonWriter e(String name, long durationNs) {
        return kv("ph", "E").kv("name", name).pidAndTid().ns("ts", durationNs);
    }

    @Override
    void write(File file) {
        arrayEnd().objectEnd();
        super.write(file);
    }
}
