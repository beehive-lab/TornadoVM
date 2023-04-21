/*
 * Copyright (c) 2023, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.examples;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.data.nativetypes.DoubleArray;
import uk.ac.manchester.tornado.api.data.nativetypes.FloatArray;
import uk.ac.manchester.tornado.api.data.nativetypes.IntArray;
import uk.ac.manchester.tornado.api.data.nativetypes.LongArray;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;

public class NativeTypesTest {

    private static final int ARRAY_SIZE = 16384;
    private static final boolean VALIDATE_RESULTS = Boolean.parseBoolean(System.getProperty("validate", "False"));

    public static void addInt(IntArray intIn, IntArray intOut) {
        for (@Parallel int i = 0; i < intIn.getSize(); i++) {
            intOut.set(i, intIn.get(i) + intOut.get(i));
        }
    }

    public static void addFloat(FloatArray floatIn, FloatArray floatOut) {
        for (@Parallel int i = 0; i < ARRAY_SIZE; i++) {
            floatOut.set(i, floatIn.get(i) + floatOut.get(i));
        }
    }

    public static void addDouble(DoubleArray doubleIn, DoubleArray doubleOut) {
        for (@Parallel int i = 0; i < ARRAY_SIZE; i++) {
            doubleOut.set(i, doubleIn.get(i) + doubleOut.get(i));
        }
    }

    public static void addLong(LongArray longIn, LongArray longOut) {
        for (@Parallel int i = 0; i < ARRAY_SIZE; i++) {
            longOut.set(i, longIn.get(i) + longOut.get(i));
        }
    }

    public static void main(String[] args) {
       // === Test IntArray
        IntArray intIn = new IntArray(ARRAY_SIZE);
        IntArray intOut = new IntArray(ARRAY_SIZE);

        intIn.init(2);
        intOut.init(1);

        TaskGraph tsInt = new TaskGraph("s0")
                .task("t0", NativeTypesTest::addInt, intIn, intOut)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, intOut);

        ImmutableTaskGraph immutableTaskGraphInt = tsInt.snapshot();

        TornadoExecutionPlan tornadoExecutorInt = new TornadoExecutionPlan(immutableTaskGraphInt);

        tornadoExecutorInt.execute();

//        // === Test FloatArray
        FloatArray floatIn = new FloatArray(ARRAY_SIZE);
        FloatArray floatOut = new FloatArray(ARRAY_SIZE);

        floatIn.init(2f);
        floatOut.init(1f);

        TaskGraph tsFloat = new TaskGraph("s1")
                .task("t1", NativeTypesTest::addFloat, floatIn, floatOut)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, floatOut);

        ImmutableTaskGraph immutableTaskGraphFloat = tsFloat.snapshot();

        TornadoExecutionPlan tornadoExecutorFloat = new TornadoExecutionPlan(immutableTaskGraphFloat);

        tornadoExecutorFloat.execute();

        // === Test DoubleArray
        DoubleArray doubleIn = new DoubleArray(ARRAY_SIZE);
        DoubleArray doubleOut = new DoubleArray(ARRAY_SIZE);

        doubleIn.init(2.0);
        doubleOut.init(1.0);

        TaskGraph tsDouble = new TaskGraph("s2")
                .task("t2", NativeTypesTest::addDouble, doubleIn, doubleOut)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, doubleOut);

        ImmutableTaskGraph immutableTaskGraphDouble = tsDouble.snapshot();

        TornadoExecutionPlan tornadoExecutorDouble = new TornadoExecutionPlan(immutableTaskGraphDouble);

        tornadoExecutorDouble.execute();

        // === Test LongArray
        LongArray longIn = new LongArray(ARRAY_SIZE);
        LongArray longOut = new LongArray(ARRAY_SIZE);

        longIn.init(2L);
        longOut.init(1L);

        TaskGraph tsLong = new TaskGraph("s3")
                .task("t3", NativeTypesTest::addLong, longIn, longOut)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, longOut);

        ImmutableTaskGraph immutableTaskGraphLong = tsLong.snapshot();

        TornadoExecutionPlan tornadoExecutorLong = new TornadoExecutionPlan(immutableTaskGraphLong);

        tornadoExecutorLong.execute();

        if (VALIDATE_RESULTS) {
            System.out.println("=== Validating results ===");
            // validate int array
            IntArray intInSerial = new IntArray(ARRAY_SIZE);
            IntArray intOutSerial = new IntArray(ARRAY_SIZE);
            intInSerial.init(2);
            intOutSerial.init(1);
            addInt(intInSerial, intOutSerial);
            boolean resultInt = validateResultsInt(intOut, intOutSerial);
            if (resultInt) {
                System.out.println("The results of addInt are correct.");
            }
            // validate float array
            FloatArray floatInSerial = new FloatArray(ARRAY_SIZE);
            FloatArray floatOutSerial = new FloatArray(ARRAY_SIZE);
            floatInSerial.init(2f);
            floatOutSerial.init(1f);
            addFloat(floatInSerial, floatOutSerial);
            boolean resultFloat = validateResultsFloat(floatOut, floatOutSerial);
            if (resultFloat) {
                System.out.println("The results of addFloat are correct.");
            }
            // validate double array
            DoubleArray doubleInSerial = new DoubleArray(ARRAY_SIZE);
            DoubleArray doubleOutSerial = new DoubleArray(ARRAY_SIZE);
            doubleInSerial.init(2.0);
            doubleOutSerial.init(1.0);
            addDouble(doubleInSerial, doubleOutSerial);
            boolean resultDouble = validateResultsDouble(doubleOut, doubleOutSerial);
            if (resultDouble) {
                System.out.println("The results of addDouble are correct.");
            }
            // validate long array
            LongArray longInSerial = new LongArray(ARRAY_SIZE);
            LongArray longOutSerial = new LongArray(ARRAY_SIZE);
            longInSerial.init(2L);
            longOutSerial.init(1L);
            addLong(longInSerial, longOutSerial);
            boolean resultLong = validateResultsLong(longOut, longOutSerial);
            if (resultLong) {
                System.out.println("The results of addLong are correct.");
            }
        }
    }

    private static boolean validateResultsInt(IntArray tornadoOut, IntArray serialOut) {
        for (int i = 0; i < ARRAY_SIZE; i++) {
            int expect = tornadoOut.get(i);
            int actual = serialOut.get(i);

            if (actual != expect) {
                System.out.println("Wrong result index " + i + " expect " + expect + " actual " + actual + ".");
                return false;
            }
        }
        return true;
    }

    private static boolean validateResultsFloat(FloatArray tornadoOut, FloatArray serialOut) {
        for (int i = 0; i < ARRAY_SIZE; i++) {
            float expect = tornadoOut.get(i);
            float actual = serialOut.get(i);

            if (actual != expect) {
                System.out.println("Wrong result index " + i + " expect " + expect + " actual " + actual + ".");
                return false;
            }
        }
        return true;
    }

    private static boolean validateResultsDouble(DoubleArray tornadoOut, DoubleArray serialOut) {
        for (int i = 0; i < ARRAY_SIZE; i++) {
            double expect = tornadoOut.get(i);
            double actual = serialOut.get(i);

            if (actual != expect) {
                System.out.println("Wrong result index " + i + " expect " + expect + " actual " + actual + ".");
                return false;
            }
        }
        return true;
    }

    private static boolean validateResultsLong(LongArray tornadoOut, LongArray serialOut) {
        for (int i = 0; i < ARRAY_SIZE; i++) {
            long expect = tornadoOut.get(i);
            long actual = serialOut.get(i);

            if (actual != expect) {
                System.out.println("Wrong result index " + i + " expect " + expect + " actual " + actual + ".");
                return false;
            }
        }
        return true;
    }

}
