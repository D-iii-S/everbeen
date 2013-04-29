package cz.cuni.mff.d3s.been.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.MultiMap;

import cz.cuni.mff.d3s.been.bpk.*;
import cz.cuni.mff.d3s.been.cluster.Instance;
import cz.cuni.mff.d3s.been.cluster.Names;
import cz.cuni.mff.d3s.been.cluster.context.ClusterContext;
import cz.cuni.mff.d3s.been.core.LogMessage;
import cz.cuni.mff.d3s.been.core.ri.RuntimeInfo;
import cz.cuni.mff.d3s.been.core.sri.SWRepositoryInfo;
import cz.cuni.mff.d3s.been.core.task.*;
import cz.cuni.mff.d3s.been.datastore.SoftwareStoreFactory;
import cz.cuni.mff.d3s.been.debugassistant.DebugAssistant;
import cz.cuni.mff.d3s.been.debugassistant.DebugListItem;
import cz.cuni.mff.d3s.been.swrepoclient.SwRepoClient;
import cz.cuni.mff.d3s.been.swrepoclient.SwRepoClientFactory;

/**
 * User: donarus Date: 4/27/13 Time: 11:50 AM
 */
public class BeenApiImpl implements BeenApi {

	private static final Logger log = LoggerFactory.getLogger(BeenApiImpl.class);

	private final ClusterContext clusterContext;

	public BeenApiImpl(
			String host,
			int port,
			String groupName,
			String groupPassword) {
		HazelcastInstance instance = Instance.newNativeInstance(
				host,
				port,
				groupName,
				groupPassword);
		clusterContext = new ClusterContext(instance);
	}

	public BeenApiImpl(ClusterContext clusterContext) {
		this.clusterContext = clusterContext;
	}

	@Override
	public Collection<TaskEntry> getTasks() {
		return clusterContext.getTasks().getTasks();
	}

	@Override
	public TaskEntry getTask(String id) {
		return clusterContext.getTasks().getTask(id);
	}

	@Override
	public Collection<TaskContextEntry> getTaskContexts() {
		return clusterContext.getTaskContexts().getTaskContexts();
	}

	@Override
	public TaskContextEntry getTaskContext(String id) {
		return clusterContext.getTaskContexts().getTaskContext(id);
	}

	@Override
	public Collection<RuntimeInfo> getRuntimes() {
		return clusterContext.getRuntimes().getRuntimes();
	}

	@Override
	public RuntimeInfo getRuntime(String id) {
		return clusterContext.getRuntimes().getRuntimeInfo(id);
	}

	@Override
	public Collection<String> getLogSets() {
		MultiMap<String, LogMessage> logs = clusterContext.getInstance().getMultiMap(
				Names.LOGS_MULTIMAP_NAME);
		return logs.keySet();
	}

	@Override
	public Collection<LogMessage> getLogs(String setId) {
		MultiMap<String, LogMessage> logs = clusterContext.getInstance().getMultiMap(
				Names.LOGS_MULTIMAP_NAME);
		return logs.get(setId);
	}

	@Override
	public Collection<BpkIdentifier> getBpks() {
		SWRepositoryInfo swInfo = clusterContext.getServices().getSWRepositoryInfo();
		SwRepoClient client = new SwRepoClientFactory(SoftwareStoreFactory.getDataStore()).getClient(
				swInfo.getHost(),
				swInfo.getHttpServerPort());
		return client.listBpks();
	}

	@Override
	public
			void
			uploadBpk(InputStream bpkInputStream) throws BpkConfigurationException {
		SWRepositoryInfo swInfo = clusterContext.getServices().getSWRepositoryInfo();
		SwRepoClient client = new SwRepoClientFactory(SoftwareStoreFactory.getDataStore()).getClient(
				swInfo.getHost(),
				swInfo.getHttpServerPort());

		BpkIdentifier bpkIdentifier = new BpkIdentifier();

		ByteArrayOutputStream tempStream = new ByteArrayOutputStream();
		try {
			IOUtils.copy(bpkInputStream, tempStream);
		} catch (IOException e) {
			log.error("Cannot upload BPK.", e);
			return;
		}

		ByteArrayInputStream tempInputStream = new ByteArrayInputStream(tempStream.toByteArray());

		BpkConfiguration bpkConfiguration = BpkResolver.resolve(tempInputStream);
		MetaInf metaInf = bpkConfiguration.getMetaInf();
		bpkIdentifier.setGroupId(metaInf.getGroupId());
		bpkIdentifier.setBpkId(metaInf.getBpkId());
		bpkIdentifier.setVersion(metaInf.getVersion());

		tempInputStream.reset();

		client.putBpk(bpkIdentifier, tempInputStream);
	}

	@Override
	public InputStream downloadBpk(BpkIdentifier bpkIdentifier) {
		SWRepositoryInfo swInfo = clusterContext.getServices().getSWRepositoryInfo();
		SwRepoClient client = new SwRepoClientFactory(SoftwareStoreFactory.getDataStore()).getClient(
				swInfo.getHost(),
				swInfo.getHttpServerPort());

		Bpk bpk = client.getBpk(bpkIdentifier);
		try {
			return bpk.getInputStream();
		} catch (IOException e) {
			log.error("Cannot get input stream from BPK.", e);
			return null;
		}
	}

	@Override
	public void deleteBpk(BpkIdentifier bpkIdentifier) {
		// TODO
		throw new UnsupportedOperationException("Not yet implemented.");
	}

	@Override
	public String submitTask(TaskDescriptor taskDescriptor) {
		TaskContextDescriptor contextDescriptor = new TaskContextDescriptor();
		Task taskInTaskContext = new Task();
		Descriptor descriptorInTaskContext = new Descriptor();
		descriptorInTaskContext.setTaskDescriptor(taskDescriptor);
		taskInTaskContext.setDescriptor(descriptorInTaskContext);
		contextDescriptor.getTask().add(taskInTaskContext);

		TaskContextEntry taskContextEntry = clusterContext.getTaskContexts().submit(
				contextDescriptor);

		if (taskContextEntry.getContainedTask().size() == 0) {
			throw new RuntimeException("Created task context does not contain a task.");
		}

		String taskId = taskContextEntry.getContainedTask().get(0);
		return taskId;
	}

	@Override
	public void killTask(String taskId) {
		// TODO
		throw new UnsupportedOperationException("Not yet implemented.");
	}

	@Override
	public String submitTaskContext(TaskContextDescriptor taskContextDescriptor) {
		TaskContextEntry taskContextEntry = clusterContext.getTaskContexts().submit(
				taskContextDescriptor);

		return taskContextEntry.getId();
	}

	@Override
	public void killTaskContext(String taskId) {
		// TODO
		throw new UnsupportedOperationException("Not yet implemented.");
	}

	@Override
	public Collection<DebugListItem> getDebugWaitingTasks() {
		DebugAssistant debugAssistant = new DebugAssistant(clusterContext);
		return debugAssistant.listWaitingProcesses();
	}
}