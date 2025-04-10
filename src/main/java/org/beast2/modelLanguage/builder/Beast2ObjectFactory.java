package org.beast2.modelLanguage.builder;

import org.beast2.modelLanguage.model.Beast2Model;

import java.util.Map;

/**
 * Interface for creating BEAST2 objects from a Beast2Model.
 */
public interface Beast2ObjectFactory {
    
    /**
     * Build BEAST2 objects from a Beast2Model
     * 
     * @param model the Beast2Model to convert to BEAST2 objects
     * @return the root BEAST2 object (typically the MCMC object)
     * @throws Exception if construction fails
     */
    Object buildFromModel(Beast2Model model) throws Exception;
    
    /**
     * Get all created BEAST2 objects
     * 
     * @return a map of object name to object instance
     */
    Map<String, Object> getAllObjects();
    
    /**
     * Get a specific BEAST2 object by name
     * 
     * @param name the name of the object to retrieve
     * @return the BEAST2 object, or null if not found
     */
    Object getObject(String name);
}