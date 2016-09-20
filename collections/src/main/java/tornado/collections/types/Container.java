/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.collections.types;

/**
 *
 * @author jamesclarkson
 */
interface Container<T> {
    
    public void set(int index, T value);
    public T get(int index);
    public int numElements();
    
}
