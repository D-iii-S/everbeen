package cz.cuni.mff.d3s.been.hostruntime;

import cz.cuni.mff.d3s.been.cluster.action.Action;
import cz.cuni.mff.d3s.been.cluster.action.Actions;
import cz.cuni.mff.d3s.been.cluster.context.ClusterContext;
import cz.cuni.mff.d3s.been.socketworks.SocketHandlerException;
import cz.cuni.mff.d3s.been.socketworks.twoway.ReadReplyHandler;
import cz.cuni.mff.d3s.been.socketworks.twoway.Replies;
import cz.cuni.mff.d3s.been.task.checkpoints.CheckpointRequest;
import cz.cuni.mff.d3s.been.util.JsonException;

/**
 * Handler of CheckPoint requests.
 * 
 * @author Radek Mácha
 */
public class CheckpointHandler implements ReadReplyHandler {

	private final ClusterContext ctx;
	private final HandlerRecycler recycler;

	private CheckpointHandler(ClusterContext ctx, HandlerRecycler recycler) {
		this.ctx = ctx;
		this.recycler = recycler;
	}

	/**
	 * Create a {@link CheckpointHandler}, providing it a way to recycle itself
	 * after service.
	 * 
	 * @param ctx
	 *          Current cluster context (used to listening for checkpoints)
	 * @param recycler
	 *          Recycler to use after service
	 * 
	 * @return The {@link CheckpointHandler}
	 */
	static final CheckpointHandler create(ClusterContext ctx, HandlerRecycler recycler) {
		return new CheckpointHandler(ctx, recycler);
	}

	@Override
	public String handle(String message) throws SocketHandlerException, InterruptedException {
		CheckpointRequest request = null;

		try {
			request = CheckpointRequest.fromJson(message);
		} catch (JsonException e) {
			return Replies.createErrorReply("Cannot deserialize").toJson();
		}

		Action action = Actions.createAction(request, ctx);
		return action.handle().toJson();
	}

	@Override
	public void markAsRecyclable() {
		if (recycler != null) {
			recycler.recycle(this);
		}
	}

}
