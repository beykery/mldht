/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

/**
 * @author Damokles
 *
 */
public interface DatabaseStats {

	/**
	 * @return the itemCount
	 */
	public int getItemCount();

	/**
	 * @return the keyCount
	 */
	public int getKeyCount();
}
