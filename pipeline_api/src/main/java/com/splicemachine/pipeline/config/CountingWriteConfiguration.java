/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.pipeline.config;

import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.splicemachine.access.api.CallTimeoutException;
import com.splicemachine.access.api.NotServingPartitionException;
import com.splicemachine.access.api.WrongPartitionException;
import com.splicemachine.pipeline.api.Code;
import com.splicemachine.pipeline.api.PipelineExceptionFactory;
import com.splicemachine.pipeline.api.WriteResponse;
import com.splicemachine.pipeline.client.ActionStatusReporter;
import com.splicemachine.pipeline.client.BulkWrite;
import com.splicemachine.pipeline.client.BulkWriteResult;
import com.splicemachine.pipeline.client.WriteResult;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.concurrent.ExecutionException;

/**
 * @author Scott Fines
 *         Created on: 9/6/13
 */
public class CountingWriteConfiguration extends ForwardingWriteConfiguration{
    private final ActionStatusReporter statusReporter;
    private final PipelineExceptionFactory exceptionFactory;

    public CountingWriteConfiguration(WriteConfiguration writeConfiguration,
                                      ActionStatusReporter statusMonitor,
                                      PipelineExceptionFactory exceptionFactory){
        super(writeConfiguration);
        this.statusReporter=statusMonitor;
        this.exceptionFactory=exceptionFactory;
    }

    @Override
    public WriteResponse globalError(Throwable t) throws ExecutionException{
        statusReporter.globalFailures.incrementAndGet();
        @SuppressWarnings("ThrowableResultOfMethodCallIgnored") Throwable ioe = exceptionFactory.processPipelineException(t);
        if(ioe instanceof CallTimeoutException)
            statusReporter.timedOutFlushes.incrementAndGet();
        else if(ioe instanceof NotServingPartitionException)
            statusReporter.notServingRegionFlushes.incrementAndGet();
        else if(ioe instanceof WrongPartitionException)
            statusReporter.wrongRegionFlushes.incrementAndGet();
        return super.globalError(t);
    }

    @Override
    public WriteResponse processGlobalResult(BulkWriteResult bulkWriteResult) throws Throwable{
        WriteResult result=bulkWriteResult.getGlobalResult();
        Code code=result.getCode();
        switch(code){
            case UNIQUE_VIOLATION:
            case CHECK_VIOLATION:
            case FAILED:
            case FOREIGN_KEY_VIOLATION:
            case PRIMARY_KEY_VIOLATION:
                statusReporter.failedBufferFlushes.incrementAndGet();
                break;
            case NOT_SERVING_REGION:
                statusReporter.notServingRegionFlushes.incrementAndGet();
                break;
            case PARTIAL:
                statusReporter.partialFailures.incrementAndGet();
                break;
            case PIPELINE_TOO_BUSY:
            case REGION_TOO_BUSY:
                statusReporter.rejectedCount.incrementAndGet();
                break;
            case WRITE_CONFLICT:
                statusReporter.writeConflictBufferFlushes.incrementAndGet();
                break;
            case WRONG_REGION:
                statusReporter.wrongRegionFlushes.incrementAndGet();
                break;
            default:
                break;

        }
        return super.processGlobalResult(bulkWriteResult);
    }

    @Override
    @SuppressFBWarnings(value = "SF_SWITCH_NO_DEFAULT",justification = "Intentional")
    public WriteResponse partialFailure(BulkWriteResult result,BulkWrite request) throws ExecutionException{
        statusReporter.partialFailures.incrementAndGet();
        //look for timeouts, not serving regions, wrong regions, and so forth
        boolean notServingRegion=false;
        boolean wrongRegion=false;
        boolean failed=false;
        boolean writeConflict=false;
        for(IntObjectCursor<WriteResult> cursor : result.getFailedRows()){
            Code code=cursor.value.getCode();
            switch(code){
                case FAILED:
                    failed=true;
                    break;
                case WRITE_CONFLICT:
                    writeConflict=true;
                    break;
                case NOT_SERVING_REGION:
                    notServingRegion=true;
                    break;
                case WRONG_REGION:
                    wrongRegion=true;
                    break;
            }
        }
        if(notServingRegion)
            statusReporter.notServingRegionFlushes.incrementAndGet();
        if(wrongRegion)
            statusReporter.wrongRegionFlushes.incrementAndGet();
        if(writeConflict)
            statusReporter.writeConflictBufferFlushes.incrementAndGet();
        if(failed)
            statusReporter.failedBufferFlushes.incrementAndGet();
        return super.partialFailure(result,request);
    }

    @Override
    public String toString(){
        return String.format("CountingWriteConfiguration{delegate=%s, statusReporter=%s}",this.delegate,statusReporter);
    }

}
