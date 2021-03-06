/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.splicemachine.db.impl.services.cache;

class CacheStat {

	/*
	** Fields
	*/
	protected int findHit;
	protected int findMiss;
	protected int findFault;
	protected int findCachedHit;
	protected int findCachedMiss;
	protected int create;
	protected int ageOut;
	protected int cleanAll;
	protected int remove;
	protected long initialSize;
	protected long maxSize;
	protected long currentSize;

	protected long[] data;

	public long[] getStats() 
	{
		if (data == null)
			data = new long[14];

		data[0] = findHit + findMiss;
		data[1] = findHit;
		data[2] = findMiss;
		data[3] = findFault;
		data[4] = findCachedHit + findCachedMiss;
		data[5] = findCachedHit;
		data[6] = findCachedMiss;
		data[7] = create;
		data[8] = ageOut;
		data[9] = cleanAll;
		data[10] = remove;
		data[11] = initialSize;
		data[12] = maxSize;
		data[13] = currentSize;

		return data;
	}

	public void reset()
	{
		findHit = 0;
		findMiss = 0;
		findFault = 0;
		findCachedHit = 0;
		findCachedMiss = 0;
		create = 0;
		ageOut = 0;
		cleanAll = 0;
		remove = 0;
	}
}
