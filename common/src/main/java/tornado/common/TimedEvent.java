package tornado.common;

public class TimedEvent {
	protected final long start;
	protected final long stop;
	
	public TimedEvent(final long t0, final long t1) {
		start = t0;
		stop = t1;
	}

	public long getNanoTime() {
		return stop - start;
	}

	public long getStart() {
		return start;
	}

	public long getStop() {
		return stop;
	}

	public double getTime() {
		return 1e-9 * getNanoTime();
	}
	
}
