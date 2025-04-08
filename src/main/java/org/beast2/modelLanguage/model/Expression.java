package org.beast2.modelLanguage.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base interface for all expressions in the Beast2 model
 */
public abstract class Expression extends ModelNode {
    public Expression(String id) {
        super(id);
    }
}
