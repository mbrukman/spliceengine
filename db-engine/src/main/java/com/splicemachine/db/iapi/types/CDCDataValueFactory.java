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

package com.splicemachine.db.iapi.types;

import java.util.Properties;
import com.splicemachine.db.iapi.services.info.JVMInfo;
import com.splicemachine.db.iapi.services.monitor.ModuleSupportable;
import com.splicemachine.db.iapi.error.StandardException;

/**
 * DataValueFactory implementation for J2ME/CDC/Foundation.
 * Cannot use SQLDecimal since that requires java.math.BigDecimal.
 * Uses BigIntegerDecimal for DECIMAL support.
 *
 * @see DataValueFactory
 */

public class CDCDataValueFactory extends DataValueFactoryImpl
	implements ModuleSupportable
{
     /**
	 *     Make the constructor public.
	 *
	 */
	public CDCDataValueFactory() {
	}
	
	/* (non-Javadoc)
	 * @see com.splicemachine.db.iapi.services.monitor.ModuleSupportable#canSupport(java.util.Properties)
	 */
	public boolean canSupport(Properties properties) {
		return JVMInfo.J2ME;
	}
	public NumberDataValue getDecimalDataValue(Long value,
			NumberDataValue previous) throws StandardException {
		if (previous == null)
			previous = new BigIntegerDecimal();

		previous.setValue(value);
		return previous;
	}

	public NumberDataValue getDecimalDataValue(String value)
			throws StandardException {
		NumberDataValue ndv = new BigIntegerDecimal();

		ndv.setValue(value);
		return ndv;
	}

	public NumberDataValue getNullDecimal(NumberDataValue dataValue) {
		if (dataValue == null) {
			return new BigIntegerDecimal();
		} else {
			dataValue.setToNull();
			return dataValue;
		}
	}
}
