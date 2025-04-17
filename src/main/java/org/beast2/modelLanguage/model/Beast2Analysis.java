package org.beast2.modelLanguage.model;

/**  
 * A Beast2Analysis ties together a model spec and its MCMC/inference setup.  
 */
public class Beast2Analysis {
    private final Beast2Model model;
    private long    chainLength;
    private int     logEvery;
    private String  traceFileName;

    public Beast2Analysis(Beast2Model model,
                          long chainLength,
                          int logEvery,
                          String traceFileName) {
        this.model         = model;
        this.chainLength   = chainLength;
        this.logEvery      = logEvery;
        this.traceFileName = traceFileName;
    }

    public Beast2Analysis(Beast2Model model) {
        this.model = model;
        chainLength = 10000000;
        logEvery = 1000;
        traceFileName = "output.log";
    }

    public Beast2Model getModel()           { return model; }
    public long        getChainLength()     { return chainLength; }

    public void setChainLength(long chainLength) {
        this.chainLength = chainLength;
    }
    public int         getLogEvery()        { return logEvery; }

    public void setLogEvery(int logEvery) {
        this.logEvery = logEvery;
    }
    public String      getTraceFileName()   { return traceFileName; }

    public void setTraceFileName(String traceFileName) {
        this.traceFileName = traceFileName;
    }
}
