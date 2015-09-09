package com.splicemachine.derby.stream.spark;

import com.google.common.collect.Iterables;
import com.splicemachine.constants.SpliceConstants;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.types.SQLInteger;
import com.splicemachine.db.impl.sql.execute.ValueRow;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.impl.spark.SpliceSpark;
import com.splicemachine.derby.impl.sql.execute.operations.LocatedRow;
import com.splicemachine.derby.impl.sql.execute.operations.export.ExportExecRowWriter;
import com.splicemachine.derby.impl.sql.execute.operations.export.ExportOperation;
import com.splicemachine.derby.impl.sql.execute.operations.export.ExportParams;
import com.splicemachine.derby.stream.function.*;
import com.splicemachine.derby.stream.iapi.DataSet;
import com.splicemachine.derby.stream.iapi.PairDataSet;
import com.splicemachine.utils.ByteDataInput;
import com.splicemachine.utils.ByteDataOutput;
import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 *
 * DataSet Implementation for Spark.
 *
 * @see com.splicemachine.derby.stream.iapi.DataSet
 * @see java.io.Serializable
 *
 */
public class SparkDataSet<V> implements DataSet<V>, Serializable {
    public JavaRDD<V> rdd;
    public int offset = -1;
    public int fetch = -1;
    public SparkDataSet(JavaRDD<V> rdd) {
        this.rdd = rdd;
    }

    public SparkDataSet(JavaRDD<V> rdd, int offset, int fetch) {
        this.offset = offset;
        this.fetch = fetch;
        this.rdd = rdd;
    }

    @Override
    public List<V> collect() {
        return rdd.collect();
    }

    @Override
    public <Op extends SpliceOperation, U> DataSet<U> mapPartitions(SpliceFlatMapFunction<Op,Iterator<V>, U> f) {
        return new SparkDataSet<U>(rdd.mapPartitions(f));
    }

    @Override
    public DataSet<V> distinct() {
        return new SparkDataSet(rdd.distinct());
    }

    @Override
    public <Op extends SpliceOperation> V fold(V zeroValue, SpliceFunction2<Op,V, V, V> function2) {
        return rdd.fold(zeroValue,function2);
    }

    @Override
    public <Op extends SpliceOperation, K,U> PairDataSet<K,U> index(SplicePairFunction<Op,V,K,U> function) {
        return new SparkPairDataSet(
                rdd.mapToPair(function));
    }

    @Override
    public <Op extends SpliceOperation, U> DataSet<U> map(SpliceFunction<Op,V,U> function) {
        return new SparkDataSet<>(rdd.map(function));
    }


    @Override
    public Iterator<V> toLocalIterator() {
        if (offset ==-1)
            return rdd.collect().iterator();
        return Iterables.limit(Iterables.skip(new Iterable() {
            @Override
            public Iterator iterator() {
                return rdd.collect().iterator();
            }
        },offset), fetch).iterator();
    }

    @Override
    public <Op extends SpliceOperation, K> PairDataSet< K, V> keyBy(SpliceFunction<Op, V, K> f) {
        return new SparkPairDataSet(rdd.keyBy(f));
    }

    @Override
    public long count() {
        return rdd.count();
    }

    @Override
    public DataSet< V> union(DataSet< V> dataSet) {
        return new SparkDataSet<>(rdd.union(((SparkDataSet) dataSet).rdd));
    }

    @Override
    public <Op extends SpliceOperation> DataSet< V> filter(SplicePredicateFunction<Op, V> f) {
        return new SparkDataSet<>(rdd.filter(f));
    }

    @Override
    public DataSet< V> intersect(DataSet< V> dataSet) {
        return new SparkDataSet<>(rdd.intersection(((SparkDataSet) dataSet).rdd));
    }

    @Override
    public DataSet< V> subtract(DataSet< V> dataSet) {
        return new SparkDataSet<>(rdd.subtract( ((SparkDataSet) dataSet).rdd));
    }

    @Override
    public boolean isEmpty() {
        return rdd.take(1).isEmpty();
    }

    @Override
    public <Op extends SpliceOperation, U> DataSet< U> flatMap(SpliceFlatMapFunction<Op, V, U> f) {
        return new SparkDataSet<>(rdd.flatMap(f));
    }

    @Override
    public void close() {

    }

    @Override
    public DataSet<V> fetchWithOffset(int offset, int fetch) {
        this.offset = offset;
        this.fetch = fetch;
        return this;
    }

    @Override
    public DataSet<V> take(int take) {
        JavaSparkContext ctx = SpliceSpark.getContext();
        return new SparkDataSet<V>(ctx.parallelize(rdd.take(take)));
    }

    @Override
    public <Op extends SpliceOperation> DataSet<LocatedRow> writeToDisk(String directory, SpliceFunction2<Op, OutputStream, Iterator<V>, Integer> exportFunction) {
        Configuration conf = new Configuration();
        ByteDataOutput bdo = new ByteDataOutput();
        Job job;
        String encoded;

        try {
            bdo.writeObject(exportFunction);
            encoded = Base64.encodeBase64String(bdo.toByteArray());
            conf.set("exportFunction", encoded);

            job = Job.getInstance(conf);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        job.setOutputKeyClass(Void.class);
        job.setOutputValueClass(LocatedRow.class);
        job.setOutputFormatClass(EOutputFormat.class);
        job.getConfiguration().set("mapred.output.dir", directory);

        JavaRDD<V> cached = rdd.cache();
        int writtenRows = (int) cached.count();
        rdd.keyBy(new Function<V, Object>() {
            @Override
            public Object call(V v) throws Exception {
                return null;
            }
        }).saveAsNewAPIHadoopDataset(job.getConfiguration());
        cached.unpersist();

        JavaSparkContext ctx = SpliceSpark.getContext();
        ValueRow valueRow = new ValueRow(2);
        valueRow.setColumn(1,new SQLInteger(writtenRows));
        valueRow.setColumn(2, new SQLInteger(0));
        return new SparkDataSet<>(ctx.parallelize(Arrays.asList(new LocatedRow(valueRow)), 1));
    }

    public static class EOutputFormat extends FileOutputFormat<Void, LocatedRow> {

        @Override
        public RecordWriter<Void, LocatedRow> getRecordWriter(TaskAttemptContext taskAttemptContext) throws IOException, InterruptedException {
            Configuration conf = taskAttemptContext.getConfiguration();
            String encoded = conf.get("exportFunction");
            ByteDataInput bdi = new ByteDataInput(
            Base64.decodeBase64(encoded));
            SpliceFunction2<ExportOperation, OutputStream, Iterator<LocatedRow>, Void> exportFunction;
            try {
                exportFunction = (SpliceFunction2<ExportOperation, OutputStream, Iterator<LocatedRow>, Void>) bdi.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException(e);
            }

            final ExportOperation op = exportFunction.getOperation();
            CompressionCodec codec = null;
            String extension = ".csv";
            boolean isCompressed = op.getExportParams().isCompression();
            if (isCompressed) {
                Class<? extends CompressionCodec> codecClass =
                        getOutputCompressorClass(taskAttemptContext, GzipCodec.class);
                codec = (CompressionCodec) ReflectionUtils.newInstance(codecClass, conf);
                extension += ".gz";
            }

            Path file = getDefaultWorkFile(taskAttemptContext, extension);
            FileSystem fs = file.getFileSystem(conf);
            OutputStream fileOut = fs.create(file, false);
            if (isCompressed) {
                fileOut = new GZIPOutputStream(fileOut);
            }
            final ExportExecRowWriter rowWriter = ExportOperation.initializeRowWriter(fileOut, op.getExportParams());
            return new RecordWriter<Void, LocatedRow>() {
                @Override
                public void write(Void _, LocatedRow locatedRow) throws IOException, InterruptedException {
                    try {
                        rowWriter.writeRow(locatedRow.getRow(), op.getSourceResultColumnDescriptors());
                    } catch (StandardException e) {
                        throw new IOException(e);
                    }
                }

                @Override
                public void close(TaskAttemptContext taskAttemptContext) throws IOException, InterruptedException {
                    rowWriter.close();
                }
            };
        }
    }
}