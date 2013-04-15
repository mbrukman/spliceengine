package com.splicemachine.si2.si.api;

import org.apache.log4j.Logger;

import java.util.concurrent.Callable;

public class ParentTransactionManager {
    static final Logger LOG = Logger.getLogger(ParentTransactionManager.class);
    private static ThreadLocal<String> parentTransactionIdThreadLocal = new ThreadLocal<String>();

    private static void setParentTransactionId(String transactionId) {
        parentTransactionIdThreadLocal.set(transactionId);
    }

    public static String getParentTransactionId() {
        return parentTransactionIdThreadLocal.get();
    }

    public static <T> T runInParentTransaction(String parentTransactionId, Callable<T> callable) throws Exception {
        final String oldParentTransactionId = ParentTransactionManager.getParentTransactionId();
        try {
            ParentTransactionManager.setParentTransactionId(parentTransactionId);
            return callable.call();
        } finally {
            ParentTransactionManager.setParentTransactionId(oldParentTransactionId);
        }
    }
}