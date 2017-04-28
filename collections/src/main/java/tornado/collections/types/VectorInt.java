/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.collections.types;

import java.nio.IntBuffer;
import java.util.Arrays;
import tornado.collections.math.TornadoMath;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.MIN_VALUE;
import static java.lang.String.format;
import static java.nio.IntBuffer.wrap;
import static java.util.Arrays.copyOf;
import static tornado.collections.types.IntOps.fmt;

public class VectorInt implements PrimitiveStorage<IntBuffer> {
	
	private final int numElements;
	private final int[] storage;
	private static final int elementSize = 1;
	/**
	 * Creates a vector using the provided backing array
	 * @param numElements
	 * @param offset
	 * @param step
	 * @param elementSize
	 * @param array
	 */
	protected VectorInt(int numElements, int[] array){
		this.numElements = numElements;
		this.storage = array;
	}
	
	/**
	 * Creates an empty vector with 
	 * @param numElements
	 */
	public VectorInt(int numElements){
		this(numElements,new int[numElements]);
	}
	

	/**
	 * Creates an new vector from the provided storage
	 * @param storage
	 */
	public VectorInt(int[] storage) {
		this(storage.length/elementSize,storage);
	}
	
	
	/**
	 * Returns the int at the given index of this vector
	 * @param index
	 * @return value
	 */
	public int get(int index){
		return storage[index];
	}

	/**
	 * Sets the int at the given index of this vector
	 * @param index
	 * @param value
	 */
	public void set(int index, int value){
		storage[index] = value;
	}

	/**
	 * Sets the elements of this vector to that of the provided vector
	 * @param values
	 */
	public void set(VectorInt values){
		for(int i=0;i<values.storage.length;i++)
			storage[i] = values.storage[i];
	}
	
	/**
	 * Sets the elements of this vector to that of the provided array
	 * @param values
	 */
	public void set(int[] values){
		for(int i=0;i<values.length;i++)
			storage[i] = values[i];
	}
	
	/**
	 * Sets all elements to value
	 * @param value
	 */
	public void fill(int value){
		for(int i=0;i<storage.length;i++)
			storage[i] = value;
	}
	
	/**
	 * Returns slice of this vector
	 * @param start starting index
	 * @param numElements number of elements
	 * @return
	 */
	public VectorInt subVector(int start, int length){
		final VectorInt v = new VectorInt(length);
		for(int i=0;i<length;i++){
			v.storage[i] = storage[i+start];
		}
		
		return v;
	}
	
	/**
	 * Duplicates this vector
	 * @return
	 */
	public VectorInt duplicate(){
		return new VectorInt(copyOf(storage, storage.length));
	}
	
	
	public static int min(VectorInt v){
		int result = MAX_VALUE;
		for(int i=0;i<v.storage.length;i++)
			result = Math.min(v.storage[i],result);
		
		return result;
	}
	
	public static int max(VectorInt v){
		int result = MIN_VALUE;
		for(int i=0;i<v.storage.length;i++)
			result = Math.max(v.storage[i],result);
		
		return result;
	}

//	/**
//	 * Adds value to each element
//	 * @param value
//	 */
//	public void add(int value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) + value);
//	}
//
//	/**
//	 * Pairwise vector addition
//	 * @param values
//	 */
//	public void add(VectorInt values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) + values.get(i));
//	}
//	
//	/**
//	 * Sets this vector to the result of adding two vectors
//	 * @param values
//	 */
//	public void add(VectorInt a, VectorInt b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, a.get(i) + b.get(i));
//	}
//
//	/**
//	 * Subtracts value from each element
//	 * @param value
//	 */
//	public void sub(int value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) - value);
//	}
//	
//	/**
//	 * Pairwise vector subtraction
//	 * @param values
//	 */
//	public void sub(VectorInt values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) - values.get(i));
//	}
//
//	/**
//	 * Sets this vector to the result of subtracting two vectors
//	 * @param values
//	 */
//	public void sub(VectorInt a, VectorInt b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, a.get(i) - b.get(i));
//	}
//
//	
//	/**
//	 * Pairwise vector multiplication
//	 * @param values
//	 */
//	public void mult(VectorInt values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) * values.get(i));
//	}
//	
//	/**
//	 * Pairwise vector multiplication
//	 * @param values
//	 */
//	public void mult(VectorInt values, int value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, values.get(i) * value);
//	}
//	
//	/**
//	 * Multiplies each element by value
//	 * @param value
//	 */
//	public void mult(int value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) * value);
//	}
//	
//	/**
//	 * Sets this vector to the result of multiplying two vectors
//	 * @param values
//	 */
//	public void mult(VectorInt a, VectorInt b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, a.get(i) * b.get(i));
//	}
//
//	/**
//	 * Pairwise vector division
//	 * @param values
//	 */
//	public void div(VectorInt values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) / values.get(i));
//	}
//	
//	/**
//	 * Divides each element by value
//	 * @param value
//	 */
//	public void div(int value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) / value);
//	}
//	
//	/**
//	 * Sets this vector to the result of dividing two vectors
//	 * @param values
//	 */
//	public void div(VectorInt a, VectorInt b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, a.get(i) / b.get(i));
//	}
//	
//	/**
//	 * Returns the value of the smallest element
//	 */
//	public int min(){
//		int result = get(0);
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
//	public void min(VectorInt values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.min(get(i) , values.get(i)));
//	}
//	
//	/**
//	 * Sets vector equal to this[i] = min(element[i],value) for all elements
//	 * @param value
//	 */
//	public void min(int value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.min(get(i) , value));
//	}
//	
//	/**
//	 * Sets this vector to the minimum value from each vector
//	 * Sets vector equal to this[i] = min(a[i],b[i]) for all elements
//	 * @param values
//	 */
//	public void min(VectorInt a, VectorInt b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.min(a.get(i) ,b.get(i)));
//	}
//	
//	/**
//	 * Returns the value of the largest element
//	 */
//	public int max(){
//		int result = get(0);
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
//	public void max(VectorInt values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.max(get(i) , values.get(i)));
//	}
//	
//	/**
//	 * Sets vector equal to this[i] = max(element[i],value) for all elements
//	 * @param value
//	 */
//	public void max(int value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.max(get(i) , value));
//	}
//	
//	/**
//	 * Sets this vector to the minimum value from each vector
//	 * Sets vector equal to this[i] = max(a[i],b[i]) for all elements
//	 * @param values
//	 */
//	public void max(VectorInt a, VectorInt b){
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
//	public void clamp(int min, int max){
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
//	public void scale(int value){
//		mult(value);
//	}

	/**
	 * Vector equality test
	 * @param vector
	 * @return true if vectors match
	 */
	public boolean isEqual(VectorInt vector){
		return TornadoMath.isEqual(storage, vector.storage);
	}
	
	/**
	 * dot product (this . this)
	 * @return
	 */
	public static final int dot(VectorInt a, VectorInt b){
		int sum = 0;
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
//	public int length(){
//		return TornadoMath.sqrt(dot());
//	}
//
//	/**
//	 * Normalises the vector
//	 */
//	public void normalise(){
//		int len = length();
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
			str += format(fmt,get(i)) + " ";
		}

		str += "]";
		
		return str;
	}

	/**
	 * 
	 */
	public String toString(){
		String str = format("VectorInt <%d>",numElements);
		if(numElements < 32)
			str += toString(fmt);
		return str;
	}

	@Override
	public void loadFromBuffer(IntBuffer buffer) {
		asBuffer().put(buffer);
		
	}

	@Override
	public IntBuffer asBuffer() {
		return wrap(storage);
	}

	@Override
	public int size() {
		return numElements;
	}

//	public int mean() {
//		return sum() / (int) getNumElements();
//	}
//	
//	public int sum() {
//		int result = 0f;
//		for(int i=0;i<getNumElements();i++)
//			result += get(i);
//		return result;
//	}
}
