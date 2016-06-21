package com.splicemachine.stats.cardinality;

import com.splicemachine.hash.Hash64;
import com.splicemachine.stats.DoubleFunction;

import java.util.Arrays;

/**
 * Cardinality Estimator with automatic bias-adjustment for low-cardinality estimations.
 *
 * <p>This is a simple, non-thread-safe implementation which uses a dense array for storage.
 * Thus the memory requirement is 1 byte per register.</p>
 *
 * @author Scott Fines
 * Date: 1/1/14
 */
public class AdjustedHyperLogLogCounter extends BaseBiasAdjustedHyperLogLogCounter {
		final byte[] buckets;


		public AdjustedHyperLogLogCounter(int size, Hash64 hashFunction) {
				super(size, hashFunction);
				this.buckets = new byte[numRegisters];
		}

    private AdjustedHyperLogLogCounter(int precision, Hash64 hashFunction, byte[] bytes) {
        super(precision, hashFunction);
        this.buckets = bytes;
    }

    @Override
    public BaseLogLogCounter getClone() {
        return new AdjustedHyperLogLogCounter(precision,hashFunction, Arrays.copyOf(buckets,buckets.length));
    }

    public AdjustedHyperLogLogCounter(int precision, Hash64 hashFunction, DoubleFunction biasAdjuster) {
				super(precision, hashFunction, biasAdjuster);
				this.buckets = new byte[numRegisters];
		}

		@Override
		protected void updateRegister(int register, int value) {
				byte b = buckets[register];
				if(b>=value) return;
				buckets[register] = (byte)(value & 0xff);
		}

		@Override
		protected int getRegister(int register) {
				return buckets[register];
		}

}