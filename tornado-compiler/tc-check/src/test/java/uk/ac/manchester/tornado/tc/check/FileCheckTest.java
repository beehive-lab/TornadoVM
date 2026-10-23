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
package uk.ac.manchester.tornado.tc.check;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class FileCheckTest {

    private static final String INPUT = """
            kernel @add(%a: i32, %b: i32) -> i32 {
            ^entry:
              %sum = add %a, %b : i32
              %twice = add %sum, %sum : i32
              ret %twice
            }

            device @helper() -> void {
            ^entry:
              ret
            }
            """;

    private static boolean passes(String checks) {
        var result = FileCheck.check("test", checks, INPUT);
        return result.passed();
    }

    private static void assertPasses(String checks) {
        var result = FileCheck.check("test", checks, INPUT);
        assertTrue(result.passed(), result.message());
    }

    @Test
    public void plainChecksMatchInOrder() {
        assertPasses("CHECK: kernel @add\nCHECK: ret %twice");
        assertFalse(passes("CHECK: ret %twice\nCHECK: kernel @add"));
    }

    @Test
    public void whitespaceIsCanonicalised() {
        assertPasses("CHECK: %sum   =   add %a, %b");
    }

    @Test
    public void nextMustBeOnTheFollowingLine() {
        assertPasses("CHECK: %sum = add\nCHECK-NEXT: %twice = add");
        assertFalse(passes("CHECK: %sum = add\nCHECK-NEXT: ret %twice"));
    }

    @Test
    public void sameMustBeOnTheSameLine() {
        assertPasses("CHECK: %sum = add\nCHECK-SAME: %a, %b : i32");
        assertFalse(passes("CHECK: %sum = add\nCHECK-SAME: %twice"));
    }

    @Test
    public void notIsCheckedBetweenMatches() {
        assertPasses("CHECK: %sum = add\nCHECK-NOT: mul\nCHECK: ret");
        assertFalse(passes("CHECK: kernel\nCHECK-NOT: %twice\nCHECK: ret"));
    }

    @Test
    public void trailingNotIsCheckedToTheEnd() {
        assertFalse(passes("CHECK: kernel\nCHECK-NOT: device"));
    }

    @Test
    public void dagMatchesInAnyOrder() {
        assertPasses("CHECK-DAG: %twice = add\nCHECK-DAG: %sum = add\nCHECK: ret %twice");
    }

    @Test
    public void labelsSplitTheInput() {
        assertPasses("CHECK-LABEL: kernel @add\nCHECK: ret %twice\nCHECK-LABEL: device @helper\nCHECK: ret");
        // The check after the first label must not escape into the second region.
        assertFalse(passes("CHECK-LABEL: kernel @add\nCHECK: ^entry:\nCHECK: ret\nCHECK: ^entry:\nCHECK-LABEL: device @helper"));
    }

    @Test
    public void regexBlocksAndVariables() {
        assertPasses("CHECK: %[[S:[a-z]+]] = add %a, %b\nCHECK: add %[[S]], %[[S]]");
        assertPasses("CHECK: %sum = {{(add|sub)}} %a");
        assertFalse(passes("CHECK: %[[S:[a-z]+]] = add %a, %b\nCHECK: ret %[[S]]"));
    }

    @Test
    public void emptyMatchesABlankLine() {
        assertPasses("CHECK: }\nCHECK-EMPTY:\nCHECK: device");
        assertFalse(passes("CHECK: ^entry:\nCHECK-EMPTY:"));
    }

    @Test
    public void customPrefixes() {
        var result = FileCheck.check("test", "OPT: %twice\nCHECK: this would fail", INPUT, List.of("OPT"));
        assertTrue(result.passed(), result.message());
    }

    @Test
    public void missingDirectivesIsAnError() {
        assertFalse(passes("nothing to check here"));
    }

    @Test
    public void failureMessagePointsAtTheCheckLine() {
        var result = FileCheck.check("t.tcir", "CHECK: kernel\nCHECK-NEXT: nope", INPUT);
        assertFalse(result.passed());
        assertTrue(result.message().startsWith("t.tcir:2: error: CHECK-NEXT:"), result.message());
    }
}
