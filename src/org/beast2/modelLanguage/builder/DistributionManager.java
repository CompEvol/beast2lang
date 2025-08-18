package org.beast2.modelLanguage.builder;

import java.util.List;
import java.util.Map;

/**
 * Interface for distribution-specific operations.
 */
public interface DistributionManager {
    /**
     * Filter objects to get only distributions.
     */
    List<Object> getDistributions(Map<String, Object> objects);

    /**
     * Sample from a parametric distribution.
     */
    Double[][] sampleFromDistribution(Object distribution, int sampleSize);
}