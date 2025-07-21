package uk.ac.manchester.tornado.unittests.quantization;

import org.junit.Test;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.utils.QuantizationUtils;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * How to test?
 *
 * <p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.quantization.QuantizationTests
 * </code>
 * </p>
 */
public class QuantizationTests extends TornadoTestBase {
    // CHECKSTYLE:OFF

    public static void performDP4A(Int8Array a, Int8Array b, IntArray result) {
        for (@Parallel int i = 0; i < result.getSize(); i++) {
            int dot = QuantizationUtils.dp4a(a, b, 0, i * 4);
            result.set(i, dot);
        }
    }

    @Test
    public void testDP4A() throws TornadoExecutionPlanException {
        int N = 512;
        Int8Array a = new Int8Array(N);
        Int8Array b = new Int8Array(N);
        IntArray result = new IntArray(N / 4);
        IntArray resultSeq = new IntArray(N / 4);

        Random r = new Random();
        IntStream.range(0, N).sequential().forEach(i -> {
           a.set(i, (byte) r.nextInt());
           b.set(i, (byte) r.nextInt());
        });

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
                .task("t0", QuantizationTests::performDP4A, a, b, result)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan tornadoExecutor = new TornadoExecutionPlan(immutableTaskGraph)) {
            tornadoExecutor.execute();
        }

        performDP4A(a, b, resultSeq);

        for (int i = 0; i < result.getSize(); i++) {
            assertEquals(resultSeq.get(i), result.get(i), 0.0001);
        }
    }

    // CHECKSTYLE:ON
}
