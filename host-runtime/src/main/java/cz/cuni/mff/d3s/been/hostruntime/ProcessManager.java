package cz.cuni.mff.d3s.been.hostruntime;

import static cz.cuni.mff.d3s.been.cluster.Names.ACTION_QUEUE_NAME;
import static cz.cuni.mff.d3s.been.core.TaskPropertyNames.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.cuni.mff.d3s.been.bpk.*;
import cz.cuni.mff.d3s.been.cluster.Names;
import cz.cuni.mff.d3s.been.cluster.Service;
import cz.cuni.mff.d3s.been.cluster.ServiceException;
import cz.cuni.mff.d3s.been.cluster.context.ClusterContext;
import cz.cuni.mff.d3s.been.cluster.context.Tasks;
import cz.cuni.mff.d3s.been.core.protocol.command.CommandEntry;
import cz.cuni.mff.d3s.been.core.protocol.command.CommandEntryState;
import cz.cuni.mff.d3s.been.core.protocol.messages.BaseMessage;
import cz.cuni.mff.d3s.been.core.protocol.messages.DeleteTaskWrkDirMessage;
import cz.cuni.mff.d3s.been.core.protocol.messages.KillTaskMessage;
import cz.cuni.mff.d3s.been.core.protocol.messages.RunTaskMessage;
import cz.cuni.mff.d3s.been.core.ri.RuntimeInfo;
import cz.cuni.mff.d3s.been.core.task.TaskDescriptor;
import cz.cuni.mff.d3s.been.core.task.TaskEntry;
import cz.cuni.mff.d3s.been.core.task.TaskProperty;
import cz.cuni.mff.d3s.been.hostruntime.task.*;
import cz.cuni.mff.d3s.been.hostruntime.tasklogs.TaskLogHandler;
import cz.cuni.mff.d3s.been.mq.IMessageReceiver;
import cz.cuni.mff.d3s.been.mq.IMessageSender;
import cz.cuni.mff.d3s.been.mq.MessageQueues;
import cz.cuni.mff.d3s.been.mq.MessagingException;
import cz.cuni.mff.d3s.been.socketworks.MessageDispatcher;
import cz.cuni.mff.d3s.been.socketworks.NamedSockets;
import cz.cuni.mff.d3s.been.swrepoclient.SwRepoClientFactory;
import cz.cuni.mff.d3s.been.util.ZipUtil;

/**
 * Manages all Host Runtime task processes.
 * <p/>
 * All good names taken, so 'Process' is used.
 * 
 * @author Martin Sixta
 * @author donarus
 */
final class ProcessManager implements Service {

	/**
	 * Logger
	 */
	private static final Logger log = LoggerFactory.getLogger(ProcessManager.class);

	private static final String STD_ERR_REDIRECT_FILENAME = "stderr.log";

	private static final String STD_OUT_REDIRECT_FILENAME = "stdout.log";

	/**
	 * Host Runtime info
	 */
	private RuntimeInfo hostInfo;

	/**
	 * Connection to the cluster.
	 */
	private ClusterContext clusterContext;

	/**
	 * Manages software resources.
	 */
	private SoftwareResolver softwareResolver;

	/**
	 * Shortcut to task cluster context.
	 */
	private Tasks clusterTasks;

	/**
	 * Thread dispatching task action messages.
	 */
	TaskActionThread taskActionThread;

	/**
	 * Context of the Host Runtime
	 */
	private ProcessManagerContext tasks;

	private final MessageDispatcher messageDispatcher;

	/**
	 * Creates new instance.
	 * <p/>
	 * Call {@link #start()} to fire it up, {@link #stop()} to get rid of it.
	 * 
	 * @param clusterContext
	 *          connection to the cluster
	 * @param swRepoClientFactory
	 *          connection to the Software Repository
	 * @param hostInfo
	 *          Information about the current Host Runtime
	 */
	ProcessManager(ClusterContext clusterContext, SwRepoClientFactory swRepoClientFactory, RuntimeInfo hostInfo) {
		this.clusterContext = clusterContext;
		this.hostInfo = hostInfo;
		this.softwareResolver = new SoftwareResolver(clusterContext.getServices(), swRepoClientFactory);
		this.clusterTasks = clusterContext.getTasks();

		this.tasks = new ProcessManagerContext(clusterContext, hostInfo);
		this.messageDispatcher = MessageDispatcher.create("localhost");
	}

	/**
	 * Starts processing messages and tasks.
	 */
	@Override
	public void start() throws ServiceException {
		startTaskActionThread();
		startMessageDispatcher();
	}

	/**
	 * Starts the {@link TaskActionThread}
	 */
	private void startTaskActionThread() throws ServiceException {
		taskActionThread = new TaskActionThread();
		taskActionThread.start();
	}

	/**
	 * Starts the {@link MessageDispatcher}
	 */
	private void startMessageDispatcher() throws ServiceException {
		messageDispatcher.addReceiveHandler(NamedSockets.TASK_LOG_0MQ.getName(), TaskLogHandler.create(clusterContext));
		messageDispatcher.addReceiveHandler(
				NamedSockets.TASK_RESULT_PERSIST_0MQ.getName(),
				ResultHandler.create(clusterContext));
		messageDispatcher.addRespondingHandler(
				NamedSockets.TASK_CHECKPOINT_0MQ.getName(),
				CheckpointHandlerFactory.create(clusterContext));
		messageDispatcher.addRespondingHandler(
				NamedSockets.TASK_RESULT_QUERY_0MQ.getName(),
				PersistenceQueryHandlerFactory.create(clusterContext));
		messageDispatcher.start();
	}

	/**
	 * Stops processing, kills all remaining running processes
	 */
	@Override
	public void stop() {
		stopMessageDispatcher();
		stopTaskActionThread();

		// Kill all remaining running clusterTasks
		tasks.killRunningTasks();

	}

	/**
	 * Stops the {@link MessageDispatcher}
	 */
	private void stopMessageDispatcher() {
		log.debug("Stopping message dispatcher...");
		messageDispatcher.stop();
		log.debug("Message dispatcher stopped.");
	}

	/**
	 * Stops the {@link TaskActionThread}
	 */
	private void stopTaskActionThread() {
		log.debug("Stopping task action thread");
		try {
			taskActionThread.poison();
			taskActionThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		log.debug("Task action thread stopped");
	}

	/**
	 * Handles RunTaskMessage.
	 * <p/>
	 * Tries to run a task.
	 * 
	 * @param message
	 *          message carrying the information
	 */
	void onRunTask(RunTaskMessage message) {
		TaskEntry taskHandle = loadTask(message.taskId);
		if (taskHandle == null) {
			log.warn("No such task to run: {}", message.taskId);
		} else {
			runTask(taskHandle);
		}
	}

	/**
	 * Handles KillTaskMessage.
	 * <p/>
	 * Tries to kill a task.
	 * 
	 * @param message
	 *          message carrying the information
	 */
	synchronized void onKillTask(KillTaskMessage message) {
		TaskEntry taskEntry = loadTask(message.taskId);
		if (taskEntry == null) {
			log.warn("No such task to kill: {}", message.taskId);
		} else {
			tasks.killTask(taskEntry.getId());

		}
	}

	/**
	 * Returns cluster-wide identifier of this Host Runtime.
	 * 
	 * @return node identifier
	 */
	public String getNodeId() {
		return hostInfo.getId();
	}

	private void runTask(TaskEntry taskEntry) {

		String id = taskEntry.getId();
		TaskHandle taskHandle = new TaskHandle(taskEntry, clusterContext);

		try {
			tasks.tryAcceptTask(taskHandle);
		} catch (Exception e) {
			taskHandle.reSubmit("Cannot accept the task on %s. Reason: %s", getNodeId(), e.getMessage());
			log.info("Cannot run task {}", taskHandle.getTaskId());
			return;
		}

		File taskDir = createTaskDir(taskEntry);
		tasks.updateTaskDirs();

		try {
			TaskProcess process = createTaskProcess(taskEntry, taskDir);

			tasks.addTask(id, process);

			if (process.isDebugListeningMode()) {
				taskHandle.setDebug(process.getDebugPort(), process.isSuspended());
			}

			taskHandle.setRunning(process);

			int exitValue = process.start();

			taskHandle.setFinished(exitValue);

			try {
				process.close();
				FileUtils.deleteDirectory(taskDir);
				tasks.updateTaskDirs();
			} catch (IOException e) {
				String msg = String.format(
						"Task directory '%s' for task '%s' has not been deleted due to underlying exception.",
						taskDir,
						id);
				log.warn(msg, e);
			}
		} catch (TaskException e) {
			String msg = String.format("Task '%s' has been aborted due to underlying exception.", id);
			log.error(msg, e);
			taskHandle.setAborted(msg, e.getExitValue());
		} catch (Exception e) {
			String msg = String.format("Task '%s' has been aborted due to underlying exception.", id);
			log.error(msg, e);
			taskHandle.setAborted(msg);
		} finally {
			tasks.removeTask(taskHandle);
		}
	}

	/**
	 * Creates a new task processes.
	 * <p/>
	 * TODO: Refactoring might be useful. Fortunately the mess is concentrated
	 * only in this function
	 * 
	 * @param taskEntry
	 *          entry associated with the new process
	 * @param taskDirectory
	 *          root directory of the task
	 * @return task process representation
	 * @throws IOException
	 * @throws BpkConfigurationException
	 * @throws TaskException
	 */
	private synchronized
			TaskProcess
			createTaskProcess(TaskEntry taskEntry, File taskDirectory) throws IOException, BpkConfigurationException, TaskException {

		TaskDescriptor taskDescriptor = taskEntry.getTaskDescriptor();

		Bpk bpk = getBpk(taskDescriptor);

		ZipUtil.unzipToDir(bpk.getInputStream(), taskDirectory);

		// obtain bpk configuration
		Path taskWrkDir = taskDirectory.toPath();

		// obtain runtime information
		BpkRuntime runtime = getBpkRuntime(taskDirectory);

		// create process for the task
		CmdLineBuilder cmdLineBuilder = CmdLineBuilderFactory.create(runtime, taskDescriptor, taskDirectory);

		// create dependency downloader
		DependencyDownloader dependencyDownloader = DependencyDownloaderFactory.create(runtime);

		// create streams to redirect stdout and stderr to
		OutputStream stdOutFileOutputStream = new FileOutputStream(new File(taskDirectory, STD_OUT_REDIRECT_FILENAME));
		OutputStream stdErrFileOutputStream = new FileOutputStream(new File(taskDirectory, STD_ERR_REDIRECT_FILENAME));

		// let the compiler optimize this out
		String taskId = taskEntry.getId();
		String contextId = taskEntry.getTaskContextId();
		String benchmarkId = taskEntry.getBenchmarkId();

		TaskStdInOutHandler stdOutHandler = new TaskStdInOutHandler(taskId, contextId, benchmarkId, "stdout", stdOutFileOutputStream);
		TaskStdInOutHandler stdErrHandler = new TaskStdInOutHandler(taskId, contextId, benchmarkId, "stderr", stdErrFileOutputStream);

		// create environment properties
		Map<String, String> environment = createEnvironmentProperties(taskEntry);

		TaskProcess taskProcess = new TaskProcess(cmdLineBuilder, taskWrkDir, environment, stdOutHandler, stdErrHandler, dependencyDownloader);

		long timeout = determineTimeout(taskDescriptor);

		taskProcess.setTimeout(timeout);

		return taskProcess;
	}

	private Bpk getBpk(TaskDescriptor taskDescriptor) throws TaskException {
		BpkIdentifier bpkIdentifier = BpkIdentifierCreator.createBpkIdentifier(taskDescriptor);
		return softwareResolver.getBpk(bpkIdentifier);
	}

	private BpkRuntime getBpkRuntime(File workingDirectory) throws BpkConfigurationException {
		// obtain bpk configuration
		Path configPath = workingDirectory.toPath().resolve(BpkNames.CONFIG_FILE);
		BpkConfiguration bpkConfiguration = BpkConfigUtils.fromXml(configPath);

		return bpkConfiguration.getRuntime();

	}

	private long determineTimeout(TaskDescriptor td) {
		return td.isSetFailurePolicy() ? td.getFailurePolicy().getTimeoutRun() : TaskProcess.NO_TIMEOUT;
	}

	private Map<String, String> createEnvironmentProperties(TaskEntry taskEntry) {

		Map<String, String> properties = new TreeMap<String, String>(System.getenv());
		properties.putAll(messageDispatcher.getBindings());

		// Task specific properties
		properties.put(LOGGER, System.getProperty(LOGGER));
		properties.put(TASK_ID, taskEntry.getId());
		properties.put(CONTEXT_ID, taskEntry.getTaskContextId());
		properties.put(BENCHMARK_ID, taskEntry.getBenchmarkId());

		// add properties specified in the TaskDescriptor
		TaskDescriptor td = taskEntry.getTaskDescriptor();
		if (td.isSetProperties() && td.getProperties().isSetProperty()) {
			for (TaskProperty property : td.getProperties().getProperty()) {
				String value = property.getValue();

				if (value == null) {
					value = ""; // must not be null, issue #146
				}

				properties.put(property.getName(), value);
			}
		}
		return properties;
	}

	private File createTaskDir(TaskEntry taskEntry) {
		String taskDirName = taskEntry.getTaskDescriptor().getName() + "_" + taskEntry.getId();
		File taskDir = new File(hostInfo.getTasksWorkingDirectory(), taskDirName);
		// TODO check return value
		taskDir.mkdirs();
		return taskDir;
	}

	private TaskEntry loadTask(String taskId) {
		return clusterTasks.getTask(taskId);
	}

	/**
	 * Thread listening for task action messages. Dispatches messages to its
	 * handlers.
	 * <p/>
	 * The thread is an inner class for easy access to the ProcessManager.
	 */
	private class TaskActionThread extends Thread {

		final MessageQueues queues;

		private final Logger log = LoggerFactory.getLogger(TaskActionThread.class);

		// TODO Consider use of ExecutorService for task handling

		TaskActionThread() {
			this.queues = MessageQueues.getInstance();
		}

		@Override
		public void run() {
			IMessageReceiver<BaseMessage> receiver;

			try {
				receiver = queues.getReceiver(ACTION_QUEUE_NAME);
			} catch (MessagingException e) {
				String msg = String.format("Cannot start %s", TaskActionThread.class);
				log.error(msg, e);
				return;
			}

			while (!Thread.interrupted()) {
				try {
					final BaseMessage msg = receiver.receive();

					if (msg instanceof RunTaskMessage) {

						// spawn a new thread for the task, it might take a while
						new Thread() {
							@Override
							public void run() {
								onRunTask((RunTaskMessage) msg);
							}
						}.start();

					} else if (msg instanceof KillTaskMessage) {
						onKillTask((KillTaskMessage) msg);
					} else if (msg instanceof PoisonMessage) {
						break;
					} else if (msg instanceof MonitoringSampleMessage) {
						tasks.updateMonitoringSample(((MonitoringSampleMessage) msg).getSample());
					} else if (msg instanceof DeleteTaskWrkDirMessage) {
						onDeleteTaskWrkDir((DeleteTaskWrkDirMessage) msg);
					} else {
						log.warn("Host Runtime does not know how to handle message of type {}", msg.getClass());
					}

				} catch (MessagingException e) {
					log.error("Error receiving a message", e);
				} catch (Exception e) {
					log.error("Unknown error", e);
					break;
				}
			}

			log.info("Processing of Task Action Messages stopped");
		}

		public void poison() {
			IMessageSender<BaseMessage> sender = null;
			try {
				sender = queues.createSender(ACTION_QUEUE_NAME);
				PoisonMessage msg = new PoisonMessage("0");
				sender.send(msg);
			} catch (MessagingException e) {
				log.error("Cannot poison Task Action queue", e);
			} finally {
				if (sender != null) {
					sender.close();
				}
			}

		}
	}

	private void onDeleteTaskWrkDir(DeleteTaskWrkDirMessage msg) {
		String description = String.format("DELETE TASK WORKING DIRECTORY '%s'", msg.taskWrkDirName);

		Map<Long, CommandEntry> commandEntries = clusterContext.getMap(Names.BEEN_MAP_COMMAND_ENTRIES);
		String runtimeId = hostInfo.getId();

		commandEntries.put(
				msg.operationId,
				new CommandEntry(runtimeId, description, CommandEntryState.PENDING, msg.operationId));

		CommandEntry commandEntry;

		try {
			Path tasksRoot = Paths.get(hostInfo.getTasksWorkingDirectory());
			Path dirToDelete = Paths.get(msg.taskWrkDirName).toAbsolutePath(); // must use absolute path!

			if (dirToDelete.startsWith(tasksRoot)) {
				FileUtils.deleteDirectory(dirToDelete.toFile());
				tasks.updateTaskDirs();
				commandEntry = new CommandEntry(runtimeId, description, CommandEntryState.FINISHED, msg.operationId);
			} else {
				String errorMsg = String.format(
						"Cannot delete task working directory '%s' because it is not a task directory",
						msg.taskWrkDirName);
				log.error(errorMsg);

				commandEntry = new CommandEntry(runtimeId, errorMsg, CommandEntryState.FAILED, msg.operationId);
			}
		} catch (IOException | InvalidPathException e) {
			String errorMsg = String.format("Cannot delete task working directory '%s'", msg.taskWrkDirName);
			log.error(errorMsg, e);

			commandEntry = new CommandEntry(runtimeId, errorMsg, CommandEntryState.FAILED, msg.operationId);

		}

		commandEntries.put(msg.operationId, commandEntry);

	}

	/**
	 * Poison message for the task action thread
	 */
	private static class PoisonMessage extends BaseMessage {
		public PoisonMessage(String receiverId) {
			super(receiverId);
		}
	}
}
