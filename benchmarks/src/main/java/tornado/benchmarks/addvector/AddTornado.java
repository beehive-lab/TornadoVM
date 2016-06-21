package tornado.benchmarks.addvector;

import tornado.api.Event;
import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.GraphicsKernels;
import tornado.collections.types.Float4;
import tornado.collections.types.FloatOps;
import tornado.collections.types.VectorFloat4;
import tornado.common.DeviceMapping;
import tornado.benchmarks.EventList;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskUtils;
import tornado.runtime.api.CompilableTask;

public class AddTornado extends BenchmarkDriver {

	private final int numElements;
	private final DeviceMapping device;

	private VectorFloat4 a, b, c;

	private CompilableTask add;

	private Event last;
	private final EventList<Event> events;

	public AddTornado(int iterations, int numElements, DeviceMapping device) {
		super(iterations);
		this.numElements = numElements;
		this.device = device;
		events = new EventList<Event>(iterations);
		last = null;
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

		add = TaskUtils.createTask(GraphicsKernels::addVector, a, b, c);
		add.mapTo(device);
	}

	@Override
	public void tearDown() {
		add.invalidate();

		a = null;
		b = null;
		c = null;

		((OCLDeviceMapping) device).reset();
		super.tearDown();
	}

	@Override
	public void code() {

		if (last == null) {
			add.schedule();
			last = add.getEvent();
		} else {
			add.schedule(last);
			last = add.getEvent();
		}

		events.add(last);
	}

	@Override
	public void barrier() {
		last.waitOn();
	}

	@Override
	public boolean validate() {

		final VectorFloat4 result = new VectorFloat4(numElements);

		add.execute();

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
					"id=opencl-device-%d, elapsed=%f, per iteration=%f, %s\n",
					((OCLDeviceMapping) device).getDeviceIndex(), getElapsed(),
					getElapsedPerIteration(), events.summeriseEvents());
		else
			System.out.printf("id=opencl-device-%d produced invalid result\n",
					((OCLDeviceMapping) device).getDeviceIndex());
	}

	public double getOverhead() {
		return 1.0 - events.getMeanExecutionTime() / getElapsedPerIteration();
	}

}
