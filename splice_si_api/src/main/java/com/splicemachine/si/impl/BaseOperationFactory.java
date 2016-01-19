package com.splicemachine.si.impl;

import com.carrotsearch.hppc.LongArrayList;
import com.splicemachine.encoding.MultiFieldDecoder;
import com.splicemachine.encoding.MultiFieldEncoder;
import com.splicemachine.si.api.data.ExceptionFactory;
import com.splicemachine.si.api.data.SDataLib;
import com.splicemachine.si.api.data.TxnOperationFactory;
import com.splicemachine.si.api.txn.Txn;
import com.splicemachine.si.api.txn.TxnView;
import com.splicemachine.si.impl.txn.ActiveWriteTxn;
import com.splicemachine.si.impl.txn.ReadOnlyTxn;
import com.splicemachine.storage.Attributable;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import static com.splicemachine.si.constants.SIConstants.*;


/**
 * @author Scott Fines
 *         Date: 7/8/14
 */
public abstract class BaseOperationFactory<OperationWithAttributes,
        Data,
        Delete extends OperationWithAttributes,
        Get extends OperationWithAttributes,
        Put extends OperationWithAttributes,
        Scan extends OperationWithAttributes> implements TxnOperationFactory<OperationWithAttributes, Get, Scan>{
    private SDataLib<OperationWithAttributes, Data, Get, Scan> dataLib;
    private ExceptionFactory exceptionLib;

    public BaseOperationFactory(SDataLib<OperationWithAttributes, Data, Get, Scan> dataLib,
                                ExceptionFactory exceptionFactory){
        this.dataLib = dataLib;
        this.exceptionLib = exceptionFactory;
    }


    @Override
    public Scan newScan(TxnView txn){
        return newScan(txn,false);
    }

    @Override
    public Scan newScan(TxnView txn,boolean isCountStar){
        Scan scan=dataLib.newScan();
        if(txn==null){
            makeNonTransactional(scan);
            return scan;
        }
        encodeForReads(scan,txn,isCountStar);
        return scan;
    }

    @Override
    public Get newGet(TxnView txn,byte[] rowKey){
        Get get=dataLib.newGet(rowKey);
        if(txn==null){
            makeNonTransactional(get);
            return get;
        }
        encodeForReads(get,txn,false);
        return get;
    }

    @Override
    public TxnView fromReads(Attributable op) throws IOException{
        byte[] txnData = op.getAttribute(SI_TRANSACTION_ID_KEY);
        if(txnData==null) return null;
        return decode(txnData,0,txnData.length);
    }

    @Override
    public TxnView fromWrites(Attributable op) throws IOException{
        byte[] txnData=op.getAttribute(SI_TRANSACTION_ID_KEY);
        if(txnData==null) return null; //non-transactional
        return fromWrites(txnData,0,txnData.length);
    }

    @Override
    public TxnView fromWrites(byte[] data,int off,int length) throws IOException{
        if(length<=0) return null; //non-transactional
        MultiFieldDecoder decoder=MultiFieldDecoder.wrap(data,off,length);
        long beginTs=decoder.decodeNextLong();
        boolean additive=decoder.decodeNextBoolean();
        Txn.IsolationLevel level=Txn.IsolationLevel.fromByte(decoder.decodeNextByte());
        //throw away the allow reads bit, since we won't care anyway
        decoder.decodeNextBoolean();

        TxnView parent=Txn.ROOT_TRANSACTION;
        while(decoder.available()){
            long id=decoder.decodeNextLong();
            parent=new ActiveWriteTxn(id,id,parent,additive,level);
        }
        return new ActiveWriteTxn(beginTs,beginTs,parent,additive,level);
    }

    @Override
    public TxnView fromReads(byte[] data,int off,int length) throws IOException{
        return decode(data,off,length);
    }

    @Override
    public TxnView readTxn(ObjectInput oi) throws IOException{
        int size=oi.readInt();
        byte[] txnData=new byte[size];
        int readAmt = oi.read(txnData);
        if(readAmt!=size) throw new IOException("Did not read enough bytes!");

        return decode(txnData,0,txnData.length);
    }

    @Override
    public byte[] encode(TxnView txn){
        MultiFieldEncoder encoder=MultiFieldEncoder.create(5)
                .encodeNext(txn.getTxnId())
                .encodeNext(txn.isAdditive())
                .encodeNext(txn.getIsolationLevel().encode())
                .encodeNext(txn.allowsWrites());

        LongArrayList parentTxnIds=LongArrayList.newInstance();
        byte[] build=encodeParentIds(txn,parentTxnIds);
        encoder.setRawBytes(build);
        return encoder.build();
    }

    public TxnView decode(byte[] data,int offset,int length){
        MultiFieldDecoder decoder=MultiFieldDecoder.wrap(data,offset,length);
        long beginTs=decoder.decodeNextLong();
        boolean additive=decoder.decodeNextBoolean();
        Txn.IsolationLevel level=Txn.IsolationLevel.fromByte(decoder.decodeNextByte());
        boolean allowsWrites=decoder.decodeNextBoolean();

        TxnView parent=Txn.ROOT_TRANSACTION;
        while(decoder.available()){
            long id=decoder.decodeNextLong();
            if(allowsWrites)
                parent=new ActiveWriteTxn(id,id,parent,additive,level);
            else
                parent=new ReadOnlyTxn(id,id,level,parent,UnsupportedLifecycleManager.INSTANCE,exceptionLib,additive);
        }
        if(allowsWrites)
            return new ActiveWriteTxn(beginTs,beginTs,parent,additive,level);
        else
            return new ReadOnlyTxn(beginTs,beginTs,level,parent,UnsupportedLifecycleManager.INSTANCE,exceptionLib,additive);
    }

    @Override
    public void writeTxn(TxnView txn,ObjectOutput out) throws IOException{
        byte[] eData= encode(txn);
        out.writeInt(eData.length);
        out.write(eData,0,eData.length);
    }

    @Override
    public void encodeForWrites(Attributable op,TxnView txn) throws IOException{
        if(!txn.allowsWrites())
            throw exceptionLib.readOnlyModification("ReadOnly txn "+txn.getTxnId());
        byte[] data=encode(txn);
        op.addAttribute(SI_TRANSACTION_ID_KEY,data);
        op.addAttribute(SI_NEEDED,SI_NEEDED_VALUE_BYTES);
    }

    @Override
    public void encodeForReads(Attributable op,TxnView txn,boolean isCountStar){
        if(isCountStar)
            op.addAttribute(SI_COUNT_STAR,TRUE_BYTES);
        byte[] data=encode(txn);
        op.addAttribute(SI_TRANSACTION_ID_KEY,data);
        op.addAttribute(SI_NEEDED,SI_NEEDED_VALUE_BYTES);
    }

    protected void makeNonTransactional(Attributable op){
        op.addAttribute(SI_EXEMPT,TRUE_BYTES);
    }

    /******************************************************************************************************************/
        /*private helper functions*/

    private void encodeForReads(OperationWithAttributes op,TxnView txn,boolean isCountStar){
        if(isCountStar)
            dataLib.setAttribute(op,SI_COUNT_STAR,TRUE_BYTES);
        byte[] data=encode(txn);
        dataLib.setAttribute(op,SI_TRANSACTION_ID_KEY,data);
        dataLib.setAttribute(op,SI_NEEDED,SI_NEEDED_VALUE_BYTES);
    }


    private byte[] encodeParentIds(TxnView txn,LongArrayList parentTxnIds){
        /*
         * For both active reads AND active writes, we only need to know the
         * parent's transaction ids, since we'll use the information immediately
         * available to determine other properties (additivity, etc.) Thus,
         * by doing this bit of logic, we can avoid a network call on the server
         * for every parent on the transaction chain, at the cost of 2-10 bytes
         * per parent on the chain--a cheap trade.
         */
        TxnView parent=txn.getParentTxnView();
        while(!Txn.ROOT_TRANSACTION.equals(parent)){
            parentTxnIds.add(parent.getTxnId());
            parent=parent.getParentTxnView();
        }
        int parentSize=parentTxnIds.size();
        long[] parentIds=parentTxnIds.buffer;
        MultiFieldEncoder parents=MultiFieldEncoder.create(parentSize);
        for(int i=1;i<=parentSize;i++){
            parents.encodeNext(parentIds[parentSize-i]);
        }
        return parents.build();
    }


    private void makeNonTransactional(OperationWithAttributes op){
        dataLib.setAttribute(op,SI_EXEMPT,TRUE_BYTES);
    }


}