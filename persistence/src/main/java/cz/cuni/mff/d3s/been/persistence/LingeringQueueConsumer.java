package cz.cuni.mff.d3s.been.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.IQueue;

public class LingeringQueueConsumer<T> extends QueueConsumer<T> {

	private static final Logger log = LoggerFactory.getLogger(LingeringQueueConsumer.class);

	LingeringQueueConsumer(
			IQueue<T> queue,
			PersistAction<T> pa) {
		super(queue, pa);
	}

	@Override
	public void run() {
		try {
			innerRun();
		} catch (Throwable e) {
			log.error(e.getMessage(), e);
		}
	}

	private void innerRun() {
		log.debug("Thread starting.");
		while (!Thread.currentThread().isInterrupted()) {
			try {
				final T item = queue.take();
				if (item == null) {
					// The documentation doesn't say this, but Hazelcast queue yields null
					// rather than throwing an exception when interrupted
					// This throw is not entirely correct, as internally, Hazelcast
					// only uses a big timeout for take(). However, the timeout being
					// Long.MAX_VALUE, this implementation is unlikely to return null in any
					// other circumstances than during interruption.
					throw new InterruptedException("Queue take yielded null item.");
				}
				log.debug("Taken item {} from queue", item);
				persist(item);
			} catch (InterruptedException e) {
				// the take has been interrupted - a signal that this repository is being terminated
				log.debug("Queue take interrupted..");
				Thread.currentThread().interrupt();
			}
		}
		log.debug("Thread terminating.");
	}
}
