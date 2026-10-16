/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
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
