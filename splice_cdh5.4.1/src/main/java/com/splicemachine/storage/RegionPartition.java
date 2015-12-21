package com.splicemachine.storage;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.splicemachine.metrics.MetricFactory;
import com.splicemachine.metrics.Metrics;
import com.splicemachine.si.constants.SIConstants;
import com.splicemachine.storage.util.MeasuredListScanner;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.regionserver.HRegion;
import org.apache.hadoop.hbase.regionserver.HRegionUtil;
import org.apache.hadoop.hbase.regionserver.OperationStatus;
import org.apache.hadoop.hbase.regionserver.RegionScanner;
import org.apache.hadoop.hbase.util.Bytes;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;

/**
 * Representation of a single HBase Region as a Partition.
 *
 * A lot of these methods shouldn't (and generally won't) be called directly, because they will
 * result in an excessive number of objects being created. For example, instead of relying on
 * {@link #get(DataGet,DataResult)} to get the latest version of all the columns, instead call
 * {@link #getLatest(byte[], DataResult)}, which will save on the creation of a DataGet object, while still
 * allowing re-use of the DataResult object.
 *
 * Nonetheless, all methods are properly implemented in a Thread-safe manner (they must, in order to pass SI
 * acceptance tests).
 *
 * @author Scott Fines
 *         Date: 12/17/15
 */
@ThreadSafe
public class RegionPartition implements Partition{
    private final HRegion region;

    public RegionPartition(HRegion region){
        this.region=region;
    }

    @Override
    public String getTableName(){
        return region.getTableDesc().getTableName().getNameAsString();
    }

    @Override
    public String getName(){
        return region.getRegionNameAsString();
    }


    /*Lifecycle management*/
    @Override public void startOperation() throws IOException{ region.startRegionOperation(); }
    @Override public void closeOperation() throws IOException{ region.closeRegionOperation(); }
    @Override public boolean isClosed(){ return region.isClosed(); }
    @Override public boolean isClosing(){ return region.isClosing(); }
    @Override
    public void close() throws IOException{
        //no-op for regions
    }

    /*Single row access*/
    @Override
    public DataResult get(DataGet get,DataResult previous) throws IOException{
        assert get instanceof HGet: "Programmer error: improper type!";

        Result result=region.get(((HGet)get).unwrapDelegate());
        if(previous==null)
            previous = new HResult();
        ((HResult)previous).set(result);
        return previous;
    }

    @Override
    public DataResult getFkCounter(byte[] key,DataResult previous) throws IOException{
        Get g = new Get(key);
        g.addColumn(SIConstants.DEFAULT_FAMILY_BYTES,SIConstants.SNAPSHOT_ISOLATION_FK_COUNTER_COLUMN_BYTES);

        Result r = region.get(g);
        if(previous==null)
            previous = new HResult(r);
        else{
            ((HResult)previous).set(r);
        }
        return previous;
    }

    @Override
    public DataResult getLatest(byte[] key,DataResult previous) throws IOException{
        Get g = new Get(key);
        g.setMaxVersions(1);

        Result result=region.get(g);
        if(previous==null)
            previous = new HResult(result);
        else{
            ((HResult)previous).set(result);
        }
        return previous;
    }

    @Override
    public DataResult getLatest(byte[] rowKey,byte[] family,DataResult previous) throws IOException{
        Get g = new Get(rowKey);
        g.setMaxVersions(1);
        g.addFamily(family);

        Result result=region.get(g);
        if(previous==null)
            previous = new HResult(result);
        else{
            ((HResult)previous).set(result);
        }
        return previous;
    }

    /*Multi-row access*/
    @Override
    public DataScanner openScanner(DataScan scan) throws IOException{
        return openScanner(scan,Metrics.noOpMetricFactory());
    }

    @Override
    public DataScanner openScanner(DataScan scan,MetricFactory metricFactory) throws IOException{
        assert scan instanceof HScan: "Programmer error: improper type!";

        Scan s=((HScan)scan).unwrapDelegate();
        RegionScanner scanner=region.getScanner(s);

        return new RegionDataScanner(scanner,metricFactory);
    }

    @Override
    public DataResultScanner openResultScanner(DataScan scan) throws IOException{
        return openResultScanner(scan,Metrics.noOpMetricFactory());
    }

    @Override
    public DataResultScanner openResultScanner(DataScan scan,MetricFactory metricFactory) throws IOException{
        assert scan instanceof HScan: "Programmer error: improper type!";

        Scan s=((HScan)scan).unwrapDelegate();
        RegionScanner scanner=region.getScanner(s);

        //TODO -sf- massage the batch size properly
        return new RegionResultScanner(s.getBatch(),new MeasuredListScanner(scanner,metricFactory));
    }

    /*Data mutation methods*/
    @Override
    public void put(DataPut put) throws IOException{
        assert put instanceof HPut: "Programmer error: incorrect put type!";

        Put p = ((HPut)put).unwrapDelegate();

        region.put(p);
    }

    @Override
    public Iterator<MutationStatus> writeBatch(DataPut[] toWrite) throws IOException{
        if(toWrite==null||toWrite.length<=0) return Collections.emptyIterator();
        Mutation[] mutations = new Mutation[toWrite.length];

        for(int i=0;i<toWrite.length;i++){
            mutations[i] = ((HMutation)toWrite[i]).unwrapHbaseMutation();
        }
        OperationStatus[] operationStatuses=region.batchMutate(mutations);
        final HMutationStatus resultStatus = new HMutationStatus();
        return Iterators.transform(Iterators.forArray(operationStatuses),new Function<OperationStatus, MutationStatus>(){
            @Override
            public MutationStatus apply(OperationStatus input){
                resultStatus.set(input);
                return resultStatus;
            }
        });
    }

    @Override
    public void increment(byte[] rowKey,byte[] family,byte[] qualifier,long amount) throws IOException{
        Increment incr = new Increment(rowKey);
        incr.addColumn(family,qualifier,amount);
        region.increment(incr);
    }
    @Override
    public void delete(DataDelete delete) throws IOException{
        Delete d = ((HDelete)delete).unwrapDelegate();

        region.delete(d);
    }

    @Override
    public void mutate(DataMutation put) throws IOException{
        if(put instanceof HPut)
            region.put(((HPut)put).unwrapDelegate());
        else
            region.delete(((HDelete)put).unwrapDelegate());
    }

    @Override
    public Lock getRowLock(byte[] key,int keyOff,int keyLen) throws IOException{
        if(keyOff==0 && keyLen==key.length)
            return new HLock(region,key);
        else return new HLock(region,Bytes.copy(key,keyOff,keyLen));
    }

    /*Data range ownership methods*/
    @Override public byte[] getStartKey(){ return region.getStartKey(); }
    @Override public byte[] getEndKey(){ return region.getEndKey(); }

    @Override public boolean containsRow(byte[] row){ return region.getRegionInfo().containsRow(row); }

    @Override
    public boolean containsRow(byte[] row,int offset,int length){
        if(offset==0 && length==row.length)
            return region.getRegionInfo().containsRow(row);
        else
            return region.getRegionInfo().containsRow(Bytes.copy(row,offset,length));
    }

    @Override
    public boolean containsRange(byte[] start,byte[] stop){
        return region.getRegionInfo().containsRange(start,stop);
    }

    @Override
    public boolean containsRange(byte[] start,int startOff,int startLen,byte[] stop,int stopOff,int stopLen){
        byte[] s;
        byte[] e;
        if(startOff==0 && startLen==start.length)
            s = start;
        else s = Bytes.copy(start,startOff,startLen);

        if(stopOff==0 && stopLen==stop.length)
            e = stop;
        else e = Bytes.copy(stop,stopOff,stopLen);

        return region.getRegionInfo().containsRange(s,e);
    }

    @Override
    public void writesRequested(long writeRequests){
        HRegionUtil.updateWriteRequests(region,writeRequests);
    }

    @Override
    public void readsRequested(long readRequests){
        HRegionUtil.updateReadRequests(region,readRequests);
    }

    public HRegion unwrapDelegate(){
        return region;
    }
}
