package cz.cuni.mff.d3s.been.hostruntime;

import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.IQueue;

import cz.cuni.mff.d3s.been.cluster.Names;
import cz.cuni.mff.d3s.been.cluster.context.ClusterContext;
import cz.cuni.mff.d3s.been.mq.IMessageQueue;
import cz.cuni.mff.d3s.been.mq.IMessageReceiver;
import cz.cuni.mff.d3s.been.mq.Messaging;
import cz.cuni.mff.d3s.been.mq.MessagingException;
import cz.cuni.mff.d3s.been.results.ResultCarrier;

/**
 * This dispatcher recieves serialized results from the Task and queues them up
 * to the results queue, from which they are processed further.
 * 
 * @author Martin Sixta
 */
final class ResultsDispatcher extends Thread {

	private static final Logger log = LoggerFactory.getLogger(ResultsDispatcher.class);

	private final ClusterContext clusterContext;
	private final String hostName;
	private final IQueue<ResultCarrier> resultQueue;
	private final ObjectMapper objectMapper;
	private final ObjectReader resultReader;

	final IMessageQueue<String> resultsMessages;
	IMessageReceiver<String> receiver;

	ResultsDispatcher(ClusterContext clusterContext, String host) {
		this.clusterContext = clusterContext;
		this.hostName = host;
		this.resultsMessages = Messaging.createTcpQueue(host);
		this.resultQueue = clusterContext.getQueue(Names.RESULT_QUEUE_NAME);
		this.objectMapper = new ObjectMapper();
		this.resultReader = objectMapper.reader(ResultCarrier.class);
	}
	public int getPort() {
		return receiver.getPort();
	}

	public void init() throws MessagingException {
		receiver = resultsMessages.getReceiver();
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				final ResultCarrier rc = resultReader.readValue(receiver.receive());
				if (resultQueue.add(rc)) {
					log.debug("Queued result {}", rc.toString());
				} else {
					log.warn("Could not put result {} to queue.", rc.toString());
					// TODO buffer and try next time
				}
			} catch (MessagingException e) {
				log.error("Cannot receive result:", e);
			} catch (IOException e) {
				log.error("Cannot deserialize result carrier:", e);
			}
		}
		resultsMessages.terminate();
	}
}