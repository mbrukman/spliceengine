package com.splicemachine.si.impl;

import com.splicemachine.si.api.TxnSupplier;
import org.apache.hadoop.hbase.filter.Filter;

import java.io.IOException;

public interface TxnFilter<Data> {
    Filter.ReturnCode filterKeyValue(Data keyValue) throws IOException;
    void nextRow();
    Data produceAccumulatedKeyValue();
    boolean getExcludeRow();
	CellType getType(Data keyValue) throws IOException;
	DataStore getDataStore();

    boolean isPacked();

    TxnSupplier getTxnSupplier();
}
