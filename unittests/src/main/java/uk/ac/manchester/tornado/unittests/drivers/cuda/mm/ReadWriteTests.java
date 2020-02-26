package uk.ac.manchester.tornado.unittests.drivers.cuda.mm;

import org.junit.BeforeClass;
import org.junit.Test;
import uk.ac.manchester.tornado.api.exceptions.TornadoMemoryException;
import uk.ac.manchester.tornado.api.exceptions.TornadoOutOfMemoryException;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDADriver;
import uk.ac.manchester.tornado.drivers.cuda.mm.*;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class ReadWriteTests extends TornadoTestBase {

    private final int NUM_ELEMENTS = 8;

    private static CUDADeviceContext context;

    @BeforeClass
    public static void getDeviceContext() {
        CUDADriver driver = TornadoCoreRuntime.getTornadoRuntime().getDriver(CUDADriver.class);
        context = (CUDADeviceContext) driver.getDefaultDevice().getDeviceContext();
    }

    @Test
    public void testByteBufferReadWrite() {
        byte[] a = new byte[NUM_ELEMENTS];
        byte[] b = new byte[NUM_ELEMENTS];

        Arrays.fill(a, (byte) 1);
        Arrays.fill(b, (byte) 2);

        CUDAByteBuffer cudaBuffer = new CUDAByteBuffer(NUM_ELEMENTS, 0, context);
        cudaBuffer.buffer().put(a);
        cudaBuffer.write();

        CUDAByteBuffer otherCudaBuffer = new CUDAByteBuffer(NUM_ELEMENTS, 0, context);
        otherCudaBuffer.buffer().put(b);
        otherCudaBuffer.read();

        for (int i = 0; i < NUM_ELEMENTS; i++) {
            assertEquals(1, otherCudaBuffer.buffer().array()[i]);
        }
    }

    @Test
    public void testByteArrayReadWrite() {
        byte[] a = new byte[NUM_ELEMENTS];
        byte[] b = new byte[NUM_ELEMENTS];

        Arrays.fill(a, (byte) 1);
        Arrays.fill(b, (byte) 2);

        CUDAByteArrayWrapper arrayWrapper = new CUDAByteArrayWrapper(context);

        try {
            arrayWrapper.allocate(a, 0);
        } catch (TornadoOutOfMemoryException | TornadoMemoryException e) {
            e.printStackTrace();
        }

        arrayWrapper.enqueueWrite(a, 0, 0, null, false);
        arrayWrapper.enqueueRead(b, 0, null, false);

        for (int i = 0; i < NUM_ELEMENTS; i++) {
            assertEquals(1, b[i]);
        }
    }

    @Test
    public void testCharArrayReadWrite() {
        char[] a = new char[NUM_ELEMENTS];
        char[] b = new char[NUM_ELEMENTS];

        Arrays.fill(a, (char) 1);
        Arrays.fill(b, (char) 2);

        CUDACharArrayWrapper arrayWrapper = new CUDACharArrayWrapper(context);

        try {
            arrayWrapper.allocate(a, 0);
        } catch (TornadoOutOfMemoryException | TornadoMemoryException e) {
            e.printStackTrace();
        }

        arrayWrapper.enqueueWrite(a, 0, 0, null, false);
        arrayWrapper.enqueueRead(b, 0, null, false);

        for (int i = 0; i < NUM_ELEMENTS; i++) {
            assertEquals(1, b[i]);
        }
    }

    @Test
    public void testIntArrayReadWrite() {
        int[] a = new int[NUM_ELEMENTS];
        int[] b = new int[NUM_ELEMENTS];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        CUDAIntArrayWrapper arrayWrapper = new CUDAIntArrayWrapper(context);

        try {
            arrayWrapper.allocate(a, 0);
        } catch (TornadoOutOfMemoryException | TornadoMemoryException e) {
            e.printStackTrace();
        }

        arrayWrapper.enqueueWrite(a, 0, 0, null, false);
        arrayWrapper.enqueueRead(b, 0, null, false);

        for (int i = 0; i < NUM_ELEMENTS; i++) {
            assertEquals(1, b[i]);
        }
    }

    @Test
    public void testLongArrayReadWrite() {
        long[] a = new long[NUM_ELEMENTS];
        long[] b = new long[NUM_ELEMENTS];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);

        CUDALongArrayWrapper arrayWrapper = new CUDALongArrayWrapper(context);

        try {
            arrayWrapper.allocate(a, 0);
        } catch (TornadoOutOfMemoryException | TornadoMemoryException e) {
            e.printStackTrace();
        }

        arrayWrapper.enqueueWrite(a, 0, 0, null, false);
        arrayWrapper.enqueueRead(b, 0, null, false);

        for (int i = 0; i < NUM_ELEMENTS; i++) {
            assertEquals(1, b[i]);
        }
    }

    @Test
    public void testFloatArrayReadWrite() {
        float[] a = new float[NUM_ELEMENTS];
        float[] b = new float[NUM_ELEMENTS];

        Arrays.fill(a, (float) 1.0);
        Arrays.fill(b, (float) 2.0);

        CUDAFloatArrayWrapper arrayWrapper = new CUDAFloatArrayWrapper(context);

        try {
            arrayWrapper.allocate(a, 0);
        } catch (TornadoOutOfMemoryException | TornadoMemoryException e) {
            e.printStackTrace();
        }

        arrayWrapper.enqueueWrite(a, 0, 0, null, false);
        arrayWrapper.enqueueRead(b, 0, null, false);

        for (int i = 0; i < NUM_ELEMENTS; i++) {
            assertEquals(1, b[i], 0.1);
        }
    }

    @Test
    public void testDoubleArrayReadWrite() {
        double[] a = new double[NUM_ELEMENTS];
        double[] b = new double[NUM_ELEMENTS];

        Arrays.fill(a, (double) 1.0);
        Arrays.fill(b, (double) 2.0);

        CUDADoubleArrayWrapper arrayWrapper = new CUDADoubleArrayWrapper(context);

        try {
            arrayWrapper.allocate(a, 0);
        } catch (TornadoOutOfMemoryException | TornadoMemoryException e) {
            e.printStackTrace();
        }

        arrayWrapper.enqueueWrite(a, 0, 0, null, false);
        arrayWrapper.enqueueRead(b, 0, null, false);

        for (int i = 0; i < NUM_ELEMENTS; i++) {
            assertEquals(1, b[i], 0.1);
        }
    }
}
