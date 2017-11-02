package tornado.unitests.arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Ignore;
import org.junit.Test;

import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;

public class TestArrays {

	public static void addAccumulator(int[] a, int value) {
		for (@Parallel int i = 0; i < a.length; i++) {
			a[i] += value;
		}
	}

	@Test
	public void testAdd() {

		final int N = 128;
		int numKernels = 8;

		int[] data = new int[N];

		IntStream.range(0, N).parallel().forEach(idx -> {
			data[idx] = idx;
		});

		TaskSchedule s0 = new TaskSchedule("s0");
		assertNotNull(s0);

		for (int i = 0; i < numKernels; i++) {
			s0.task("t" + i, TestArrays::addAccumulator, data, 1);
		}

		s0.streamOut(data).execute();

		for (int i = 0; i < N; i++) {
			assertEquals(i + numKernels, data[i], 0.0001);
		}
	}

	public static void vectorAddDouble(double[] a, double[] b, double[] c) {
		for (@Parallel int i = 0; i < c.length; i++) {
			c[i] = a[i] + b[i];
		}
	}

	public static void vectorAddFloat(float[] a, float[] b, float[] c) {
		for (@Parallel int i = 0; i < c.length; i++) {
			c[i] = a[i] + b[i];
		}
	}

	public static void vectorAddInteger(int[] a, int[] b, int[] c) {
		for (@Parallel int i = 0; i < c.length; i++) {
			c[i] = a[i] + b[i];
		}
	}

	public static void vectorAddLong(long[] a, long[] b, long[] c) {
		for (@Parallel int i = 0; i < c.length; i++) {
			c[i] = a[i] + b[i];
		}
	}

	public static void vectorAddShort(short[] a, short[] b, short[] c) {
		for (@Parallel int i = 0; i < c.length; i++) {
			c[i] = (short) (a[i] + b[i]);
		}
	}

	@Test
	public void testVectorAdditionDouble() {
		final int numElements = 4096;
		double[] a = new double[numElements];
		double[] b = new double[numElements];
		double[] c = new double[numElements];

		IntStream.range(0, numElements).sequential().forEach(i -> {
			a[i] = (float) Math.random();
			b[i] = (float) Math.random();
		});

		//@formatter:off
		new TaskSchedule("s0")
				 .streamIn(a, b)
	             .task("t0", TestArrays::vectorAddDouble, a, b, c)
	             .streamOut(c)
	             .execute();
	    //@formatter:on

		for (int i = 0; i < c.length; i++) {
			assertEquals(a[i] + b[i], c[i], 0.001);
		}
	}

	@Test
	public void testVectorAdditionFloat() {
		final int numElements = 4096;
		float[] a = new float[numElements];
		float[] b = new float[numElements];
		float[] c = new float[numElements];

		IntStream.range(0, numElements).sequential().forEach(i -> {
			a[i] = (float) Math.random();
			b[i] = (float) Math.random();
		});

		//@formatter:off
		new TaskSchedule("s0")
				 .streamIn(a, b)
	             .task("t0", TestArrays::vectorAddFloat, a, b, c)
	             .streamOut(c)
	             .execute();
	    //@formatter:on

		for (int i = 0; i < c.length; i++) {
			assertEquals(a[i] + b[i], c[i], 0.001);
		}
	}

	@Test
	public void testVectorAdditionInteger() {
		final int numElements = 4096;
		int[] a = new int[numElements];
		int[] b = new int[numElements];
		int[] c = new int[numElements];

		Random r = new Random();
		IntStream.range(0, numElements).sequential().forEach(i -> {
			a[i] = r.nextInt();
			b[i] = r.nextInt();
		});

		//@formatter:off
		new TaskSchedule("s0")
				 .streamIn(a, b)
	             .task("t0", TestArrays::vectorAddInteger, a, b, c)
	             .streamOut(c)
	             .execute();
	    //@formatter:on

		for (int i = 0; i < c.length; i++) {
			assertEquals(a[i] + b[i], c[i], 0.001);
		}
	}

	@Test
	public void testVectorAdditionLong() {
		final int numElements = 4096;
		long[] a = new long[numElements];
		long[] b = new long[numElements];
		long[] c = new long[numElements];

		IntStream.range(0, numElements).parallel().forEach(i -> {
			a[i] = i;
			b[i] = i;
		});

		//@formatter:off
		new TaskSchedule("s0")
				 .streamIn(a, b)
	             .task("t0", TestArrays::vectorAddLong, a, b, c)
	             .streamOut(c)
	             .execute();
	    //@formatter:on

		for (int i = 0; i < c.length; i++) {
			assertEquals(a[i] + b[i], c[i], 0.001);
		}
	}

	@Test
	@Ignore
	public void testVectorAdditionShort() {
		final int numElements = 4096;
		short[] a = new short[numElements];
		short[] b = new short[numElements];
		short[] c = new short[numElements];

		IntStream.range(0, numElements).parallel().forEach(i -> {
			a[i] = 10;
			b[i] = 11;
		});

		//@formatter:off
		new TaskSchedule("s0")
				 .streamIn(a, b)
	             .task("t0", TestArrays::vectorAddShort, a, b, c)
	             .streamOut(c)
	             .execute();
	    //@formatter:on

		for (int i = 0; i < c.length; i++) {
			assertEquals(a[i] + b[i], c[i], 0.001);
		}
	}

}
