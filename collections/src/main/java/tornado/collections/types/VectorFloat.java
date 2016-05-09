package tornado.collections.types;

import java.nio.FloatBuffer;
import java.util.Arrays;

import tornado.collections.math.TornadoMath;
import tornado.common.exceptions.TornadoInternalError;

public class VectorFloat implements PrimitiveStorage<FloatBuffer> {
	
	private final int numElements;
	private final float[] storage;
	private static final int elementSize = 1;
	/**
	 * Creates a vector using the provided backing array
	 * @param numElements
	 * @param offset
	 * @param step
	 * @param elementSize
	 * @param array
	 */
	protected VectorFloat(int numElements, float[] array){
		this.numElements = numElements;
		this.storage = array;
	}
	
	/**
	 * Creates an empty vector with 
	 * @param numElements
	 */
	public VectorFloat(int numElements){
		this(numElements,new float[numElements]);
	}
	

	/**
	 * Creates an new vector from the provided storage
	 * @param storage
	 */
	public VectorFloat(float[] storage) {
		this(storage.length/elementSize,storage);
	}
	
	
	/**
	 * Returns the float at the given index of this vector
	 * @param index
	 * @return value
	 */
	public float get(int index){
		return storage[index];
	}

	/**
	 * Sets the float at the given index of this vector
	 * @param index
	 * @param value
	 */
	public void set(int index, float value){
		storage[index] = value;
	}

	/**
	 * Sets the elements of this vector to that of the provided vector
	 * @param values
	 */
	public void set(VectorFloat values){
		for(int i=0;i<values.storage.length;i++)
			storage[i] = values.storage[i];
	}
	
	/**
	 * Sets the elements of this vector to that of the provided array
	 * @param values
	 */
	public void set(float[] values){
		for(int i=0;i<values.length;i++)
			storage[i] = values[i];
	}
	
	/**
	 * Sets all elements to value
	 * @param value
	 */
	public void fill(float value){
		for(int i=0;i<storage.length;i++)
			storage[i] = value;
	}
	
	/**
	 * Returns slice of this vector
	 * @param start starting index
	 * @param numElements number of elements
	 * @return
	 */
	public VectorFloat subVector(int start, int length){
		final VectorFloat v = new VectorFloat(length);
		for(int i=0;i<length;i++){
			v.storage[i] = storage[i+start];
		}
		
		return v;
	}
	
	/**
	 * Duplicates this vector
	 * @return
	 */
	public VectorFloat duplicate(){
		return new VectorFloat(Arrays.copyOf(storage, storage.length));
	}
	
	
	public static float min(VectorFloat v){
		float result = Float.MAX_VALUE;
		for(int i=0;i<v.storage.length;i++)
			result = Math.min(v.storage[i],result);
		
		return result;
	}
	
	public static float max(VectorFloat v){
		float result = Float.MIN_VALUE;
		for(int i=0;i<v.storage.length;i++)
			result = Math.max(v.storage[i],result);
		
		return result;
	}

//	/**
//	 * Adds value to each element
//	 * @param value
//	 */
//	public void add(float value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) + value);
//	}
//
//	/**
//	 * Pairwise vector addition
//	 * @param values
//	 */
//	public void add(VectorFloat values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) + values.get(i));
//	}
//	
//	/**
//	 * Sets this vector to the result of adding two vectors
//	 * @param values
//	 */
//	public void add(VectorFloat a, VectorFloat b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, a.get(i) + b.get(i));
//	}
//
//	/**
//	 * Subtracts value from each element
//	 * @param value
//	 */
//	public void sub(float value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) - value);
//	}
//	
//	/**
//	 * Pairwise vector subtraction
//	 * @param values
//	 */
//	public void sub(VectorFloat values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) - values.get(i));
//	}
//
//	/**
//	 * Sets this vector to the result of subtracting two vectors
//	 * @param values
//	 */
//	public void sub(VectorFloat a, VectorFloat b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, a.get(i) - b.get(i));
//	}
//
//	
//	/**
//	 * Pairwise vector multiplication
//	 * @param values
//	 */
//	public void mult(VectorFloat values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) * values.get(i));
//	}
//	
//	/**
//	 * Pairwise vector multiplication
//	 * @param values
//	 */
//	public void mult(VectorFloat values, float value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, values.get(i) * value);
//	}
//	
//	/**
//	 * Multiplies each element by value
//	 * @param value
//	 */
//	public void mult(float value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) * value);
//	}
//	
//	/**
//	 * Sets this vector to the result of multiplying two vectors
//	 * @param values
//	 */
//	public void mult(VectorFloat a, VectorFloat b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, a.get(i) * b.get(i));
//	}
//
//	/**
//	 * Pairwise vector division
//	 * @param values
//	 */
//	public void div(VectorFloat values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) / values.get(i));
//	}
//	
//	/**
//	 * Divides each element by value
//	 * @param value
//	 */
//	public void div(float value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) / value);
//	}
//	
//	/**
//	 * Sets this vector to the result of dividing two vectors
//	 * @param values
//	 */
//	public void div(VectorFloat a, VectorFloat b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, a.get(i) / b.get(i));
//	}
//	
//	/**
//	 * Returns the value of the smallest element
//	 */
//	public float min(){
//		float result = get(0);
//		for(int i=1;i<getNumElements();i++)
//			result = Math.min(result , get(i));
//		return result;
//	}
//	
//	/**
//	 * Pairwise vector minimum
//	 * Sets vector equal to this[i] = min(element[i],values[i]) for all elements
//	 * @param values
//	 */
//	public void min(VectorFloat values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.min(get(i) , values.get(i)));
//	}
//	
//	/**
//	 * Sets vector equal to this[i] = min(element[i],value) for all elements
//	 * @param value
//	 */
//	public void min(float value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.min(get(i) , value));
//	}
//	
//	/**
//	 * Sets this vector to the minimum value from each vector
//	 * Sets vector equal to this[i] = min(a[i],b[i]) for all elements
//	 * @param values
//	 */
//	public void min(VectorFloat a, VectorFloat b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.min(a.get(i) ,b.get(i)));
//	}
//	
//	/**
//	 * Returns the value of the largest element
//	 */
//	public float max(){
//		float result = get(0);
//		for(int i=1;i<getNumElements();i++)
//			result = Math.max(result , get(i));
//		return result;
//	}
//	
//	/**
//	 * Pairwise vector maximum
//	 * Sets vector equal to this[i] = max(element[i],values[i]) for all elements
//	 * @param values
//	 */
//	public void max(VectorFloat values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.max(get(i) , values.get(i)));
//	}
//	
//	/**
//	 * Sets vector equal to this[i] = max(element[i],value) for all elements
//	 * @param value
//	 */
//	public void max(float value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.max(get(i) , value));
//	}
//	
//	/**
//	 * Sets this vector to the minimum value from each vector
//	 * Sets vector equal to this[i] = max(a[i],b[i]) for all elements
//	 * @param values
//	 */
//	public void max(VectorFloat a, VectorFloat b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.max(a.get(i) ,b.get(i)));
//	}
//	
//	public void floor(){
//		for(int i=0;i<getNumElements();i++)
//			set(i, TornadoMath.floor(get(i)));
//	}
//	
//	public void frac(){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) - TornadoMath.floor(get(i)));
//	}
//	
//	public void clamp(float min, float max){
//		for(int i=0;i<getNumElements();i++)
//			set(i, GraphicsMath.clamp(get(i), min, max));
//		
//		
//	}
//
//	/**
//	 * Scales vector according to value
//	 * @param value
//	 */
//	public void scale(float value){
//		mult(value);
//	}

	/**
	 * Vector equality test
	 * @param vector
	 * @return true if vectors match
	 */
	public boolean isEqual(VectorFloat vector){
		return TornadoMath.isEqual(storage, vector.storage);
	}
	
	/**
	 * dot product (this . this)
	 * @return
	 */
	public static final float dot(VectorFloat a, VectorFloat b){
		float sum = 0;
		for(int i=0; i<a.size();i++){
			sum +=a.get(i) * b.get(i);
		}
		return sum;
	}
//
//	/**
//	 * Returns the vector length
//	 * e.g. the sqrt of all elements squared
//	 * @return
//	 */
//	public float length(){
//		return TornadoMath.sqrt(dot());
//	}
//
//	/**
//	 * Normalises the vector
//	 */
//	public void normalise(){
//		float len = length();
//
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) / len);
//
//	}

	/**
	 * Prints the vector using the specified format string
	 * @param fmt
	 * @return
	 */
	public String toString(String fmt){
		String str = "[ ";

		for(int i=0;i<numElements;i++){
			str += String.format(fmt,get(i)) + " ";
		}

		str += "]";
		
		return str;
	}

	/**
	 * 
	 */
	public String toString(){
		String str = String.format("VectorFloat <%d>",numElements);
		if(numElements < 32)
			str += toString(FloatOps.fmt);
		return str;
	}

	@Override
	public void loadFromBuffer(FloatBuffer buffer) {
		asBuffer().put(buffer);
		
	}

	@Override
	public FloatBuffer asBuffer() {
		return FloatBuffer.wrap(storage);
	}

	@Override
	public int size() {
		return numElements;
	}

//	public float mean() {
//		return sum() / (float) getNumElements();
//	}
//	
//	public float sum() {
//		float result = 0f;
//		for(int i=0;i<getNumElements();i++)
//			result += get(i);
//		return result;
//	}
}
