/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
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
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.common;

public class StackTracer {
    
    public static void printStack(){
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        System.out.println("Stack Trace:");
        for(int i=2;i<st.length;i++){
            StackTraceElement e = st[i];
            System.out.printf("\t at %s.%s(%s:%d)\n",e.getClassName(),e.getMethodName(),e.getFileName(),e.getLineNumber());
        }
    }
}
