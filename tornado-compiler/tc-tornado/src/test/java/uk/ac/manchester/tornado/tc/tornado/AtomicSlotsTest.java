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
package uk.ac.manchester.tornado.tc.tornado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.IrPrinter;

/** AtomicInteger counters become slots of the atomics region, in the runtime's registration format. */
public class AtomicSlotsTest {

    @Test
    public void createdAndArgumentCountersGetSlots() {
        var function = IrParser.parseFunction("""
                kernel @K.count(%out: array<i32>, %ai: obj<java.util.concurrent.atomic.AtomicInteger>) -> void {
                ^entry:
                  %v = const 200 : i32
                  %local = tornado.atomic_int.new(%v) : obj<java.util.concurrent.atomic.AtomicInteger>
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
        assertTrue(!text.contains("atomic_int.new"), text);
        assertTrue(!function.params().get(1).hasUses(), text);
    }
}
