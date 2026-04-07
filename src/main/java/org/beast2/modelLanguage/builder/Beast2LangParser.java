package org.beast2.modelLanguage.builder;

import org.beast2.modelLanguage.model.Beast2Model;

import java.io.IOException;
import java.io.InputStream;

/**
 * Interface for parsing Beast2Lang syntax into a Beast2Model.
 */
public interface Beast2LangParser {
    
    /**
     * Parse Beast2Lang from an input stream
     * 
     * @param inputStream the input stream containing Beast2Lang code
     * @return a Beast2Model representing the parsed content
     * @throws IOException if an I/O error occurs
     */
    Beast2Model parseFromStream(InputStream inputStream) throws IOException;
    
    /**
     * Parse Beast2Lang from a string
     * 
     * @param input the string containing Beast2Lang code
     * @return a Beast2Model representing the parsed content
     */
    Beast2Model parseFromString(String input);
}