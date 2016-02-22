package cz.cuni.mff.d3s.been.manager.msg;

import static cz.cuni.mff.d3s.been.core.task.TaskState.ABORTED;
import static cz.cuni.mff.d3s.been.core.task.TaskState.FINISHED;

import cz.cuni.mff.d3s.been.cluster.context.ClusterContext;
import cz.cuni.mff.d3s.been.core.task.TaskEntry;
import cz.cuni.mff.d3s.been.core.task.TaskState;
import cz.cuni.mff.d3s.been.manager.action.Actions;
import cz.cuni.mff.d3s.been.manager.action.TaskAction;
import cz.cuni.mff.d3s.been.manager.selector.NoRuntimeFoundException;
import cz.cuni.mff.d3s.been.manager.selector.RuntimeSelectors;

/**
 * Message which checks whether a task can be scheduled.
 * 
 * If a task can be scheduled an appropriate action should take place.
 * 
 * @author Martin Sixta
 */
@SuppressWarnings("serial")
public class CheckSchedulabilityMessage implements TaskMessage {
	private final TaskEntry entry;

	/**
	 * Creates new CheckSchedulabilityMessage
	 * 
	 * @param entry
	 *          targeted entry
	 */
	public CheckSchedulabilityMessage(TaskEntry entry) {
		this.entry = entry;
	}

	@Override
	public TaskAction createAction(ClusterContext ctx) {

		if (isWaitingOnTask(ctx)) {
			return Actions.createNullAction();
		}

		try {
			RuntimeSelectors.fromEntry(entry, ctx).select();
			return Actions.createScheduleTaskAction(ctx, entry);
		} catch (NoRuntimeFoundException e) {
			// do nothing, will have to wait
		}

		return Actions.createNullAction();
	}

	/**
	 * Checks whether the task is waiting on another task.
	 * 
	 * @param ctx Connection to the cluster
	 * @return Returns true if the task is waiting on another task that is not yet done
	 */
	private boolean isWaitingOnTask(final ClusterContext ctx) {
		String dependencyString = entry.getTaskDependency();
		
		// If the dependency attribute is empty, we obviously do not wait on anyone.
		if (dependencyString == null || dependencyString.isEmpty()) return (false);

		// We have a specified dependency attribute, check the state of that task.
		TaskEntry dependencyTask = ctx.getTasks().getTask(dependencyString);
		if (dependencyTask == null) return (false); 
		TaskState dependencyState = dependencyTask.getState();
		boolean dependencyDone = (dependencyState == ABORTED) || (dependencyState == FINISHED); 
		return (!dependencyDone);
	}
}
