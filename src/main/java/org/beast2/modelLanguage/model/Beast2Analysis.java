package org.beast2.modelLanguage.model;

/**
 * A Beast2Analysis ties together a model spec and its MCMC/inference setup.  
 */
public class Beast2Analysis {
    private final Beast2Model model;
    private long chainLength;
    private int logEvery;
    private String traceFileName;
    private String treeLogFileName;
    private Long seed;
    private int threadCount;
    private boolean resume;
    private int preBurnin;
    private String screenLogFileName;
    private boolean useBeagle;
    private boolean useMultiThreaded;

    /**
     * Full constructor with all parameters
     */
    public Beast2Analysis(Beast2Model model,
                          long chainLength,
                          int logEvery,
                          String traceFileName,
                          String treeLogFileName,
                          Long seed,
                          int threadCount,
                          boolean resume,
                          int preBurnin,
                          String screenLogFileName,
                          boolean useBeagle,
                          boolean useMultiThreaded) {
        this.model = model;
        this.chainLength = chainLength;
        this.logEvery = logEvery;
        this.traceFileName = traceFileName;
        this.treeLogFileName = treeLogFileName;
        this.seed = seed;
        this.threadCount = threadCount;
        this.resume = resume;
        this.preBurnin = preBurnin;
        this.screenLogFileName = screenLogFileName;
        this.useBeagle = useBeagle;
        this.useMultiThreaded = useMultiThreaded;
    }

    /**
     * Basic constructor with essential parameters
     */
    public Beast2Analysis(Beast2Model model,
                          long chainLength,
                          int logEvery,
                          String traceFileName) {
        this.model = model;
        this.chainLength = chainLength;
        this.logEvery = logEvery;
        this.traceFileName = traceFileName;
        this.treeLogFileName = "tree.trees";
        this.threadCount = 1;
        this.resume = false;
        this.preBurnin = 0;
        this.screenLogFileName = null;
        this.useBeagle = false;
        this.useMultiThreaded = false;
    }

    /**
     * Default constructor with reasonable defaults
     */
    public Beast2Analysis(Beast2Model model) {
        this.model = model;
        this.chainLength = 10000000;
        this.logEvery = 1000;
        this.traceFileName = "output.log";
        this.treeLogFileName = "tree.trees";
        this.threadCount = 1;
        this.resume = false;
        this.preBurnin = 0;
        this.screenLogFileName = null;
        this.useBeagle = false;
        this.useMultiThreaded = false;
    }

    // Getters and setters

    public Beast2Model getModel() {
        return model;
    }

    public long getChainLength() {
        return chainLength;
    }

    public void setChainLength(long chainLength) {
        this.chainLength = chainLength;
    }

    public int getLogEvery() {
        return logEvery;
    }

    public void setLogEvery(int logEvery) {
        this.logEvery = logEvery;
    }

    public String getTraceFileName() {
        return traceFileName;
    }

    public void setTraceFileName(String traceFileName) {
        this.traceFileName = traceFileName;
    }

    public String getTreeLogFileName() {
        return treeLogFileName;
    }

    public void setTreeLogFileName(String treeLogFileName) {
        this.treeLogFileName = treeLogFileName;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public boolean isResume() {
        return resume;
    }

    public void setResume(boolean resume) {
        this.resume = resume;
    }

    public int getPreBurnin() {
        return preBurnin;
    }

    public void setPreBurnin(int preBurnin) {
        this.preBurnin = preBurnin;
    }

    public String getScreenLogFileName() {
        return screenLogFileName;
    }

    public void setScreenLogFileName(String screenLogFileName) {
        this.screenLogFileName = screenLogFileName;
    }

    public boolean isUseBeagle() {
        return useBeagle;
    }

    public void setUseBeagle(boolean useBeagle) {
        this.useBeagle = useBeagle;
    }

    public boolean isUseMultiThreaded() {
        return useMultiThreaded;
    }

    public void setUseMultiThreaded(boolean useMultiThreaded) {
        this.useMultiThreaded = useMultiThreaded;
    }

    /**
     * Creates a string representation of this analysis with key parameters
     */
    @Override
    public String toString() {
        return "Beast2Analysis{" +
                "model=" + model +
                ", chainLength=" + chainLength +
                ", logEvery=" + logEvery +
                ", traceFileName='" + traceFileName + '\'' +
                ", treeLogFileName='" + treeLogFileName + '\'' +
                (seed != null ? ", seed=" + seed : "") +
                ", threadCount=" + threadCount +
                ", resume=" + resume +
                ", preBurnin=" + preBurnin +
                (screenLogFileName != null ? ", screenLogFileName='" + screenLogFileName + '\'' : "") +
                ", useBeagle=" + useBeagle +
                ", useMultiThreaded=" + useMultiThreaded +
                '}';
    }
}