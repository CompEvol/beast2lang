package org.beast2.modelLanguage.schema.builder;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import org.beast2.modelLanguage.beast.BEASTUtils;
import org.beast2.modelLanguage.schema.core.TypeResolver;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Logger;

/**
 * Builds argument definitions from BEAST2 inputs
 */
public class ArgumentBuilder {
    private static final Logger logger = Logger.getLogger(ArgumentBuilder.class.getName());

    private final TypeResolver typeResolver;
    private final DimensionResolver dimensionResolver;
    private final ConstraintResolver constraintResolver;

    // Map of class.parameter -> recommended status for known important optional parameters
    private static final Map<String, Boolean> RECOMMENDED_PARAMETERS = new HashMap<>();

    static {
        // LogNormal distribution parameters
        RECOMMENDED_PARAMETERS.put("LogNormalDistributionModel.M", true);
        RECOMMENDED_PARAMETERS.put("LogNormalDistributionModel.S", true);
        RECOMMENDED_PARAMETERS.put("LogNormal.M", true);
        RECOMMENDED_PARAMETERS.put("LogNormal.S", true);

        // Normal distribution parameters
        RECOMMENDED_PARAMETERS.put("Normal.mean", true);
        RECOMMENDED_PARAMETERS.put("Normal.sigma", true);

        // Exponential distribution
        RECOMMENDED_PARAMETERS.put("Exponential.mean", true);

        // Beta distribution
        RECOMMENDED_PARAMETERS.put("Beta.alpha", true);
        RECOMMENDED_PARAMETERS.put("Beta.beta", true);

        // Gamma distribution
        RECOMMENDED_PARAMETERS.put("Gamma.alpha", true);
        RECOMMENDED_PARAMETERS.put("Gamma.beta", true);

        // Uniform distribution
        RECOMMENDED_PARAMETERS.put("Uniform.lower", true);
        RECOMMENDED_PARAMETERS.put("Uniform.upper", true);

        // Dirichlet distribution
        RECOMMENDED_PARAMETERS.put("Dirichlet.alpha", true);

        RECOMMENDED_PARAMETERS.put("Frequencies.frequencies", true);
    }

    public ArgumentBuilder(TypeResolver typeResolver,
                           DimensionResolver dimensionResolver,
                           ConstraintResolver constraintResolver) {
        this.typeResolver = typeResolver;
        this.dimensionResolver = dimensionResolver;
        this.constraintResolver = constraintResolver;
    }

    /**
     * Build an argument object from an Input
     */
    public JSONObject buildArgument(Input<?> input, BEASTInterface instance, Class<?> clazz) {
        JSONObject arg = new JSONObject();
        arg.put("name", input.getName());

        // Get the proper type name
        String inputType = getProperInputTypeName(input, instance);
        arg.put("type", inputType);

        // Add description and validation info
        arg.put("description", input.getTipText());
        boolean isRequired = input.getRule() == Input.Validate.REQUIRED;
        arg.put("required", isRequired);

        // Check if this optional parameter should be recommended
        if (!isRequired) {
            String className = clazz.getSimpleName();
            String paramKey = className + "." + input.getName();

            if (RECOMMENDED_PARAMETERS.getOrDefault(paramKey, false)) {
                arg.put("recommended", true);
                logger.fine("Marking " + paramKey + " as recommended");
            }
        }

        // Add default value if present
        if (input.defaultValue != null) {
            arg.put("default", input.defaultValue.toString());
        }

        // Add dimension information
        addDimensionInfo(arg, input, clazz, inputType);

        // Add constraints
        addConstraints(arg, input, clazz);

        return arg;
    }

    /**
     * Build a primary argument for distributions
     */
    public JSONObject buildPrimaryArgument(BEASTInterface instance, String primaryInputName) {
        if (primaryInputName == null) {
            return null;
        }

        Input<?> primaryInput = instance.getInput(primaryInputName);
        if (primaryInput == null) {
            return null;
        }

        return buildArgument(primaryInput, instance, instance.getClass());
    }

    /**
     * Create a synthetic primary argument for ParametricDistributions
     */
    public JSONObject createSyntheticPrimaryArgument() {
        JSONObject primaryArg = new JSONObject();
        primaryArg.put("name", "x");
        primaryArg.put("type", "Function");
        primaryArg.put("description", "Random variable (automatically wrapped in Prior)");
        return primaryArg;
    }

    /**
     * Get proper input type name using BEASTUtils
     */
    private String getProperInputTypeName(Input<?> input, BEASTInterface instance) {
        try {
            // Use BEASTUtils method to get the proper type
            Type expectedType = BEASTUtils.getInputExpectedType(input, instance, input.getName());

            if (expectedType != null) {
                return typeResolver.resolveType(expectedType);
            }
        } catch (Exception e) {
            logger.fine("Error getting input type for " + input.getName() + ": " + e.getMessage());
        }

        // Fallback to basic approach
        return getBasicInputTypeName(input);
    }

    /**
     * Fallback method for getting input type names
     */
    private String getBasicInputTypeName(Input<?> input) {
        Type type = input.getType();

        if (type == null) {
            return "Object";
        }

        return typeResolver.resolveType(type);
    }

    /**
     * Add dimension information to an argument
     */
    private void addDimensionInfo(JSONObject arg, Input<?> input, Class<?> clazz, String typeStr) {
        String className = clazz.getSimpleName();
        String argName = input.getName();

        // Check for known dimension dependencies
        Optional<JSONObject> dimension = dimensionResolver.resolveDimension(className, argName);
        if (dimension.isPresent()) {
            arg.put("dimension", dimension.get());
        } else if (dimensionResolver.isCollectionType(typeStr)) {
            // For simple arrays/lists without specific dimension requirements,
            // the type itself indicates it's a collection
        }
    }

    /**
     * Add constraints to arguments
     */
    private void addConstraints(JSONObject arg, Input<?> input, Class<?> clazz) {
        String className = clazz.getSimpleName();
        String argName = input.getName();

        Optional<String> constraint = constraintResolver.resolveConstraint(className, argName);
        constraint.ifPresent(c -> arg.put("constraint", c));
    }
}