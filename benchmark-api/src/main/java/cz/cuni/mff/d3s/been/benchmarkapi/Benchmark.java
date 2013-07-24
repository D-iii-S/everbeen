package cz.cuni.mff.d3s.been.benchmarkapi;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.cuni.mff.d3s.been.core.jaxb.BindingParser;
import cz.cuni.mff.d3s.been.core.jaxb.XSD;
import cz.cuni.mff.d3s.been.core.task.Properties;
import cz.cuni.mff.d3s.been.core.task.Property;
import cz.cuni.mff.d3s.been.core.task.TaskContextDescriptor;
import cz.cuni.mff.d3s.been.mq.MessagingException;
import cz.cuni.mff.d3s.been.taskapi.Task;

/**
 * @author Kuba Brecka
 */
public abstract class Benchmark extends Task {

	/** Logging */
	private static final Logger log = LoggerFactory.getLogger(Benchmark.class);

	/** Local storage which is synced to been */
	private Map<String, String> storage;

	private BenchmarkRequestor benchmarkRequestor;

	/**
	 * Method which drives the benchmark by generating Contexts.
	 * 
	 * The context is submitted and run by BEEN.
	 * 
	 * To indicated end of the benchmark null must be returned.
	 * 
	 * @return TaskContextDescriptor to be submitted, or null to indicate end of
	 *         the benchmark
	 * 
	 * @throws BenchmarkException
	 */
	public abstract TaskContextDescriptor generateTaskContext() throws BenchmarkException;

	/**
	 * Retrieves a value for the given key from benchmark wide storage.
	 * 
	 * If no value is found, null is returned instead.
	 * 
	 * The values are preserved among runs of the generator for the same
	 * benchmark.
	 * 
	 * @param key
	 *          identification of the value
	 * @return benchmark-wide value for the given key if it exists, null otherwise
	 */
	protected String storageGet(String key) {
		return storage.get(key);
	}

	/**
	 * Retrieves a value for the given key from benchmark wide storage.
	 * 
	 * If no value is found, defaultValue is returned instead.
	 * 
	 * The values are preserved among runs of the generator for the same
	 * benchmark.
	 * 
	 * 
	 * @param key
	 *          identification of the value
	 * @param defaultValue
	 *          value returned if no benchmark-value is found
	 * @return benchmark-wide value for the given key if it exists, defaultValue
	 *         otherwise
	 */
	protected String storageGet(String key, String defaultValue) {
		return storage.get(key) != null ? storage.get(key) : defaultValue;
	}

	/**
	 * Stores a value with the given key to Benchmark-wide storage.
	 * 
	 * The values are preserved among runs of the generator for the same benchmark
	 * and can be retrieved with {@link Benchmark#storageGet(String)} or
	 * {@link Benchmark#storageGet(String, String)}
	 * 
	 * @param key
	 *          identification of the value
	 * @param value
	 *          value to store to the benchmark-wide storage
	 */
	protected void storageSet(String key, String value) {
		storage.put(key, value);
	}

	@Override
	public void run(String[] args) {
		try {
			benchmarkRequestor = BenchmarkRequestor.create();
		} catch (MessagingException e) {
			log.error("Could not initialize checkpoint requestor", e);
			return;
		}

		try {
			processContexts();
		} finally {
			try {
				benchmarkRequestor.close();
			} catch (MessagingException e) {
				log.error("Could not close checkpoint requestor", e);
			}
		}
	}

	private void processContexts() {
		try {
			this.storage = benchmarkRequestor.storageRetrieve(getBenchmarkId());
		} catch (TimeoutException e) {
			throw new RuntimeException(e.getMessage(), e);
		}

		while (true) {
			TaskContextDescriptor taskContextDescriptor = null;
			try {
				taskContextDescriptor = generateTaskContext();

				if (taskContextDescriptor == null) {
					return;
				}
			} catch (BenchmarkException e) {
				throw new RuntimeException("Cannot generate task context.", e);
			}

			try {
				// First save the local storage, before submitting the context
				benchmarkRequestor.storagePersist(getBenchmarkId(), storage);

				log.debug("Submitting task context descriptor.");
				String taskContextId = benchmarkRequestor.contextSubmit(taskContextDescriptor, getBenchmarkId());
				log.debug("Task context descriptor with ID '{}' submitted", taskContextId);

				benchmarkRequestor.contextWait(taskContextId);
				log.debug("Task context '{}' finished.", taskContextId);
			} catch (TimeoutException e) {
				throw new RuntimeException(e.getMessage(), e);
				// TODO this must be handled with greater care!
			}
		}
	}

	/**
	 * Use {@link ContextBuilder} instead.
	 */
	@Deprecated
	public TaskContextDescriptor getTaskContextFromResource(String resourceName) throws BenchmarkException {

		try {
			InputStream inputStream = this.getClass().getResourceAsStream(resourceName);

			TaskContextDescriptor taskContextDescriptor = null;

			BindingParser<TaskContextDescriptor> bindingComposer = XSD.TASK_CONTEXT_DESCRIPTOR.createParser(TaskContextDescriptor.class);
			taskContextDescriptor = bindingComposer.parse(inputStream);

			return taskContextDescriptor;
		} catch (Exception e) {
			throw new BenchmarkException("Cannot read resource.", e);
		}

	}

	/**
	 * Use {@link ContextBuilder} instead.
	 */
	@Deprecated
	public void setTaskContextProperty(TaskContextDescriptor descriptor, String key, String value) {
		Property p = new Property();
		p.setName(key);
		p.setValue(value);
		if (!descriptor.isSetProperties())
			descriptor.setProperties(new Properties());
		descriptor.getProperties().getProperty().add(p);
	}
}
