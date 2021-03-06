package cz.cuni.mff.d3s.been.cluster.context;

import java.util.Collection;

import com.hazelcast.core.IMap;

import cz.cuni.mff.d3s.been.cluster.Names;
import cz.cuni.mff.d3s.been.core.ri.RuntimeInfo;

/**
 * Utility class for operations related to host runtimes.
 * 
 * @author Martin Sixta
 */
public class Runtimes {

	/** BEEN cluster connection */
	private ClusterContext clusterCtx;

	/**
	 * Package private constructor, creates a new instance that uses the specified
	 * BEEN cluster context.
	 * 
	 * @param clusterCtx
	 *          the cluster context to use
	 */
	Runtimes(ClusterContext clusterCtx) {
		// package private visibility prevents out-of-package instantiation
		this.clusterCtx = clusterCtx;
	}

	/**
	 * @return collection clone (changes not reflected) of all registered host
	 *         runtimes. </br>
	 * 
	 *         <b>Warning!</b> modifying the returned list does not affect the
	 *         original list.
	 */
	public Collection<RuntimeInfo> getRuntimes() {
		return getRuntimeMap().values();
	}

	/**
	 * @param key
	 *          the ID of the host runtime to retrieve
	 * @return clone of {@link RuntimeInfo} registered in cluster. <br/>
	 * 
	 *         <b>Warning!</b> modifying the returned value does not change the
	 *         original value.
	 */
	public RuntimeInfo getRuntimeInfo(String key) {
		return getRuntimeMap().get(key);
	}

	/**
	 * Stores given {@link RuntimeInfo} in cluster.
	 * 
	 * @param runtimeInfo
	 *          the host runtime information to store
	 */
	public void storeRuntimeInfo(RuntimeInfo runtimeInfo) {
		getRuntimeMap().put(runtimeInfo.getId(), runtimeInfo);
	}

	/**
	 * Removes stored {@link RuntimeInfo} identified by given id from cluster.
	 * 
	 * @param id
	 *          the ID of the host runtime to remove
	 */
	public void removeRuntimeInfo(String id) {
		getRuntimeMap().remove(id);
	}

	/**
	 * @return modifiable map of all registered Host Runtimes.
	 */
	public IMap<String, RuntimeInfo> getRuntimeMap() {
		return clusterCtx.getMap(Names.HOSTRUNTIMES_MAP_NAME);
	}

}
