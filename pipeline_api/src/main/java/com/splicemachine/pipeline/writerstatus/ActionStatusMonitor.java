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

package com.splicemachine.pipeline.writerstatus;

import com.splicemachine.pipeline.api.WriterStatus;
import com.splicemachine.pipeline.client.ActionStatusReporter;

/**
 * @author Scott Fines
 *         Created on: 9/6/13
 */
public class ActionStatusMonitor implements WriterStatus {
    private final ActionStatusReporter statusMonitor;

    public ActionStatusMonitor(ActionStatusReporter statusMonitor) {
        this.statusMonitor = statusMonitor;
    }

    @Override public int getExecutingBufferFlushes() { return statusMonitor.numExecutingFlushes.get(); }
    @Override public long getTotalSubmittedFlushes() { return statusMonitor.totalFlushesSubmitted.get(); }
    @Override public long getFailedBufferFlushes() { return statusMonitor.failedBufferFlushes.get(); }
    @Override public long getNotServingRegionFlushes() { return statusMonitor.notServingRegionFlushes.get(); }
    @Override public long getTimedOutFlushes() { return statusMonitor.timedOutFlushes.get(); }
    @Override public long getGlobalErrors() { return statusMonitor.globalFailures.get(); }
    @Override public long getPartialFailures() { return statusMonitor.partialFailures.get(); }
    @Override public long getMaxFlushTime() { return statusMonitor.maxFlushTime.get(); }
    @Override public long getMinFlushTime() { return statusMonitor.minFlushTime.get(); }
    @Override public long getWrongRegionFlushes() { return statusMonitor.wrongRegionFlushes.get(); }
    @Override public long getMaxFlushedBufferSize() { return statusMonitor.maxFlushSizeBytes.get(); }
    @Override public long getTotalFlushedBufferSize() { return statusMonitor.totalFlushSizeBytes.get(); }
    @Override public long getMinFlushedBufferSize() { return statusMonitor.minFlushSizeBytes.get(); }
    @Override public long getMinFlushedBufferEntries() { return statusMonitor.minFlushEntries.get(); }
    @Override public long getMaxFlushedBufferEntries() { return statusMonitor.maxFlushEntries.get(); }
    @Override public long getTotalFlushedBufferEntries() { return statusMonitor.totalFlushEntries.get(); }
    @Override public long getTotalFlushTime() { return statusMonitor.totalFlushTime.get(); }
    @Override public long getMaxRegionsPerFlush() { return statusMonitor.maxFlushRegions.get(); }
    @Override public long getMinRegionsPerFlush() { return statusMonitor.minFlushRegions.get(); }

    @Override
    public long getAvgRegionsPerFlush() {
        return (long)(statusMonitor.totalFlushRegions.get()/(double)statusMonitor.totalFlushesSubmitted.get());
    }

    @Override
    public double getAvgFlushTime() {
        long totalFlushTime = getTotalFlushTime();
        long totalFlushes = getTotalSubmittedFlushes();
        return (double)totalFlushTime/totalFlushes;
    }

    @Override
    public void reset() {
        statusMonitor.reset();
    }

		@Override public long getTotalRejectedFlushes() { return statusMonitor.rejectedCount.get(); }

		@Override public double getAvgFlushedBufferSize() {
        return statusMonitor.totalFlushSizeBytes.get()/(double)statusMonitor.totalFlushesSubmitted.get();
    }

    @Override
    public double getAvgFlushedBufferEntries() {
        return statusMonitor.totalFlushEntries.get()/(double)statusMonitor.totalFlushesSubmitted.get();
    }

    @Override
    public double getOverallWriteThroughput() {
        /*
         * Throughput in rows/ms
         */
        long flushTimeMs = statusMonitor.totalFlushTime.get();
        double rowsPerMs = statusMonitor.totalFlushEntries.get()/(double)flushTimeMs;

        return rowsPerMs*1000; //throughput in rows/s
    }

    @Override
    public double getAvgFlushedEntriesPerRegion() {
        return getAvgFlushedBufferEntries()/getAvgRegionsPerFlush();
    }

    @Override
    public double getAvgFlushedSizePerRegion() {
        return getAvgFlushedBufferSize()/getAvgRegionsPerFlush();
    }
}
