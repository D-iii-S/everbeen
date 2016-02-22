package cz.cuni.mff.d3s.been.manager;

import static cz.cuni.mff.d3s.been.core.task.TaskState.WAITING;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.IMap;
import com.hazelcast.query.SqlPredicate;

import cz.cuni.mff.d3s.been.cluster.ServiceException;
import cz.cuni.mff.d3s.been.cluster.context.ClusterContext;
import cz.cuni.mff.d3s.been.core.task.TaskEntries;
import cz.cuni.mff.d3s.been.core.task.TaskEntry;
import cz.cuni.mff.d3s.been.core.task.TaskState;
import cz.cuni.mff.d3s.been.manager.msg.Messages;
import cz.cuni.mff.d3s.been.manager.msg.TaskMessage;
import cz.cuni.mff.d3s.been.mq.IMessageSender;
import cz.cuni.mff.d3s.been.mq.MessagingException;

/**
 * Handles local update events for the Task Map.
 * <p> 
 * The Task Map contains information about all task instances.
 * Updates to the local portion of the Task Map are turned
 * into messages that are processed by a dedicated thread
 * in the host runtime.
 * 
 * @author Martin Sixta
 */
final class LocalTaskListener extends TaskManagerService implements EntryListener<String, TaskEntry> {

	private static final Logger log = LoggerFactory.getLogger(LocalTaskListener.class);

	/** Format of "tasks waiting for another task to finish" query. */
	private static final String WAITING_TASKS_FMT = "taskContextId = '%s' AND taskDependency = '%s'";

	/** Distributed hash map of all task entries. */
	private IMap<String, TaskEntry> taskMap;

	/** Connection to the cluster. */
	private ClusterContext cluster;

	/** Sender of local task manager action  messages. */
	private IMessageSender<TaskMessage> sender;

	/**
	 * Creates local task change listener.
	 * <p>
	 * The listener lifecycle is directed by the {@link start} and {@link stop} methods.
	 * 
	 * @param argCluster connection to the cluster.
	 */
	public LocalTaskListener(ClusterContext argCluster) {
		this.cluster = argCluster;
		taskMap = argCluster.getTasks().getTasksMap();
		MapConfig cfg = argCluster.getTasks().getTasksMapConfig();

		// The distributed task map must be configured in Hazelcast.
		// Caching of the map values must be disabled in Hazelcast.
		if (cfg == null) throw new RuntimeException("BEEN_MAP_TASKS does not have a config.");
		if (cfg.isCacheValue()) throw new RuntimeException("BEEN_MAP_TASKS must not cache entries.");
	}

	@Override
	public void start() throws ServiceException {
		sender = createSender();
		taskMap.addLocalEntryListener(this);
	}

	@Override
	public void stop() {
		taskMap.removeEntryListener(this);
		sender.close();
	}

	@Override
	public synchronized void entryAdded(EntryEvent<String, TaskEntry> event) {
		log.debug("TaskEntry {} added", event.getKey());

		TaskEntry entry = event.getValue();

		if (entry.isSetTaskDependency()) {
			String dep = entry.getTaskDependency();

			TaskEntries.setState(entry, WAITING, "Waiting for task %s to finish", dep);
			cluster.getTasks().putTask(entry);
			return;
		}
		try {
			TaskMessage msg = Messages.createNewTaskMessage(entry);
			sender.send(msg);
		} catch (MessagingException e) {
			String msg = String.format("Cannot send message to '%s'", sender.getConnection());
			log.error(msg, e);
		}
	}

	@Override
	public synchronized void entryRemoved(EntryEvent<String, TaskEntry> event) {
		log.debug("TaskEntry {} removed ", event.getKey());
	}

	@Override
	public synchronized void entryUpdated(EntryEvent<String, TaskEntry> event) {
		log.debug("TaskEntry {} updated", event.getKey());

		TaskEntry entry = event.getValue();
		TaskState state = entry.getState();

		// skip waiting tasks
		if (state == TaskState.WAITING) {

			try {
				TaskMessage msg = Messages.createCheckSchedulabilityMessage(entry);

				sender.send(msg);
			} catch (MessagingException e) {
				String msg = String.format("Cannot send message to '%s'", sender.getConnection());
				log.error(msg, e);
			}

			return;
		}

		// schedule dependent tasks if any
		if (state == TaskState.FINISHED || state == TaskState.ABORTED) {
			scheduleWaitingTasks(entry);
		}

		try {
			TaskMessage msg = Messages.createTaskChangedMessage(entry);
			sender.send(msg);
		} catch (MessagingException e) {
			String msg = String.format("Cannot send message to '%s'", sender.getConnection());
			log.error(msg, e);
		}
	}

	@Override
	public synchronized void entryEvicted(EntryEvent<String, TaskEntry> event) {
		log.debug("TaskEntry {} evicted", event.getKey());
	}

	/**
	 * 
	 * Schedules tasks dependent on the finished task
	 * 
	 * @param entry
	 *          the task that has finished
	 */
	private void scheduleWaitingTasks(TaskEntry entry) {

		String query = String.format(WAITING_TASKS_FMT, entry.getTaskContextId(), entry.getId());

		SqlPredicate predicate = new SqlPredicate(query);
		try {
			Collection<TaskEntry> entries = taskMap.values(predicate);
			for (TaskEntry e : entries) {
				sender.send(Messages.createScheduleTaskMessage(e));
			}
		} catch (Exception e) {
			log.error("Cannot schedule a waiting task", e);
		}

	}
}
