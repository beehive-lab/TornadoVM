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

import java.util.function.Predicate;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;

/**
 * One self-check: a kernel run on the device and compared with the JVM.
 *
 * @param group       {@code backend}, {@code core}, {@code types}, {@code java}, {@code strict-fp} or {@code tensor-cores} / {@code device}
 * @param name        short name, unique within the group
 * @param description what a failure means, in one line
 * @param needs       the capability the check needs; a device without it skips the check
 * @param requirement that capability in words, printed for a skip
 * @param body        runs the check; returns a detail for the report, throws {@link AssertionError} on a mismatch
 */
public record Check(String group, String name, String description, Predicate<TargetCaps> needs, String requirement, Body body) {

    /** Runs the check on {@code device}, whose capabilities are {@code caps}. */
    @FunctionalInterface
    public interface Body {
        String run(TornadoDevice device, TargetCaps caps) throws Exception;
    }

    /** A check every device runs. */
    static Check of(String group, String name, String description, Body body) {
        return new Check(group, name, description, caps -> true, "", body);
    }

    String id() {
        return group + "/" + name;
    }
}
