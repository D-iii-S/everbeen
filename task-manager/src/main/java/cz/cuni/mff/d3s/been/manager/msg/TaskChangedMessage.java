package cz.cuni.mff.d3s.been.manager.msg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.cuni.mff.d3s.been.cluster.context.ClusterContext;
import cz.cuni.mff.d3s.been.core.task.TaskEntry;
import cz.cuni.mff.d3s.been.core.task.TaskState;
import cz.cuni.mff.d3s.been.core.task.TaskType;
import cz.cuni.mff.d3s.been.manager.action.Actions;
import cz.cuni.mff.d3s.been.manager.action.TaskAction;

/**
 * Message that handles changes of task states.
 * 
 * @author Martin Sixta
 */
@SuppressWarnings("serial")
final class TaskChangedMessage extends AbstractEntryTaskMessage {

	private static final Logger log = LoggerFactory.getLogger(TaskChangedMessage.class);

	/**
	 * Creates TaskChangedMessage
	 * 
	 * @param entry
	 *          targeted task entry
	 */
	public TaskChangedMessage(TaskEntry entry) {
		super(entry);
	}

	@Override
	public TaskAction createAction(ClusterContext ctx) {
		TaskState state = this.getEntry().getState();

		if (state == TaskState.SUBMITTED || state == TaskState.WAITING)
			return Actions.createScheduleTaskAction(ctx, getEntry());

		if (this.getEntry().getTaskDescriptor().getType() == TaskType.BENCHMARK) {
			if (state == TaskState.ABORTED) {
				// a benchmark generator task has failed
				log.info("BENCHMARK GENERATOR TASK ID {} FAILED", this.getEntry().getId());
				return Actions.createResubmitBenchmarkAction(ctx, getEntry());
			}
		}

		if (state == TaskState.FINISHED || state == TaskState.ABORTED) {
			return Actions.createTaskContextCheckerAction(ctx, getEntry());
		}

		return null;
	}
}
