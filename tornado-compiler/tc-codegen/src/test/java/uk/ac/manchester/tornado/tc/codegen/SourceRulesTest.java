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
package uk.ac.manchester.tornado.tc.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.tc.check.SourceRules;

/** Clean-room guard: tc-codegen may not import Graal, JVMCI or ASM, and every file carries the licence. */
public class SourceRulesTest {

    @Test
    public void sourcesFollowTheCleanRoomRules() {
        var violations = SourceRules.check(Path.of("src"));
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }
}
