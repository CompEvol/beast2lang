package org.beast2.modelLanguage.model;

import java.util.Objects;

/**
 * Represents an MRCAPrior-style calibration on a Tree parameter.
 * <p>
 * Each Calibration associates a taxon-set name with optional parameters
 * to constrain the age and topology of the most recent common ancestor
 * of that taxon set.
 * </p>
 */
public class Calibration {
    private final String taxonset;
    private final FunctionCall distribution; // Can be null for monophyletic-only constraints
    private final boolean monophyletic;
    private final boolean leaf;

    /**
     * Construct a new Calibration with all parameters.
     *
     * @param taxonset     the name of the taxon set to which this calibration applies
     * @param distribution the distribution (AST) describing the calibration prior (can be null)
     * @param monophyletic whether this is a monophyletic constraint
     * @param leaf         whether this is a leaf/tip calibration
     */
    public Calibration(String taxonset, FunctionCall distribution, boolean monophyletic, boolean leaf) {
        this.taxonset = Objects.requireNonNull(taxonset, "taxonset must not be null");
        this.distribution = distribution; // Can be null
        this.monophyletic = monophyletic;
        this.leaf = leaf;
    }

    /**
     * Construct a new Calibration with distribution only (backward compatibility).
     * Defaults: monophyletic=true, leaf=false
     *
     * @param taxonset    the name of the taxon set to which this calibration applies
     * @param distribution the distribution (AST) describing the calibration prior
     */
    public Calibration(String taxonset, FunctionCall distribution) {
        this(taxonset, distribution, true, false);
    }

    /**
     * Construct a monophyletic-only calibration (no age constraint).
     *
     * @param taxonset the name of the taxon set to which this calibration applies
     * @param monophyletic whether this is a monophyletic constraint
     */
    public Calibration(String taxonset, boolean monophyletic) {
        this(taxonset, null, monophyletic, false);
    }

    /**
     * The name of the taxon set being calibrated.
     */
    public String getTaxonset() {
        return taxonset;
    }

    /**
     * The AST node representing the calibration distribution (e.g., Uniform, Normal).
     * Can be null for monophyletic-only constraints.
     */
    public FunctionCall getDistribution() {
        return distribution;
    }

    /**
     * Whether this calibration enforces monophyly.
     */
    public boolean isMonophyletic() {
        return monophyletic;
    }

    /**
     * Whether this is a leaf/tip calibration.
     */
    public boolean isLeaf() {
        return leaf;
    }

    /**
     * Check if this calibration has a distribution constraint.
     */
    public boolean hasDistribution() {
        return distribution != null;
    }

    /**
     * Check if this calibration has a leaf constraint.
     */
    public boolean hasLeafConstraint() {
        return leaf;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Calibration that)) return false;
        return monophyletic == that.monophyletic &&
                leaf == that.leaf &&
                taxonset.equals(that.taxonset) &&
                Objects.equals(distribution, that.distribution);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taxonset, distribution, monophyletic, leaf);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("@calibration(");

        if (hasDistribution()) {
            sb.append("taxonset=").append(taxonset)
                    .append(", distribution=").append(distribution.toString());
        } else {
            sb.append("monophyletic=").append(monophyletic)
                    .append(", taxonset=").append(taxonset);
        }

        if (leaf) {
            sb.append(", leaf=true");
        }

        sb.append(")");
        return sb.toString();
    }
}