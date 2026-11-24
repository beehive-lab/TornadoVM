/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
 */
package uk.ac.manchester.tornado.tc.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;

public class ConstantTablesTest {

    /** A table javac builds the usual way. */
    static final class Plain {
        static final int[] TABLE = { 1, -2, Integer.MIN_VALUE, 2_000_000 };
        static final double[] HALVES = { 0.5, -0.0 };
    }

    /** A table changed by the initialiser after it is assigned: not a constant. */
    static final class Changed {
        static final int[] TABLE = { 1, 2, 3 };

        static {
            TABLE[0] = 5;
        }
    }

    /** A table computed in a loop: not recognised. */
    static final class Computed {
        static final int[] TABLE = new int[4];

        static {
            for (int i = 0; i < TABLE.length; i++) {
                TABLE[i] = i * i;
            }
        }
    }

    private static ClassFile classFile(Class<?> c) throws IOException {
        try (var in = c.getResourceAsStream("/" + c.getName().replace('.', '/') + ".class")) {
            return ClassFile.parse(in.readAllBytes());
        }
    }

    @Test
    public void readsTablesJavacBuilds() throws IOException {
        var table = ConstantTables.read(classFile(Plain.class), "TABLE").orElseThrow();
        assertEquals(10, table.newarrayType());
        assertEquals(List.of(1, -2, Integer.MIN_VALUE, 2_000_000), table.values().stream().map(Number::intValue).toList());
        var halves = ConstantTables.read(classFile(Plain.class), "HALVES").orElseThrow();
        assertEquals(7, halves.newarrayType());
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(halves.values().get(1).doubleValue()));
    }

    @Test
    public void refusesTablesTheInitialiserChangesOrComputes() throws IOException {
        assertTrue(ConstantTables.read(classFile(Changed.class), "TABLE").isEmpty());
        assertTrue(ConstantTables.read(classFile(Computed.class), "TABLE").isEmpty());
    }

    @Test
    public void onlyTheJdksFdLibmIsTrusted() {
        Frontend.BytecodeSource source = name -> Frontend.BytecodeSource.jrt(name);
        // FdLibm's atan: atanhi = {atan(0.5), pi/4, atan(1.5), pi/2}, the fdlibm constants.
        //
        // Not Exp's ln2HI: that was {ln2_hi, -ln2_hi} up to JDK 25 and is a plain double from 26 on, so a
        // test written against it passes or fails by which JDK runs it. Atan's tables are arrays on every
        // JDK this builds for, and hold the same bits on each.
        var atanhi = source.staticFinalArray("java/lang/FdLibm$Atan", "atanhi").orElseThrow();
        assertEquals(List.of(0x3fddac670561bb4fL, 0x3fe921fb54442d18L, 0x3fef730bd281f69bL, 0x3ff921fb54442d18L),
                atanhi.values().stream().map(v -> Double.doubleToRawLongBits(v.doubleValue())).toList());
        // A user class could store into its final array anywhere: its tables are not taken as constants.
        Frontend.BytecodeSource own = name -> {
            try (var in = ConstantTablesTest.class.getResourceAsStream("/" + name + ".class")) {
                return in == null ? java.util.Optional.empty() : java.util.Optional.of(in.readAllBytes());
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        };
        assertTrue(own.staticFinalArray(Plain.class.getName().replace('.', '/'), "TABLE").isEmpty());
    }
}
