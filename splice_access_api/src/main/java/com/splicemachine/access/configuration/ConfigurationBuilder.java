package com.splicemachine.access.configuration;

import com.splicemachine.access.api.SConfiguration;

/**
 * A builder containing all Splice subsystem properties that can be used to instantiate an {@link SConfiguration}.
 * <p/>
 * When adding a new configuration property, a public field must be added here in the configuration
 * builder so that it's set in the configuration constructor:
 * {@link SConfigurationImpl#SConfigurationImpl(ConfigurationBuilder, ConfigurationSource)}
 */
public class ConfigurationBuilder {
    // SIConfigurations
    public int activeTransactionCacheSize;
    public int completedTxnCacheSize;
    public int completedTxnConcurrency;
    public int readResolverQueueSize;
    public int readResolverThreads;
    public int timestampClientWaitTime;
    public int timestampServerBindPort;
    public int transactionKeepAliveThreads;
    public int transactionLockStripes;
    public long transactionKeepAliveInterval;
    public long transactionTimeout;

    // OperationConfiguration
    public int sequenceBlockSize;

    // DDLConfiguration
    public long ddlDrainingInitialWait;
    public long ddlDrainingMaximumWait;
    public long ddlRefreshInterval;
    public long maxDdlWait;

    // AuthenticationConfiguration
    public boolean authenticationNativeCreateCredentialsDatabase;
    public String authentication;
    public String authenticationCustomProvider;
    public String authenticationLdapSearchauthdn;
    public String authenticationLdapSearchauthpw;
    public String authenticationLdapSearchbase;
    public String authenticationLdapSearchfilter;
    public String authenticationLdapServer;
    public String authenticationNativeAlgorithm;

    // StatsConfiguration
    public double fallbackNullFraction;
    public double optimizerExtraQualifierMultiplier;
    public int cardinalityPrecision;
    public int fallbackRowWidth;
    public int indexFetchSampleSize;
    public int topkSize;
    public long fallbackLocalLatency;
    public long fallbackMinimumRowCount;
    public long fallbackOpencloseLatency;
    public long fallbackRegionRowCount;
    public long fallbackRemoteLatencyRatio;
    public long partitionCacheExpiration;

    // StorageConfiguration
    public int splitBlockSize;
    public long regionMaxFileSize;
    public long tableSplitSleepInterval;

    // HConfiguration
    public int regionServerHandlerCount;
    public int timestampBlockSize;
    public long regionLoadUpdateInterval;
    public String backupPath;
    public String compressionAlgorithm;
    public String namespace;
    public String spliceRootPath;

    // SQLConfiguration
    public boolean debugDumpBindTree;
    public boolean debugDumpClassFile;
    public boolean debugDumpOptimizedTree;
    public boolean debugLogStatementContext;
    public boolean ignoreSavePoints;
    public boolean upgradeForced;
    public int batchOnceBatchSize;
    public int importMaxQuotedColumnLines;
    public int indexBatchSize;
    public int indexLookupBlocks;
    public int kryoPoolSize;
    public int networkBindPort;
    public int olapClientWaitTime;
    public int olapServerBindPort;
    public int olapServerThreads;
    public int partitionserverJmxPort;
    public int partitionserverPort;
    public long broadcastRegionMbThreshold;
    public long broadcastRegionRowThreshold;
    public long optimizerPlanMaximumTimeout;
    public long optimizerPlanMinimumTimeout;
    public String networkBindAddress;
    public String upgradeForcedFrom;

    // PipelineConfiguration
    public int coreWriterThreads;
    public int ipcThreads;
    public int maxBufferEntries;
    public int maxDependentWrites;
    public int maxIndependentWrites;
    public int maxRetries;
    public int maxWriterThreads;
    public int pipelineKryoPoolSize;
    public int writeMaxFlushesPerRegion;
    public long clientPause;
    public long maxBufferHeapSize;
    public long startupLockWaitPeriod;
    public long threadKeepaliveTime;
    public String sparkIoCompressionCodec;

    /**
     * Build the {@link SConfiguration} given the list of subsystem defaults and the configuration source.<br/>
     * When this method returns, this builder can be discarded.
     * @param defaultsList list of subsystem defaults
     * @param configurationSource the source of the configuration properties which may contain property values
     *                            that will override defaults.
     * @return the set of configuration property values that will persist for the life of this VM.
     */
    public SConfiguration build(ConfigurationDefaultsList defaultsList, ConfigurationSource configurationSource) {
        // Lay down the defaults, use the configuration source to overlay the defaults, if they exist,
        // and construct the SConfiguration config.
        for (ConfigurationDefault configurationDefault : defaultsList) {
            configurationDefault.setDefaults(this, configurationSource);
        }
        return new SConfigurationImpl(this, configurationSource);
    }
}