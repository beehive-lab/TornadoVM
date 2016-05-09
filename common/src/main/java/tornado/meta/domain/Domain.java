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
