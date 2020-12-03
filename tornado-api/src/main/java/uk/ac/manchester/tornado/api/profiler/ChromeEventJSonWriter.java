
/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 * Author Gary Frost
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
            object("args", () -> {
                kv("name", "Tornado");
            });
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
