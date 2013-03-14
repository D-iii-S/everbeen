package cz.cuni.mff.d3s.been.core;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import com.hazelcast.config.Config;
import com.hazelcast.core.AtomicNumber;
import com.hazelcast.core.ClientService;
import com.hazelcast.core.Cluster;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IList;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.Instance;
import com.hazelcast.core.Member;
import com.hazelcast.core.Transaction;

/**
 * @author Martin Sixta
 */
public class ClusterContext {

	private final MapUtils mapUtils;
	private final RuntimesUtils runtimesUtils;
	private final TaskUtils tasksUtils;
	private final TopicUtils topicUtils;
	private final ServicesUtils servicesUtils;
	private final HazelcastInstance hcInstance;

	public ClusterContext(HazelcastInstance hcInstance) {
		this.hcInstance = hcInstance;
		this.mapUtils = new MapUtils(this);
		this.runtimesUtils = new RuntimesUtils(this);
		this.tasksUtils = new TaskUtils(this);
		this.topicUtils = new TopicUtils(this);
		this.servicesUtils = new ServicesUtils(this);
	}

	public TaskUtils getTasksUtils() {
		return tasksUtils;
	}

	public MapUtils getMapUtils() {
		return mapUtils;
	}

	public RuntimesUtils getRuntimesUtils() {
		return runtimesUtils;
	}

	public TopicUtils getTopicUtils() {
		return topicUtils;
	}

	public ServicesUtils getServicesUtils() {
		return servicesUtils;
	}

	public HazelcastInstance getInstance() {
		return hcInstance;
	}

	public Set<Member> getMembers() {

		return getInstance().getCluster().getMembers();

	}

	public String getId() {
		return getLocalMember().getUuid();
	}

	public int getPort() {
		return getLocalMember().getInetSocketAddress().getPort();

	}

	public String getHostName() {
		return getLocalMember().getInetSocketAddress().getHostName();
	}

	public InetSocketAddress getInetSocketAddress() {
		return getLocalMember().getInetSocketAddress();
	}

	public Member getLocalMember() {
		return getCluster().getLocalMember();
	}

	public Cluster getCluster() {
		return getInstance().getCluster();
	}

	public ClientService getClientService() {
		return getInstance().getClientService();
	}

	public <E> IQueue<E> getQueue(String name) {
		return getInstance().getQueue(name);
	}

	public <E> ITopic<E> getTopic(String name) {
		return getInstance().getTopic(name);
	}

	public <K, V> IMap<K, V> getMap(String name) {
		return getInstance().getMap(name);
	}

	public <E> IList<E> getList(String name) {
		return getInstance().getList(name);
	}

	public Transaction getTransaction() {
		return getInstance().getTransaction();
	}

	public AtomicNumber getAtomicNumber(String name) {
		return getInstance().getAtomicNumber(name);
	}

	public Collection<Instance> getInstances() {
		return getInstance().getInstances();
	}

	public Collection<Instance> getInstances(Instance.InstanceType instanceType) {
		Collection<Instance> instances = new ArrayList<>();

		for (Instance instance : getInstances()) {
			if (instance.getInstanceType() == instanceType) {
				instances.add(instance);
			}
		}

		return instances;
	}

	public boolean containsInstance(Instance.InstanceType instanceType,
			String name) {

		for (Instance instance : getInstances(instanceType)) {
			boolean isName = instance.getId().toString().endsWith(":" + name);
			if (isName) {
				return true;
			}
		}

		return false;
	}

	public Config getConfig() {
		return getInstance().getConfig();
	}

	public void registerService(String serviceName, Object serviceInfo) {
		getMap(Names.SERVICES_MAP_NAME).put(serviceName, serviceInfo);
	}

	public void unregisterService(String serviceName) {
		getMap(Names.SERVICES_MAP_NAME).remove(serviceName);
	}

}