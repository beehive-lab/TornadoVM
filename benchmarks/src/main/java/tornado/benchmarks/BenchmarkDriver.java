package tornado.benchmarks;

import tornado.common.RuntimeUtilities;

public abstract class BenchmarkDriver {
	private static final boolean	PRINT_MEM_USAGE	= Boolean
															.parseBoolean(System.getProperty(
																	"tornado.benchmarks.memusage",
																	"false"));
	public static final float		MAX_ULP = Float.parseFloat(System.getProperty("tornado.benchmarks.maxulp","5.0"));
	
	private long					iterations;
	private double					elapsed;
	private boolean					validResult;

	public BenchmarkDriver(long iterations) {
		this.iterations = iterations;
	}

	public abstract void setUp();

	public void tearDown() {
		final Runtime runtime = Runtime.getRuntime();
		runtime.gc();
		if (PRINT_MEM_USAGE) {
			System.out.printf("memory: free=%s, total=%s, max=%s\n",
					RuntimeUtilities.humanReadableByteCount(runtime.freeMemory(), false),
					RuntimeUtilities.humanReadableByteCount(runtime.totalMemory(), false),
					RuntimeUtilities.humanReadableByteCount(runtime.maxMemory(), false));
		}
	}

	public abstract boolean validate();

	public abstract void code();

	protected void barrier() {

	}

	public void benchmark() {

		setUp();

		validResult = validate();

		if (validResult) {

			long start = System.nanoTime();
			for (long i = 0; i < iterations; i++) {
				code();
			}

			barrier();
			long end = System.nanoTime();

			elapsed = RuntimeUtilities.elapsedTimeInSeconds(start, end);

		}

		tearDown();

	}

	public double getElapsed() {
		return elapsed;
	}

	public double getElapsedPerIteration() {
		return elapsed / iterations;
	}

	public boolean isValid() {
		return validResult;
	}

	public String getSummary() {
		return String.format("elapsed=%6e, per iteration=%6e", getElapsed(),
				getElapsedPerIteration());
	}

}
