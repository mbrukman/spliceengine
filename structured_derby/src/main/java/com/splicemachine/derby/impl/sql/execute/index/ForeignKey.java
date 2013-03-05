package com.splicemachine.derby.impl.sql.execute.index;

import com.splicemachine.constants.HBaseConstants;
import org.apache.hadoop.hbase.DoNotRetryIOException;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * Representation of a ForeignKey Constraint.
 *
 * @author Scott Fines
 * Created on: 2/28/13
 */
public class ForeignKey implements Constraint{
    public static final byte[] FOREIGN_KEY_FAMILY = "fk".getBytes();
    public static final byte[] FOREIGN_KEY_COLUMN = "fk".getBytes();
    /*
     * The columns in the foreign key table to get values for
     */
    private final BitSet fkCols;

    /*
     * The table holding the primary key that this Foreign Key is referencing
     */
    private final String refTableName;
    //for performance efficiency
    private final byte[] refTableBytes;
    private final TableSource tableSource;

    private final byte[] mainTableBytes;

    public ForeignKey(String refTableName,String mainTable,BitSet fkCols,TableSource tableSource)  {
        this.fkCols = fkCols;
        this.refTableName = refTableName;
        this.refTableBytes = Bytes.toBytes(refTableName);
        this.tableSource = tableSource;
        this.mainTableBytes = Bytes.toBytes(mainTable);
    }

    @Override
    public boolean validate(Put put) throws IOException{
        Get get = new Get(Constraints.getReferencedRowKey(put, fkCols));
        get.addFamily(HBaseConstants.DEFAULT_FAMILY_BYTES);

        return tableSource.getTable(refTableBytes).exists(get);
    }

    @Override
    public boolean validate(Delete delete) throws IOException{
       //foreign keys are validated on the PK side of deletes, so nothing to validate
        return true;
    }

    public void updateForeignKey(Put put) throws IOException{
        byte[] referencedRowKey = Constraints.getReferencedRowKey(put, fkCols);
        if(referencedRowKey==null)
            throw new DoNotRetryIOException("Foreign Key Constraint Violation");

        tableSource.getTable(refTableBytes).incrementColumnValue(referencedRowKey,
                FOREIGN_KEY_FAMILY,FOREIGN_KEY_COLUMN,1l);
    }

    public void updateForeignKey(Delete delete) throws IOException{
        Get get = new Get(delete.getRow());
        for(int fk = fkCols.nextSetBit(0);fk!=-1;fk=fkCols.nextSetBit(fk+1)){
            get.addColumn(HBaseConstants.DEFAULT_FAMILY_BYTES,Integer.toString(fk).getBytes());
        }
        HTableInterface table = tableSource.getTable(mainTableBytes);
        Result result = table.get(get);
        if(result==null){
            //don't know why this would be, we're about to delete it!
            //oh well, guess we don't have to do anything
            return;
        }
        byte[] referencedRowKey = Constraints.getReferencedRowKey(
                result.getFamilyMap(HBaseConstants.DEFAULT_FAMILY_BYTES), fkCols);
        if(referencedRowKey==null) return; //nothing to update!
        table.incrementColumnValue(FOREIGN_KEY_FAMILY,FOREIGN_KEY_COLUMN,referencedRowKey,-1l);
    }

}
