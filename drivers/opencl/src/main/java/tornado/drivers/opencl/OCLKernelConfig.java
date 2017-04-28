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
package tornado.drivers.opencl;

import tornado.meta.Meta;
import tornado.meta.domain.DomainTree;

public class OCLKernelConfig {
	private final DomainTree domain;
	private final long[] globalOffset;
	private final long[] globalWork;
	private final long[] localWork;
	
	protected OCLKernelConfig(final DomainTree domain){
		this.domain = domain;
		final int dims = domain.getDepth();
		this.globalOffset = new long[dims];
		this.globalWork = new long[dims];
		this.localWork = new long[dims];
	}
	
	public static OCLKernelConfig create(final Meta meta){
		final OCLKernelConfig config = new OCLKernelConfig(meta.getDomain());
		meta.addProvider(OCLKernelConfig.class, config);
		return config;
	}
	
	public static OCLKernelConfig create(final Meta meta, long[] globalOffset, long[] globalWork, long[] localWork){
		final OCLKernelConfig config = create(meta);
		
		for(int i=0;i<config.getDims();i++){
			config.globalOffset[i] = globalOffset[i];
			config.globalWork[i] = globalWork[i];
			config.localWork[i] = localWork[i];
		}
		
		return config;
	}
	
	

	public long[] getGlobalOffset() {
		return globalOffset;
	}

	public long[] getGlobalWork() {
		return globalWork;
	}

	public long[] getLocalWork() {
		return localWork;
	}

	private static String formatArray(final long[] array) {
		final StringBuilder sb = new StringBuilder();

		sb.append("[");
		for (final long value : array) {
			sb.append(" " + value);
		}
		sb.append(" ]");

		return sb.toString();
	}
	
	public void printToLog(){
			System.out.printf("kernel info:\n");
			System.out.printf("\tdims              : %d\n", domain.getDepth());
			System.out.printf("\tglobal work offset: %s\n", formatArray(globalOffset));
			System.out.printf("\tglobal work size  : %s\n", formatArray(globalWork));
			System.out.printf("\tlocal  work size  : %s\n", formatArray(localWork));
	}

	public int getDims() {
		return domain.getDepth();
	}

	public DomainTree getDomain() {
		return domain;
	}
}
