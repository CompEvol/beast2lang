package org.beast2.modelLanguage.model;

import java.util.Objects;

/**
 * Represents an MRCAPrior-style calibration on a Tree parameter.
 *<p>
 * Each Calibration associates a taxon-set name with a distribution
 * (expressed as a FunctionCall AST node) to constrain the age of the
 * most recent common ancestor of that taxon set.
 *</p>
 */
public class Calibration {
    private final String taxonset;
    private final FunctionCall distribution;

    /**
     * Construct a new Calibration.
     *
     * @param taxonset    the name of the taxon set to which this calibration applies
     * @param distribution the distribution (AST) describing the calibration prior
     */
    public Calibration(String taxonset, FunctionCall distribution) {
        this.taxonset = Objects.requireNonNull(taxonset, "taxonset must not be null");
        this.distribution = Objects.requireNonNull(distribution, "distribution must not be null");
    }

    /**
     * The name of the taxon set being calibrated.
     */
    public String getTaxonset() {
        return taxonset;
    }

    /**
     * The AST node representing the calibration distribution (e.g., Uniform, Normal).
     */
    public FunctionCall getDistribution() {
        return distribution;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Calibration that)) return false;
        return taxonset.equals(that.taxonset)
                && distribution.equals(that.distribution);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taxonset, distribution);
    }

    @Override
    public String toString() {
        return String.format("@calibration(taxonset=%s, distribution=%s)",
                taxonset,
                distribution.toString());
    }
}
