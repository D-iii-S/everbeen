
/*
 *  BEEN: Benchmarking Environment
 *  ==============================
 *
 *  File author: Jiri Tauber
 *
 *  GNU Lesser General Public License Version 2.1
 *  ---------------------------------------------
 *  Copyright (C) 2004-2006 Distributed Systems Research Group,
 *  Faculty of Mathematics and Physics, Charles University in Prague
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License version 2.1, as published by the Free Software Foundation.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 *  MA  02111-1307  USA
 */
package cz.cuni.mff.been.taskmanager;

import java.io.Serializable;

/**
 * Callback interface used when listening for events in the Task Manager
 * (currently only task end). Those who are interested in the events must
 * register using {@link TaskManagerInterface#registerEventListener(TaskEventListener)}
 * method and later unregister using
 * <code>TaskManagerInterface.unregisterEventListener</code>.
 * 
 * @author Jiri Tauber
 */
public interface TaskEventListener extends Serializable {
	/**
	 * Called when a task is finished.
	 * 
	 * @param taskId The finished task ID
	 * @param contextId The finished task context 
	 */
	void TaskFinished(String taskId, String contextId);

}