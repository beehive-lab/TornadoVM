/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.collections.types;

import static java.lang.Double.MAX_VALUE;
import static java.lang.Double.MIN_VALUE;
import static java.lang.String.format;
import static java.nio.DoubleBuffer.wrap;
import static java.util.Arrays.copyOf;
import static uk.ac.manchester.tornado.collections.types.DoubleOps.fmt;

import java.nio.DoubleBuffer;

import uk.ac.manchester.tornado.collections.math.TornadoMath;

public class VectorDouble implements PrimitiveStorage<DoubleBuffer> {

    private final int numElements;
    private final double[] storage;
    private static final int elementSize = 1;

    /**
     * Creates a vector using the provided backing array
     *
     * @param numElements
     * @param offset
     * @param step
     * @param elementSize
     * @param array
     */
    protected VectorDouble(int numElements, double[] array) {
        this.numElements = numElements;
        this.storage = array;
    }

    /**
     * Creates an empty vector with
     *
     * @param numElements
     */
    public VectorDouble(int numElements) {
        this(numElements, new double[numElements]);
    }

    /**
     * Creates an new vector from the provided storage
     *
     * @param storage
     */
    public VectorDouble(double[] storage) {
        this(storage.length / elementSize, storage);
    }

    /**
     * Returns the double at the given index of this vector
     *
     * @param index
     *
     * @return value
     */
    public double get(int index) {
        return storage[index];
    }

    /**
     * Sets the double at the given index of this vector
     *
     * @param index
     * @param value
     */
    public void set(int index, double value) {
        storage[index] = value;
    }

    /**
     * Sets the elements of this vector to that of the provided vector
     *
     * @param values
     */
    public void set(VectorDouble values) {
        for (int i = 0; i < values.storage.length; i++) {
            storage[i] = values.storage[i];
        }
    }

    /**
     * Sets the elements of this vector to that of the provided array
     *
     * @param values
     */
    public void set(double[] values) {
        for (int i = 0; i < values.length; i++) {
            storage[i] = values[i];
        }
    }

    /**
     * Sets all elements to value
     *
     * @param value
     */
    public void fill(double value) {
        for (int i = 0; i < storage.length; i++) {
            storage[i] = value;
        }
    }

    /**
     * Returns slice of this vector
     *
     * @param start       starting index
     * @param numElements number of elements
     *
     * @return
     */
    public VectorDouble subVector(int start, int length) {
        final VectorDouble v = new VectorDouble(length);
        for (int i = 0; i < length; i++) {
            v.storage[i] = storage[i + start];
        }

        return v;
    }

    /**
     * Duplicates this vector
     *
     * @return
     */
    public VectorDouble duplicate() {
        return new VectorDouble(copyOf(storage, storage.length));
    }

    public static double min(VectorDouble v) {
        double result = MAX_VALUE;
        for (int i = 0; i < v.storage.length; i++) {
            result = Math.min(v.storage[i], result);
        }

        return result;
    }

    public static double max(VectorDouble v) {
        double result = MIN_VALUE;
        for (int i = 0; i < v.storage.length; i++) {
            result = Math.max(v.storage[i], result);
        }

        return result;
    }

//	/**
//	 * Adds value to each element
//	 * @param value
//	 */
//	public void add(double value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) + value);
//	}
//
//	/**
//	 * Pairwise vector addition
//	 * @param values
//	 */
//	public void add(VectorDouble values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) + values.get(i));
//	}
//
//	/**
//	 * Sets this vector to the result of adding two vectors
//	 * @param values
//	 */
//	public void add(VectorDouble a, VectorDouble b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, a.get(i) + b.get(i));
//	}
//
//	/**
//	 * Subtracts value from each element
//	 * @param value
//	 */
//	public void sub(double value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) - value);
//	}
//
//	/**
//	 * Pairwise vector subtraction
//	 * @param values
//	 */
//	public void sub(VectorDouble values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) - values.get(i));
//	}
//
//	/**
//	 * Sets this vector to the result of subtracting two vectors
//	 * @param values
//	 */
//	public void sub(VectorDouble a, VectorDouble b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, a.get(i) - b.get(i));
//	}
//
//
//	/**
//	 * Pairwise vector multiplication
//	 * @param values
//	 */
//	public void mult(VectorDouble values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) * values.get(i));
//	}
//
//	/**
//	 * Pairwise vector multiplication
//	 * @param values
//	 */
//	public void mult(VectorDouble values, double value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, values.get(i) * value);
//	}
//
//	/**
//	 * Multiplies each element by value
//	 * @param value
//	 */
//	public void mult(double value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) * value);
//	}
//
//	/**
//	 * Sets this vector to the result of multiplying two vectors
//	 * @param values
//	 */
//	public void mult(VectorDouble a, VectorDouble b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, a.get(i) * b.get(i));
//	}
//
//	/**
//	 * Pairwise vector division
//	 * @param values
//	 */
//	public void div(VectorDouble values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) / values.get(i));
//	}
//
//	/**
//	 * Divides each element by value
//	 * @param value
//	 */
//	public void div(double value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) / value);
//	}
//
//	/**
//	 * Sets this vector to the result of dividing two vectors
//	 * @param values
//	 */
//	public void div(VectorDouble a, VectorDouble b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, a.get(i) / b.get(i));
//	}
//
//	/**
//	 * Returns the value of the smallest element
//	 */
//	public double min(){
//		double result = get(0);
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
//	public void min(VectorDouble values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.min(get(i) , values.get(i)));
//	}
//
//	/**
//	 * Sets vector equal to this[i] = min(element[i],value) for all elements
//	 * @param value
//	 */
//	public void min(double value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.min(get(i) , value));
//	}
//
//	/**
//	 * Sets this vector to the minimum value from each vector
//	 * Sets vector equal to this[i] = min(a[i],b[i]) for all elements
//	 * @param values
//	 */
//	public void min(VectorDouble a, VectorDouble b){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.min(a.get(i) ,b.get(i)));
//	}
//
//	/**
//	 * Returns the value of the largest element
//	 */
//	public double max(){
//		double result = get(0);
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
//	public void max(VectorDouble values){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.max(get(i) , values.get(i)));
//	}
//
//	/**
//	 * Sets vector equal to this[i] = max(element[i],value) for all elements
//	 * @param value
//	 */
//	public void max(double value){
//		for(int i=0;i<getNumElements();i++)
//			set(i, Math.max(get(i) , value));
//	}
//
//	/**
//	 * Sets this vector to the minimum value from each vector
//	 * Sets vector equal to this[i] = max(a[i],b[i]) for all elements
//	 * @param values
//	 */
//	public void max(VectorDouble a, VectorDouble b){
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
//	public void clamp(double min, double max){
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
//	public void scale(double value){
//		mult(value);
//	}
    /**
     * Vector equality test
     *
     * @param vector
     *
     * @return true if vectors match
     */
    public boolean isEqual(VectorDouble vector) {
        return TornadoMath.isEqual(storage, vector.storage);
    }

    /**
     * dot product (this . this)
     *
     * @return
     */
    public static final double dot(VectorDouble a, VectorDouble b) {
        double sum = 0;
        for (int i = 0; i < a.size(); i++) {
            sum += a.get(i) * b.get(i);
        }
        return sum;
    }
//
//	/**
//	 * Returns the vector length
//	 * e.g. the sqrt of all elements squared
//	 * @return
//	 */
//	public double length(){
//		return TornadoMath.sqrt(dot());
//	}
//
//	/**
//	 * Normalises the vector
//	 */
//	public void normalise(){
//		double len = length();
//
//		for(int i=0;i<getNumElements();i++)
//			set(i, get(i) / len);
//
//	}

    /**
     * Prints the vector using the specified format string
     *
     * @param fmt
     *
     * @return
     */
    public String toString(String fmt) {
        String str = "[ ";

        for (int i = 0; i < numElements; i++) {
            str += format(fmt, get(i)) + " ";
        }

        str += "]";

        return str;
    }

    /**
     *
     */
    public String toString() {
        String str = format("VectorDouble <%d>", numElements);
        if (numElements < 32) {
            str += toString(fmt);
        }
        return str;
    }

    @Override
    public void loadFromBuffer(DoubleBuffer buffer) {
        asBuffer().put(buffer);

    }

    @Override
    public DoubleBuffer asBuffer() {
        return wrap(storage);
    }

    @Override
    public int size() {
        return numElements;
    }

//	public double mean() {
//		return sum() / (double) getNumElements();
//	}
//
//	public double sum() {
//		double result = 0f;
//		for(int i=0;i<getNumElements();i++)
//			result += get(i);
//		return result;
//	}
}
