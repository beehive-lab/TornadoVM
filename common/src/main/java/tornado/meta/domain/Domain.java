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
package tornado.meta.domain;

public interface Domain {

	/***
	 * Returns the number of elements in this domain.
	 * @return
	 */
	public int cardinality();
	
	/***
	 * Maps the given index onto the ith element in the domain. 
	 * e.g. for a domain with cardinality=3 
	 *      {2,4,6} map(1) = 4;
	 * @param index (0...cardninality())
	 * @return
	 */
	public int map(int index);
}
