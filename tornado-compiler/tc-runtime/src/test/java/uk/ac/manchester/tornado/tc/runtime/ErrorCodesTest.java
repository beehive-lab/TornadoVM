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
package uk.ac.manchester.tornado.tc.runtime;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.Test;

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
        assertTrue("codes without an ErrorCodes entry: " + missing, missing.isEmpty());
    }

    @Test
    public void userFacingCodesHaveHints() {
        for (var c : ErrorCodes.codes()) {
            if (ErrorCodes.category(c) != ErrorCodes.Category.INTERNAL) {
                assertTrue(c + " has no hint", !ErrorCodes.hint(c).isBlank());
            }
        }
    }
}
