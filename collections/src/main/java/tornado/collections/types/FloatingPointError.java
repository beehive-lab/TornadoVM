package tornado.collections.types;

public class FloatingPointError {
	private final float averageUlp;
	private final float minUlp;
	private final float maxUlp;
	private final float stdDevUlp;
	private final int errors;
	
	public FloatingPointError(float average, float min, float max, float stdDev, int errors){
		this.averageUlp = average;
		this.minUlp = min;
		this.maxUlp = max;
		this.stdDevUlp = stdDev;
		this.errors = errors;
	}
	
	public FloatingPointError(float average, float min, float max, float stdDev){
		this(average,min,max,stdDev,-1);
	}
	
	public String toString(){
		return String.format("errors=%d, mean ulp=%f, std. dev =%f, min ulp=%f, max ulp=%f",errors, averageUlp,stdDevUlp,minUlp,maxUlp);
	}

	public float getErrors() {
		return errors;
	}

	public float getAverageUlp() {
		return averageUlp;
	}

	public float getMinUlp() {
		return minUlp;
	}

	public float getMaxUlp() {
		return maxUlp;
	}

	public float getStdDevUlp() {
		return stdDevUlp;
	}
}
