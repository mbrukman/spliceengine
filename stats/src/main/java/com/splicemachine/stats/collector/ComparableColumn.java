package com.splicemachine.stats.collector;

import com.splicemachine.stats.ColumnStatistics;
import com.splicemachine.stats.ComparableColumnStatistics;
import com.splicemachine.stats.Updateable;
import com.splicemachine.stats.cardinality.CardinalityEstimator;
import com.splicemachine.stats.estimate.DistributionFactory;
import com.splicemachine.stats.frequency.FrequencyCounter;
import com.splicemachine.stats.order.ComparableMinMaxCollector;

/**
 * @author Scott Fines
 *         Date: 2/24/15
 */
class ComparableColumn<T extends Comparable<T>> implements ColumnStatsCollector<T>,Updateable<T> {
    private final int columnId;
    private final CardinalityEstimator<T> cardinalityEstimator;
    private final FrequencyCounter<T> frequencyCounter;
    private final ComparableMinMaxCollector<T> minMaxCollector;

    private long totalBytes;
    private long count;
    private long nullCount;

    private int topK;
    private DistributionFactory<T> distributionFactory;

    public ComparableColumn(int columnId,
                            CardinalityEstimator<T> cardinalityEstimator,
                            FrequencyCounter<T> frequencyCounter,
                            ComparableMinMaxCollector<T> minMaxCollector,
                            int topK,
                            DistributionFactory<T> distributionFactory) {
        this.cardinalityEstimator = cardinalityEstimator;
        this.frequencyCounter = frequencyCounter;
        this.minMaxCollector = minMaxCollector;
        this.topK = topK;
        this.columnId =columnId;
        this.distributionFactory = distributionFactory;
    }

    @Override
    public ColumnStatistics<T> build() {
        return new ComparableColumnStatistics<>(columnId,cardinalityEstimator,
                frequencyCounter.frequentElements(topK),
                minMaxCollector.minimum(),
                minMaxCollector.maximum(),
                totalBytes,
                count,
                nullCount,
                minMaxCollector.minCount(),
                distributionFactory);
    }

    @Override public void updateNull() { updateNull(1l); }
    @Override
    public void updateNull(long count) {
        nullCount+=count;
        this.count+=count;
    }
    @Override public void updateSize(int size) { totalBytes+=size; }
    @Override public void update(T item) { update(item,1l); }

    @Override
    public void update(T item, long count) {
        cardinalityEstimator.update(item,count);
        frequencyCounter.update(item,count);
        minMaxCollector.update(item, count);
        this.count+=count;
    }
}