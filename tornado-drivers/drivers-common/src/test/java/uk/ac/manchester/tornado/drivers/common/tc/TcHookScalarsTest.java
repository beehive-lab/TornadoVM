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
package uk.ac.manchester.tornado.drivers.common.tc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.runtime.TcOptions;
import uk.ac.manchester.tornado.tc.tornado.ValueSpecialisation.Fact;

/** Whether a task is compiled specialised on its scalars must not depend on which thread asks first. */
public class TcHookScalarsTest {

    public static void kernel(int n) {
    }

    private static TcHook.Key key(int value) throws Exception {
        Method method = TcHookScalarsTest.class.getMethod("kernel", int.class);
        return new TcHook.Key(method, TargetCaps.CUDA_SM89, TcOptions.DEFAULTS, List.of(new Fact.Scalar(value)));
    }

    @Test
    public void aTaskKeepsTheDecisionMadeInPlanOrder() throws Exception {
        var scalars = new TcHook.Scalars();
        // The plan's prefetch decides in task order on the dispatching thread.
        var inOrder = new ArrayList<BitSet>();
        for (int v = 1; v <= 5; v++) {
            inOrder.add(scalars.decide(key(v)));
        }
        assertTrue(inOrder.get(0).isEmpty() && inOrder.get(1).isEmpty(), "the first two values are specialised");
        assertTrue(inOrder.get(2).get(0) && inOrder.get(4).get(0), "from the third value on, the scalar is a runtime value");
        // The background compilations and the in-order ones then ask again, in any order, on any thread.
        try (var pool = Executors.newFixedThreadPool(8)) {
            var again = new ArrayList<CompletableFuture<BitSet>>();
            for (int round = 0; round < 4; round++) {
                for (int v = 5; v >= 1; v--) {
                    int value = v;
                    again.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            return scalars.decide(key(value));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, pool));
                }
            }
            for (int i = 0; i < again.size(); i++) {
                int value = 5 - i % 5;
                assertEquals(inOrder.get(value - 1), again.get(i).join(), "value " + value);
            }
        }
    }
}
