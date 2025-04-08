package org.beast2.modelLanguage.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base interface for all statements in the Beast2 model
 */
public abstract class Statement extends ModelNode {
    public Statement(String id) {
        super(id);
    }
}
