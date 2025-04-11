package org.beast2.modelLanguage.model;

/**  
 * A Beast2Analysis ties together a model spec and its MCMC/inference setup.  
 */
public class Beast2Analysis {
    private final Beast2Model model;
    private final long    chainLength;
    private final int     logEvery;
    private final String  traceFileName;

    public Beast2Analysis(Beast2Model model,
                          long chainLength,
                          int logEvery,
                          String traceFileName) {
        this.model         = model;
        this.chainLength   = chainLength;
        this.logEvery      = logEvery;
        this.traceFileName = traceFileName;
    }

    public Beast2Model getModel()           { return model; }
    public long        getChainLength()     { return chainLength; }
    public int         getLogEvery()        { return logEvery; }
    public String      getTraceFileName()   { return traceFileName; }
}
