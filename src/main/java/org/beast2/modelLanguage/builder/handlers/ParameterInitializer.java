package org.beast2.modelLanguage.builder.handlers;

import beast.base.inference.parameter.Parameter;
import beast.base.inference.parameter.RealParameter;
import beast.base.inference.distribution.ParametricDistribution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for parameter initialization, extracted from DistributionAssignmentHandler
 * to improve code organization and reusability.
 */
public class ParameterInitializer {

    /**
     * Initializes a parameter based on a distribution
     *
     * @param param Parameter to initialize
     * @param dist Distribution to use for sampling initial values
     * @return true if initialization was successful, false otherwise
     */
    public static boolean initializeParameter(Parameter<?> param, ParametricDistribution dist) {
        if (param == null || dist == null) {
            return false;
        }

        try {
            if (param instanceof RealParameter) {
                return initializeRealParameter((RealParameter) param, dist);
            }
            // Could add handlers for other parameter types here

            return false;
        } catch (Exception e) {
            Logger.getLogger(ParameterInitializer.class.getName())
                    .log(Level.WARNING, "Failed to initialize parameter: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Initializes a RealParameter based on the distribution type
     * Uses only initByName() and never calls getDimension() or setValue()
     */
    private static boolean initializeRealParameter(RealParameter param, ParametricDistribution dist) {
        // Handle specific distribution types

        return initializeFromGenericDistribution(param, dist);
    }

    /**
     * Initializes a parameter from a generic distribution using median value
     */
    private static boolean initializeFromGenericDistribution(RealParameter param, ParametricDistribution dist) {
        try {
            // Sample from distribution to determine dimension
            Double[][] sample;
            try {
                sample = dist.sample(1);
                if (sample == null || sample.length == 0 || sample[0] == null) {
                    Logger.getLogger(ParameterInitializer.class.getName())
                            .warning("Distribution " + dist.getClass().getSimpleName() +
                                    " returned null or empty sample");
                    return false;
                }
            } catch (Exception e) {
                Logger.getLogger(ParameterInitializer.class.getName())
                        .warning("Failed to sample from distribution: " + e.getMessage());
                return false;
            }

            // Determine dimension from sample
            int dimension = sample[0].length;
            Logger.getLogger(ParameterInitializer.class.getName())
                    .info("Detected dimension " + dimension + " from distribution sample");

            // Use the actual sample values as initial values
            List<Double> sampleValues = Arrays.asList(sample[0]);

            // Validate sample values
            for (int i = 0; i < sampleValues.size(); i++) {
                Double value = sampleValues.get(i);
                if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
                    Logger.getLogger(ParameterInitializer.class.getName())
                            .warning("Sample value at dimension " + i + " is invalid: " + value +
                                    ", replacing with default value 0.5");
                    sampleValues.set(i, 0.5);
                }
            }

            // Initialize parameter with sample values
            param.initByName("value", sampleValues);

            Logger.getLogger(ParameterInitializer.class.getName())
                    .info("Initialized parameter " + param.getID() +
                            " with " + dimension + " dimensions using sample values " + sampleValues +
                            " from distribution");

            return true;

        } catch (Exception e) {
            Logger.getLogger(ParameterInitializer.class.getName())
                    .warning("Failed to initialize from distribution: " + e.getMessage());
            return false;
        }
    }

    /**
     * Initializes a RealParameter with a default value
     *
     * @param param The parameter to initialize
     * @return true if initialization successful, false otherwise
     */
    public static boolean initializeRealParameterWithDefault(RealParameter param) {
        return initializeRealParameterWithDefault(param, -1);
    }

    /**
     * Initializes a RealParameter with a default value and specified dimension
     *
     * @param param The parameter to initialize
     * @param requestedDimension The dimension to use (use -1 for singleton)
     * @return true if initialization successful, false otherwise
     */
    public static boolean initializeRealParameterWithDefault(RealParameter param, int requestedDimension) {
        try {
            if (param == null) {
                return false;
            }

            int dimension = requestedDimension;

            // If no dimension specified, try to get it from parameter, defaulting to 1
            if (dimension < 0) {
                try {
                    dimension = param.getDimension();
                    // If dimension is 0, default to 1
                    if (dimension == 0) {
                        dimension = 1;
                    }
                } catch (Exception e) {
                    // If getDimension throws, default to 1
                    dimension = 1;
                    Logger.getLogger(ParameterInitializer.class.getName())
                            .fine("Could not determine dimension, using default of 1");
                }
            }

            // Create values list with appropriate defaults
            List<Double> values;
            if (dimension == 1) {
                // Single value
                values = List.of(0.5);
                Logger.getLogger(ParameterInitializer.class.getName())
                        .fine("Using single default value of 0.5");
            } else {
                // Multiple equal values that sum to 1
                values = new ArrayList<>(dimension);
                double equalValue = 1.0 / dimension;
                for (int i = 0; i < dimension; i++) {
                    values.add(equalValue);
                }
                Logger.getLogger(ParameterInitializer.class.getName())
                        .fine("Using " + dimension + " equal values of " + equalValue);
            }

            try {
                // Try to initialize parameter with values
                param.initByName("value", values);
                Logger.getLogger(ParameterInitializer.class.getName())
                        .info("Initialized parameter " + param.getID() + " with default values");
                return true;
            } catch (RuntimeException re) {
                // If init fails, try to set values directly
                Logger.getLogger(ParameterInitializer.class.getName())
                        .fine("Could not initialize parameter directly, attempting to set values individually");

                try {
                    int actualDimension = param.getDimension();
                    if (actualDimension != dimension && actualDimension > 0) {
                        // Adjust our values list to match actual dimension
                        if (actualDimension == 1) {
                            param.setValue(0, 0.5);
                        } else {
                            double equalValue = 1.0 / actualDimension;
                            for (int i = 0; i < actualDimension; i++) {
                                param.setValue(i, equalValue);
                            }
                        }
                    } else {
                        // Use original values
                        for (int i = 0; i < Math.min(dimension, values.size()); i++) {
                            param.setValue(i, values.get(i));
                        }
                    }

                    Logger.getLogger(ParameterInitializer.class.getName())
                            .info("Set values in parameter " + param.getID() + " using setValue method");
                    return true;
                } catch (Exception e) {
                    Logger.getLogger(ParameterInitializer.class.getName())
                            .warning("Failed to set parameter values: " + e.getMessage());
                    return false;
                }
            }
        } catch (Exception e) {
            Logger.getLogger(ParameterInitializer.class.getName())
                    .warning("Failed to initialize parameter with default: " + e.getMessage());
            return false;
        }
    }

    boolean isInitialized(Parameter parameter) {
        return parameter.getValues() != null;
    }
}