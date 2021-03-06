package cz.cuni.mff.d3s.been.storage;

import cz.cuni.mff.d3s.been.cluster.Service;
import cz.cuni.mff.d3s.been.core.persistence.EntityCarrier;
import cz.cuni.mff.d3s.been.core.persistence.EntityID;
import cz.cuni.mff.d3s.been.persistence.DAOException;
import cz.cuni.mff.d3s.been.persistence.Query;
import cz.cuni.mff.d3s.been.persistence.QueryAnswer;
import cz.cuni.mff.d3s.been.persistence.SuccessAction;
import cz.cuni.mff.d3s.been.core.persistence.Entity;

/**
 * A generic persistence layer for BEEN.
 * 
 * @author darklight
 * 
 */
public interface Storage extends Service {

	/**
	 * Create a {@link cz.cuni.mff.d3s.been.persistence.SuccessAction} which denotes what is to be done
	 * with an {@link EntityCarrier} when it is decided that it should be stored.
	 *
	 * The created action is assumed to create the {@link #store(cz.cuni.mff.d3s.been.core.persistence.EntityID, String)} method internally to enact the persistence.
	 *
	 * @return The persist action
	 */
	SuccessAction<EntityCarrier> createPersistAction();

	/**
	 * Store a serialized {@link Entity} to a container determined by the
	 * {@link EntityID} argument.
	 * 
	 * @param entityId
	 *          {@link EntityID} that denotes the container which should hold the
	 *          provided entity
	 * @param JSON
	 *          The provided entity, serialized into JSON
	 * 
	 * @throws DAOException
	 *           If anything goes wrong with the persisting action
	 */
	void store(EntityID entityId, String JSON) throws DAOException;

	/**
	 * Query the persistence, returning an answer describing the query outcome.
	 *
	 * @param query Query to execute
	 *
	 * @return The result of the query (a {@link QueryAnswer})
	 */
	QueryAnswer query(Query query);

	/**
	 * Check the storage for connectivity. This operation may be pretty expensive, so use it wisely.
	 *
	 * @return <code>true</code> if this {@link Storage} is connected to its underlying persistence layer, <code>false</code> if not
	 */
	boolean isConnected();

	/**
	 * Check the storage for idleness/business.
	 *
	 * @return <code>true</code> if the storage is idle; <code>false</code> if it's busy
	 */
	boolean isIdle();
}
