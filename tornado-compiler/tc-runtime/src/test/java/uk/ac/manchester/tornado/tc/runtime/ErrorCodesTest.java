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
package uk.ac.manchester.tornado.tc.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.tc.ir.ErrorCodes;

/** Every TC-Exxxx code in the compiler and the runtime hook has a category and an entry in ErrorCodes. */
public class ErrorCodesTest {

    @Test
    public void everyCodeIsRegistered() throws IOException {
        var code = Pattern.compile("\"(TC-E\\d{4})\"");
        var missing = new TreeSet<String>();
        for (var root : new Path[] { Path.of(".."), Path.of("../../tornado-drivers/drivers-common/src/main/java") }) {
            try (var files = Files.walk(root)) {
                for (var file : files.filter(f -> f.toString().endsWith(".java") && f.toString().contains("src/main/java")).toList()) {
                    var m = code.matcher(Files.readString(file));
                    while (m.find()) {
                        if (ErrorCodes.lookup(m.group(1)).isEmpty()) {
                            missing.add(m.group(1) + " (" + file.getFileName() + ")");
                        }
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "codes without an ErrorCodes entry: " + missing);
    }

    @Test
    public void userFacingCodesHaveHints() {
        for (var c : ErrorCodes.codes()) {
            if (ErrorCodes.category(c) != ErrorCodes.Category.INTERNAL) {
                assertTrue(!ErrorCodes.hint(c).isBlank(), c + " has no hint");
            }
        }
    }
}
