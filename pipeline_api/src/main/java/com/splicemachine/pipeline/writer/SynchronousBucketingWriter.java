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

package com.splicemachine.pipeline.writer;

import com.splicemachine.access.api.PartitionFactory;
import com.splicemachine.concurrent.Clock;
import com.splicemachine.pipeline.api.BulkWriterFactory;
import com.splicemachine.pipeline.api.PipelineExceptionFactory;
import com.splicemachine.pipeline.api.WriteStats;
import com.splicemachine.pipeline.api.Writer;
import com.splicemachine.pipeline.client.ActionStatusReporter;
import com.splicemachine.pipeline.client.BulkWriteAction;
import com.splicemachine.pipeline.client.BulkWrites;
import com.splicemachine.pipeline.config.CountingWriteConfiguration;
import com.splicemachine.pipeline.config.WriteConfiguration;
import com.splicemachine.pipeline.writerstatus.ActionStatusMonitor;

import javax.annotation.Nonnull;
import javax.management.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Scott Fines
 *         Created on: 9/6/13
 */
public class SynchronousBucketingWriter implements Writer{
    private final ActionStatusReporter statusMonitor;
    private ActionStatusMonitor monitor;
    private final BulkWriterFactory writerFactory;
    private final PipelineExceptionFactory exceptionFactory;
    private final PartitionFactory partitionFactory;
    private final Clock clock;

    public SynchronousBucketingWriter(BulkWriterFactory writerFactory,
                                      PipelineExceptionFactory exceptionFactory,
                                      PartitionFactory partitionFactory,
                                      Clock clock){
        this.writerFactory=writerFactory;
        this.exceptionFactory=exceptionFactory;
        this.partitionFactory=partitionFactory;
        this.statusMonitor=new ActionStatusReporter();
        this.monitor=new ActionStatusMonitor(statusMonitor);
        this.clock = clock;

    }

    @Override
    public Future<WriteStats> write(byte[] tableName,
                                    BulkWrites bulkWrites,
                                    WriteConfiguration writeConfiguration) throws ExecutionException{
        WriteConfiguration countingWriteConfiguration=new CountingWriteConfiguration(writeConfiguration,statusMonitor,exceptionFactory);
        assert bulkWrites!=null:"bulk writes passed in are null";
        BulkWriteAction action=new BulkWriteAction(tableName,
                bulkWrites,
                countingWriteConfiguration,
                statusMonitor,
                writerFactory,
                exceptionFactory,
                partitionFactory,
                clock);
        statusMonitor.totalFlushesSubmitted.incrementAndGet();
        Exception e=null;
        WriteStats stats=null;
        try{
            stats=action.call();
        }catch(Exception error){
            e=error;
        }
        return new FinishedFuture(e,stats);
    }

    @Override
    public void stopWrites(){
        //no-op
    }

    @Override
    public void registerJMX(MBeanServer mbs) throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException{
        ObjectName monitorName=new ObjectName("com.splicemachine.writer.synchronous:type=WriterStatus");
        mbs.registerMBean(monitor,monitorName);
    }

    private static class FinishedFuture implements Future<WriteStats>{
        private final WriteStats stats;
        private Exception e;

        public FinishedFuture(Exception e,WriteStats stats){
            this.e=e;
            this.stats=stats;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning){
            return false;
        }

        @Override
        public boolean isCancelled(){
            return false;
        }

        @Override
        public boolean isDone(){
            return true;
        }

        @Override
        public WriteStats get() throws InterruptedException, ExecutionException{
            if(e instanceof ExecutionException) throw (ExecutionException)e;
            else if(e instanceof InterruptedException) throw (InterruptedException)e;
            else if(e!=null) throw new ExecutionException(e);
            return stats;
        }

        @Override
        public WriteStats get(long timeout,@Nonnull TimeUnit unit) throws InterruptedException,
                ExecutionException, TimeoutException{
            return get();
        }
    }
}
