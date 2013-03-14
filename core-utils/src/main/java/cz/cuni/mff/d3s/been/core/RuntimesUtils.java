package cz.cuni.mff.d3s.been.core;

import java.util.Collection;

import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;

import cz.cuni.mff.d3s.been.core.ri.RuntimeInfo;

/**
 * @author Martin Sixta
 */
public class RuntimesUtils {

	private ClusterContext clusterCtx;

	RuntimesUtils(ClusterContext clusterCtx) {
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
	 */
	public void storeRuntimeInfo(RuntimeInfo runtimeInfo) {
		getRuntimeMap().put(runtimeInfo.getId(), runtimeInfo);
	}

	/**
	 * Removes stored {@link RuntimeInfo} identified by given id from cluster.
	 * 
	 * @param id
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

	/**
	 * FIXME Martin Sixta ?? I do not understand what the "LocalTaskQueue" really
	 * is. Please document it.
	 * 
	 * @return
	 */
	public IQueue<String> getLocalTaskQueue() {
		return clusterCtx.getInstance().getQueue(clusterCtx.getId());

	}
}