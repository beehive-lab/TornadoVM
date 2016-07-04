package tornado.benchmarks.addvector;

import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.Float4;
import tornado.collections.types.FloatOps;
import tornado.collections.types.VectorFloat4;
import tornado.common.DeviceMapping;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskGraph;

public class AddTornado extends BenchmarkDriver {

	private final int numElements;
	private final DeviceMapping device;

	private VectorFloat4 a, b, c;

	private TaskGraph graph;

	public AddTornado(int iterations, int numElements, DeviceMapping device) {
		super(iterations);
		this.numElements = numElements;
		this.device = device;
	}

	@Override
	public void setUp() {
		a = new VectorFloat4(numElements);
		b = new VectorFloat4(numElements);
		c = new VectorFloat4(numElements);

		final Float4 valueA = new Float4(new float[] { 1f, 1f, 1f, 1f });
		final Float4 valueB = new Float4(new float[] { 2f, 2f, 2f, 2f });
		for (int i = 0; i < numElements; i++) {
			a.set(i, valueA);
			b.set(i, valueB);
		}

                graph = new TaskGraph()
                    .add(GraphicsKernels::addVector, a, b, c)
                    .streamOut(c)
                    .mapAllTo(device);
                
                graph.warmup();
	}

	@Override
	public void tearDown() {
		graph.dumpTimes();
                graph.dumpProfiles();
                
		a = null;
		b = null;
		c = null;

		((OCLDeviceMapping) device).reset();
		super.tearDown();
	}

	@Override
	public void code() {

            
                graph.schedule().waitOn();
            
		
	}

	@Override
	public boolean validate() {

		final VectorFloat4 result = new VectorFloat4(numElements);

		graph.schedule().waitOn();

		GraphicsKernels.addVector(a, b, result);

		float maxULP = 0f;
		for (int i = 0; i < numElements; i++) {
			final float ulp = FloatOps.findMaxULP(result.get(i), c.get(i));

			if (ulp > maxULP) {
				maxULP = ulp;
			}
		}
		return maxULP < MAX_ULP;
	}

	public void printSummary() {
		if (isValid())
			System.out.printf(
					"id=opencl-device-%d, elapsed=%f, per iteration=%f\n",
					((OCLDeviceMapping) device).getDeviceIndex(), getElapsed(),
					getElapsedPerIteration());
		else
			System.out.printf("id=opencl-device-%d produced invalid result\n",
					((OCLDeviceMapping) device).getDeviceIndex());
	}

	

}
