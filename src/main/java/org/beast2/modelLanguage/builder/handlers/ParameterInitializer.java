package org.beast2.modelLanguage.builder.handlers;

import org.beast2.modelLanguage.builder.FactoryProvider;
import org.beast2.modelLanguage.builder.ModelObjectFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for parameter initialization.
 * Refactored to use ObjectFactory instead of direct BEAST dependencies.
 */
public class ParameterInitializer {

    private static final Logger logger = Logger.getLogger(ParameterInitializer.class.getName());
    private static final ModelObjectFactory factory = FactoryProvider.getFactory();

    /**
     * Initializes a parameter based on a distribution
     *
     * @param param Parameter object to initialize
     * @param dist Distribution object to use for sampling initial values
     * @return true if initialization was successful, false otherwise
     */
    public static boolean initializeParameter(Object param, Object dist) {
        if (param == null || dist == null || !factory.isParameter(param) || !factory.isParametricDistribution(dist)) {
            return false;
        }

        try {
            // Determine parameter type and delegate to appropriate method
            if (factory.isRealParameter(param)) {
                return initializeRealParameter(param, dist);
            }
            // Could add handlers for other parameter types here

            return false;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to initialize parameter: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Initializes a RealParameter based on the distribution
     */
    private static boolean initializeRealParameter(Object param, Object dist) {
        return initializeFromGenericDistribution(param, dist);
    }

    /**
     * Initializes a parameter from a generic distribution using sampled values
     */
    private static boolean initializeFromGenericDistribution(Object param, Object dist) {
        try {
            // Sample from distribution to determine dimension
            Double[][] sample = factory.sampleFromDistribution(dist, 1);

            if (sample == null || sample.length == 0 || sample[0] == null) {
                logger.warning("Distribution " + dist.getClass().getSimpleName() +
                        " returned null or empty sample");
                return false;
            }

            // Determine dimension from sample
            int dimension = sample[0].length;
            logger.info("Detected dimension " + dimension + " from distribution sample");

            // Use the actual sample values as initial values
            List<Double> sampleValues = Arrays.asList(sample[0]);

            // Validate sample values
            for (int i = 0; i < sampleValues.size(); i++) {
                Double value = sampleValues.get(i);
                if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
                    logger.warning("Sample value at dimension " + i + " is invalid: " + value +
                            ", replacing with default value 0.5");
                    sampleValues.set(i, 0.5);
                }
            }

            // Initialize parameter with sample values using factory
            factory.initializeParameterValues(param, sampleValues);

            String paramId = factory.getID(param);
            logger.info("Initialized parameter " + paramId +
                    " with " + dimension + " dimensions using sample values " + sampleValues +
                    " from distribution");

            return true;

        } catch (Exception e) {
            logger.warning("Failed to initialize from distribution: " + e.getMessage());
            return false;
        }
    }

    /**
     * Initializes a RealParameter with a default value
     *
     * @param param The parameter object to initialize
     * @return true if initialization successful, false otherwise
     */
    public static boolean initializeRealParameterWithDefault(Object param) {
        return initializeRealParameterWithDefault(param, -1);
    }

    /**
     * Initializes a RealParameter with a default value and specified dimension
     *
     * @param param The parameter object to initialize
     * @param requestedDimension The dimension to use (use -1 for singleton)
     * @return true if initialization successful, false otherwise
     */
    public static boolean initializeRealParameterWithDefault(Object param, int requestedDimension) {
        try {
            if (param == null) {
                return false;
            }

            int dimension = requestedDimension;

            // If no dimension specified, try to get it from parameter, defaulting to 1
            if (dimension < 0) {
                dimension = factory.getParameterDimension(param);
                // If dimension is 0, default to 1
                if (dimension == 0) {
                    dimension = 1;
                }
                logger.fine("Determined dimension: " + dimension);
            }

            // Create values list with appropriate defaults
            List<Double> values;
            if (dimension == 1) {
                // Single value
                values = List.of(0.5);
                logger.fine("Using single default value of 0.5");
            } else {
                // Multiple equal values that sum to 1
                values = new ArrayList<>(dimension);
                double equalValue = 1.0 / dimension;
                for (int i = 0; i < dimension; i++) {
                    values.add(equalValue);
                }
                logger.fine("Using " + dimension + " equal values of " + equalValue);
            }

            try {
                // Try to initialize parameter with values
                factory.initializeParameterValues(param, values);

                String paramId = factory.getID(param);
                logger.info("Initialized parameter " + paramId + " with default values");
                return true;
            } catch (RuntimeException re) {
                // If init fails, try to set values directly
                logger.fine("Could not initialize parameter directly, attempting to set values individually");

                try {
                    int actualDimension = factory.getParameterDimension(param);
                    if (actualDimension != dimension && actualDimension > 0) {
                        // Adjust our values list to match actual dimension
                        if (actualDimension == 1) {
                            factory.setParameterValue(param, 0, 0.5);
                        } else {
                            double equalValue = 1.0 / actualDimension;
                            for (int i = 0; i < actualDimension; i++) {
                                factory.setParameterValue(param, i, equalValue);
                            }
                        }
                    } else {
                        // Use original values
                        for (int i = 0; i < Math.min(dimension, values.size()); i++) {
                            factory.setParameterValue(param, i, values.get(i));
                        }
                    }

                    String paramId = factory.getID(param);
                    logger.info("Set values in parameter " + paramId + " using setValue method");
                    return true;
                } catch (Exception e) {
                    logger.warning("Failed to set parameter values: " + e.getMessage());
                    return false;
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to initialize parameter with default: " + e.getMessage());
            return false;
        }
    }
}