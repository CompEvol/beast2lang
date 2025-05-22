package org.beast2.modelLanguage.builder;

import org.beast2.modelLanguage.model.FunctionCall;
import java.util.Map;

/**
 * Interface for managing object inputs and outputs.
 */
public interface InputManager {
    // Input value management
    void setInputValue(Object obj, String inputName, Object value) throws Exception;
    Object getInputValue(Object obj, String inputName) throws Exception;
    Map<String, Object> getInputs(Object obj);

    // Input structure
    Map<String, Object> buildInputMap(Object obj) throws Exception;
    String getPrimaryInputName(Object distributionObject);

    // Complex input operations
    void configureFromFunctionCall(Object obj, FunctionCall funcCall,
                                   Map<String, Object> objectRegistry) throws Exception;
    boolean connectToFirstMatchingInput(Object source, Object target,
                                        String[] inputNames) throws Exception;
}