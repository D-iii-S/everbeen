package cz.cuni.mff.d3s.been.taskapi;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.cuni.mff.d3s.been.core.StatusCode;
import cz.cuni.mff.d3s.been.core.TaskMessageType;
import cz.cuni.mff.d3s.been.core.TaskPropertyNames;
import cz.cuni.mff.d3s.been.mq.MessagingException;
import cz.cuni.mff.d3s.been.persistence.DAOException;
import cz.cuni.mff.d3s.been.results.Result;
import cz.cuni.mff.d3s.been.util.PropertyReader;

/**
 * @author Kuba Brecka
 */
public abstract class Task {

	private static final Logger log = LoggerFactory.getLogger(Task.class);

	private String id;
	private String contextId;
	private String benchmarkId;
	protected final ResultFacade results = ResultFacadeFactory.getResultFacade();

	/**
	 * Returns ID of the running task.
	 * 
	 * @return ID of the running task
	 */
	public String getId() {
		return id;
	}

	/**
	 * Returns context ID the running task is associated with.
	 * 
	 * @return context ID associated with the running task
	 */
	public String getContextId() {
		return contextId;
	}

	/**
	 * Returns benchmark ID of the running task.
	 * 
	 * @return benchmark ID of the running task
	 */
	public String getBenchmarkId() {
		return benchmarkId;
	}

	/**
	 * Returns system property associated with the running task.
	 * 
	 * @param propertyName
	 *          name of the property
	 * @return value associated with the name
	 */
	public String getTaskProperty(String propertyName) {
		return System.getenv(propertyName);
	}

	/**
	 * Returns system property associated with the running task or default value
	 * 
	 * @param propertyName
	 *          name of the property
	 * @param defaultValue Default value of the property (if the actual value is not found)
	 *
	 * @return value associated with the name or the default value when the
	 *         property is not set
	 */
	public String getTaskProperty(String propertyName, String defaultValue) {
		String propertyValue = System.getenv(propertyName);
		if (propertyValue == null) {
			return defaultValue;
		} else {
			return propertyValue;
		}
	}

	/**
	 * Creates a {@link PropertyReader} from environment properties of the
	 * {@link Task} which are passed to it by it's Host Runtime
	 * 
	 * @return {@link PropertyReader} initialized from environment properties of
	 *         the {@link Task}
	 */
	public PropertyReader createPropertyReader() {
		Properties properties = new Properties();
		properties.putAll(System.getenv());

		return PropertyReader.on(properties);

	}

	/**
	 * Stores a {@link Result} on behalf of the Task.
	 * <p/>
	 * taskId, contextId and contextId will be filled in to the result.
	 * 
	 * @param result
	 *          Result to persist
	 * @param group
	 *          Group of the result
	 * @throws DAOException
	 *           when result cannot be persisted
	 */
	public void store(final Result result, final String group) throws DAOException {
		long time = System.currentTimeMillis();
		result.withTaskId(getId()).withContextId(getContextId()).withBenchmarkId(getBenchmarkId()).withCreated(time);
		results.persistResult(result, group);
	}

	/**
	 * The method subclasses override to implement task's functionality.
	 * <p/>
	 * To execute a task {@link #doMain(String[])} will be called.
	 *
	 * @param args Parameters passed to the task
	 *
	 * @throws TaskException When the task fails
	 * @throws MessagingException When messaging with paired host runtime fails
	 * @throws DAOException When persistent object access fails
	 */
	public abstract void run(String[] args) throws TaskException, MessagingException, DAOException;

	/**
	 * The method which sets up task's environment and calls
	 * {@link #run(String[])}.
	 * 
	 * @param args Parameters passed to the task
	 *
	 * @return Task exit code
	 */
	public int doMain(String[] args) {
		try {
			initialize();
			run(args);
		} catch (MessagingException e) {
			System.err.println("The task encountered ");
			e.printStackTrace();
			return StatusCode.EX_NETWORK_ERROR.getCode();
		} catch (TaskException e) {
			log.error("Task encountered an exception", e);
			return StatusCode.EX_UNKNOWN.getCode();
		} catch (DAOException e) {
			log.error("Task cannot persist a result", e);
			return StatusCode.EX_UNKNOWN.getCode();
		} catch (Throwable t) {
			log.error("Task encountered an unknown exception", t);
			return StatusCode.EX_UNKNOWN.getCode();
		} finally {
			tearDown();
		}

		return StatusCode.EX_OK.getCode();
	}

	private void initialize() {
		this.id = System.getenv(TaskPropertyNames.TASK_ID);
		this.contextId = System.getenv(TaskPropertyNames.CONTEXT_ID);
		this.benchmarkId = System.getenv(TaskPropertyNames.BENCHMARK_ID);
		ResultFacadeFactory.setTaskId(id);
		ResultFacadeFactory.setContextId(contextId);
		ResultFacadeFactory.setBenchmarkId(benchmarkId);

		try {
			Messages.send(String.format("%s#%s", TaskMessageType.TASK_RUNNING, id));
		} catch (MessagingException e) {
			// message passing does not work, log it with stderr ...
			System.err.println("Cannot send \"i'm running\" message");
		}
	}

	private void tearDown() {
		try {
			ResultFacadeFactory.quit();
		} catch (MessagingException e) {
			log.error("Failed to release results facade.");
		}

		try {
			Messages.terminate();
		} catch (MessagingException e) {
			System.err.println("Failed to release Messaging");
		}
	}
}
