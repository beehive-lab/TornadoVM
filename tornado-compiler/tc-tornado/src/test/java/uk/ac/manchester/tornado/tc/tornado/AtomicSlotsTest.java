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
package uk.ac.manchester.tornado.tc.tornado;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.IrPrinter;

/** AtomicInteger counters become slots of the atomics region, in the runtime's registration format. */
public class AtomicSlotsTest {

    @Test
    public void createdAndArgumentCountersGetSlots() {
        var function = IrParser.parseFunction("""
                kernel @K.count(%out: array<i32>, %ai: obj<AtomicInteger>) -> void {
                ^entry:
                  %v = const 200 : i32
                  %local = tornado.atomic_int.new(%v) : obj<AtomicInteger>
                  %one = const 1 : i32
                  %a = tornado.atomic_int.add_get(%local, %one) : i32
                  %b = tornado.atomic_int.get_add(%ai, %one) : i32
                  %zero = const 0 : i32
                  array.store %out[%zero], %a
                  array.store %out[%one], %b
                  ret
                }
                """);
        assertTrue(AtomicSlots.run(function).isEmpty());
        var info = AtomicSlots.info(function);
        assertEquals(List.of(-1, 200), info.initial());      // the argument's slot first, filled by the runtime
        assertEquals(Map.of(1, 0), info.params());           // argument 1 -> slot 0
        var text = new IrPrinter(false).printFunction(function);
        assertTrue(text, !text.contains("atomic_int.new"));
        assertTrue(text, !function.params().get(1).hasUses());
    }
}
