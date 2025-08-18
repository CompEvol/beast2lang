package org.beast2.modelLanguage.data;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.parser.NexusParser;

import java.io.File;
import java.io.IOException;

@Description("Alignment based on a nexus file")
public class NexusAlignment extends Alignment {

    final public Input<String> filePathInput = new Input<>(
            "file",
            "Path to the nexus file",
            Input.Validate.REQUIRED);

    public NexusAlignment() {
        // This is called when constructed from XML
        sequenceInput.setRule(Input.Validate.OPTIONAL);
    }

    /**
     * Constructor for programmatic creation
     *
     * @param filePath path to the nexus file
     * @throws IOException if file cannot be read or parsed
     */
    public NexusAlignment(String filePath) throws IOException {
        filePathInput.setValue(filePath, this);
        initAndValidate();
    }

    @Override
    public void initAndValidate() {
        // Parse the nexus file
        if (filePathInput.get() != null) {
            try {
                loadNexusFile(filePathInput.get());
            } catch (IOException e) {
                throw new RuntimeException("Error loading nexus file: " + e.getMessage(), e);
            }
        } else {
            // Fall back to parent initialization if no file specified
            // This should not happen with the REQUIRED validation rule
            super.initAndValidate();
        }
    }

    /**
     * Load alignment data from a nexus file
     *
     * @param filePath path to the nexus file
     * @throws IOException if file cannot be read or parsed
     */
    private void loadNexusFile(String filePath) throws IOException {
        Log.info.println("Loading alignment from nexus file: " + filePath);

        // Parse the nexus file
        NexusParser parser = new NexusParser();
        File nexusFile = new File(filePath);
        parser.parseFile(nexusFile);

        // Check if alignment was found in the file
        if (parser.m_alignment == null) {
            throw new IOException("No alignment found in nexus file: " + filePath);
        }

        // Copy data from parsed alignment to this instance
        copyFrom(parser.m_alignment);

        Log.info.println(toString(false));
    }

    /**
     * Copy all relevant data from another alignment
     *
     * @param sourceAlignment the alignment to copy from
     */
    private void copyFrom(Alignment sourceAlignment) {
        // Copy sequences
        sequenceInput.get().clear();
        for (Sequence sequence : sourceAlignment.sequenceInput.get()) {
            sequenceInput.setValue(sequence, this);
        }

        // Copy data type
        dataTypeInput.setValue(sourceAlignment.dataTypeInput.get(), this);

        // Copy other relevant settings
        if (sourceAlignment.userDataTypeInput.get() != null) {
            userDataTypeInput.setValue(sourceAlignment.userDataTypeInput.get(), this);
        }

        if (sourceAlignment.siteWeightsInput.get() != null) {
            siteWeightsInput.setValue(sourceAlignment.siteWeightsInput.get(), this);
        }

        // Initialize the alignment
        sequences = sourceAlignment.sequenceInput.get();
        m_dataType = sourceAlignment.getDataType();

        // Set up taxa, stateCounts, etc.
        taxaNames.clear();
        stateCounts.clear();
        counts.clear();
        tipLikelihoods.clear();

        for (Sequence seq : sequences) {
            counts.add(seq.getSequence(m_dataType));
            taxaNames.add(seq.getTaxon());
            tipLikelihoods.add(seq.getLikelihoods());
            usingTipLikelihoods |= (seq.getLikelihoods() != null);

            if (seq.totalCountInput.get() != null) {
                stateCounts.add(seq.totalCountInput.get());
            } else {
                stateCounts.add(m_dataType.getStateCount());
            }
        }

        // Calculate patterns and handle ascertainment, etc.
        if (counts.size() > 0) {
            sanityCheckCalcPatternsSetUpAscertainment(true);
        }
    }
}