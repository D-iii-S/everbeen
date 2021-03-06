package cz.cuni.mff.d3s.been.persistence;

import java.io.Serializable;
import java.util.Collection;

/**
 * An answer to a persistence layer {@link Query}
 *
 * @author darklight
 */
public interface QueryAnswer extends Serializable {

	/**
	 * Whether this answer carries any data.
	 *
	 * @return <code>true</code> if there is a dataset associated with this query; <code>false</code> otherwise
	 */
	boolean isCarryingData();

	/**
	 * Get the data associated with this answer. Will be <code>null</code> if the corresponding query resulted in an error.
	 *
	 * @return The data
	 */
	Collection<String> getData();

	/**
	 * Get the status of the associated query.
	 *
	 * @return The status
	 */
	QueryStatus getStatus();
}
