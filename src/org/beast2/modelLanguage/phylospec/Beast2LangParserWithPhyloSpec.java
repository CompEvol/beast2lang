package org.beast2.modelLanguage.phylospec;

import org.beast2.modelLanguage.builder.Beast2LangParser;
import org.beast2.modelLanguage.builder.Beast2LangParserImpl;
import org.beast2.modelLanguage.model.Beast2Model;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Extension of Beast2LangParserImpl that adds PhyloSpec support
 * through AST transformation.
 */
public class Beast2LangParserWithPhyloSpec implements Beast2LangParser {
    private static final Logger logger = Logger.getLogger(Beast2LangParserWithPhyloSpec.class.getName());

    private final Beast2LangParserImpl baseParser = new Beast2LangParserImpl();
    private final PhyloSpecModelTransformer transformer = new PhyloSpecModelTransformer();

    @Override
    public Beast2Model parseFromStream(InputStream inputStream) throws IOException {
        // Parse with the standard parser
        Beast2Model originalModel = baseParser.parseFromStream(inputStream);

        // Transform to handle PhyloSpec elements
        logger.info("Transforming model to handle PhyloSpec syntax");

        return transformer.transform(originalModel);
    }

    @Override
    public Beast2Model parseFromString(String input) {
        // Parse with the standard parser
        Beast2Model originalModel = baseParser.parseFromString(input);

        // Transform to handle PhyloSpec elements
        logger.info("Transforming model to handle PhyloSpec syntax");

        return transformer.transform(originalModel);
    }
}