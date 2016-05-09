package tornado.common;

import java.util.Arrays;

public class Statistics {
	double[]	data;
	double		size;

	public Statistics(final double[] data) {
		this.data = data;
		size = data.length;
	}

	public double getMean() {
		double sum = 0.0;
		for (final double a : data)
			sum += a;
		return sum / size;
	}

	public double getStdDev() {
		return Math.sqrt(getVariance());
	}

	public double getSum() {
		double sum = 0.0;
		for (final double a : data) {
			sum += a;
		}
		return sum;
	}

	public double getVariance() {
		final double mean = getMean();
		double temp = 0;
		for (final double a : data)
			temp += (mean - a) * (mean - a);
		return temp / size;
	}

	public double median() {
		final double[] b = new double[data.length];
		System.arraycopy(data, 0, b, 0, b.length);
		Arrays.sort(b);

		if ((data.length % 2) == 0) {
			return (b[(b.length / 2) - 1] + b[b.length / 2]) / 2.0;
		} else {
			return b[b.length / 2];
		}
	}
}
