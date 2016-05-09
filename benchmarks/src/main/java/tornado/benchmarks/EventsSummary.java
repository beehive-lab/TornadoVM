package tornado.benchmarks;

import java.util.function.Consumer;

public class EventsSummary {
	private final double total;
	private final double min;
	private final double max;
	private final double mean;
	private final double stdDev;
	private final long count;
	
	protected EventsSummary(long count,double total,double min, double max,double mean, double stdDev){
		this.count = count;
		this.total = total;
		this.min = min;
		this.max = max;
		this.mean = mean;
		this.stdDev = stdDev;
	}
	
	public void apply(final Consumer<EventsSummary> function){
		function.accept(this);
	}

	public double getMean() {
		return mean;
	}

	public double getStdDev() {
		return stdDev;
	}

	public long getCount() {
		return count;
	}
	
	public String toString(){
		return String
		.format("events=%8d, total=%6f, min=%6f, max=%6f, mean=%6f, std. dev=%6f",
				count, total, min, max, mean, stdDev);
	}
}
