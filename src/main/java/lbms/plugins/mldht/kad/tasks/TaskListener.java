/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.tasks;

/**
 * @author Damokles
 *
 */
public interface TaskListener {
	/**
	 * The task is finsihed.
	 * @param t The Task
	 */
	void finished (Task t);
}
