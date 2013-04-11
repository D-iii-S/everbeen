package cz.cuni.mff.d3s.been.mq;

import static cz.cuni.mff.d3s.been.mq.TestParams.TEST_TIMEOUT;
import static junit.framework.Assert.assertEquals;

import org.junit.Test;

/**
 * Basic set of tests for message queues used in BEEN.
 * 
 * Tests should subclass and implement {@link #getQueue()}.
 * 
 * @author Martin Sixta
 */
public abstract class BasicQueueTests {

	/**
	 * 
	 * Returns
	 * 
	 * @return message queue used for tests.
	 * @throws MessagingException
	 *           when a queue cannot be created.
	 */
	protected abstract IMessageQueue<String> getQueue() throws MessagingException;

	/**
	 * Tests correct termination of a queue. Since there is only receiver the
	 * termination should not block.
	 * 
	 * @throws MessagingException
	 */
	@Test(timeout = TEST_TIMEOUT)
	public void testQueueTerminationWithNoSenders() throws MessagingException {
		IMessageQueue<String> queue = getQueue();
		IMessageReceiver<String> receiver = queue.getReceiver();

		queue.terminate();
	}

	/**
	 * Tests correct termination of a queue with readers.
	 * 
	 * @throws MessagingException
	 */
	@Test(timeout = TEST_TIMEOUT)
	public void testQueueTerminationWithSenders() throws MessagingException {
		IMessageQueue<String> queue = getQueue();
		IMessageReceiver<String> receiver = queue.getReceiver();
		IMessageSender<String> sender1 = queue.createSender();
		IMessageSender<String> sender2 = queue.createSender();

		sender1.close();
		sender2.close();
		queue.terminate();

	}

	/**
	 * Tests simple push-pull on a queue.
	 * 
	 * @throws MessagingException
	 */
	@Test(timeout = TEST_TIMEOUT)
	public void testPushPull() throws MessagingException {
		IMessageQueue<String> queue = getQueue();
		IMessageReceiver<String> receiver = queue.getReceiver();
		IMessageSender<String> sender1 = queue.createSender();

		final String message = "MESSAGE";

		sender1.send(message);
		String receivedMessage = receiver.receive();

		assertEquals(message, receivedMessage);

		sender1.close();
		queue.terminate();

	}

	/**
	 * Tests simple push-pull on a queue with receiver and sender running in
	 * different thread.
	 * 
	 * @throws MessagingException
	 */
	@Test(timeout = TEST_TIMEOUT)
	public void testMultiThreadedPushPull() throws MessagingException, InterruptedException {
		IMessageQueue<String> queue = getQueue();
		final IMessageReceiver<String> receiver = queue.getReceiver();
		final IMessageSender<String> sender = queue.createSender();
		final String message = "MESSAGE";

		ReceiverThread<String> receiverThread = new ReceiverThread<>(receiver);
		SenderThread<String> senderThread = new SenderThread<>(sender, message);

		receiverThread.start();
		senderThread.start();

		senderThread.join();
		receiverThread.join();

		assertEquals(message, receiverThread.getReceivedMessage());

		sender.close();
		queue.terminate();

	}

}