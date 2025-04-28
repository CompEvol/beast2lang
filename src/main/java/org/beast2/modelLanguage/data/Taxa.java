package org.beast2.modelLanguage.data;

import java.util.ArrayList;
import java.util.List;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.evolution.alignment.Taxon;
import beast.base.evolution.alignment.TaxonSet;

@Description("A TaxonSet that can be created directly from a list of taxon names")
public class Taxa extends TaxonSet {

    final public Input<String[]> namesInput = new Input<>("names",
            "Array of taxon names");

    public Taxa() {
        // Default constructor required by BEAST
    }

    /**
     * Constructor that takes a comma-separated list of taxon names
     */
    public Taxa(String names) {
        if (names != null && !names.isEmpty()) {
            String[] nameArray = names.split(",");
            namesInput.setValue(nameArray, this);
        }
        initAndValidate();
    }

    /**
     * Constructor that takes an array of taxon names
     */
    public Taxa(String[] names) {
        namesInput.setValue(names, this);
        initAndValidate();
    }

    /**
     * Constructor with ID and comma-separated list of taxon names
     */
    public Taxa(String id, String names) {
        setID(id);
        if (names != null && !names.isEmpty()) {
            String[] nameArray = names.split(",");
            namesInput.setValue(nameArray, this);
        }
        initAndValidate();
    }

    /**
     * Constructor with ID and array of taxon names
     */
    public Taxa(String id, String[] names) {
        setID(id);
        namesInput.setValue(names, this);
        initAndValidate();
    }

    @Override
    public void initAndValidate() {
        if (namesInput.get() != null && namesInput.get().length > 0) {
            // Convert names array into Taxon objects
            List<Taxon> taxa = new ArrayList<>();

            for (String name : namesInput.get()) {
                String trimmedName = name.trim();
                if (!trimmedName.isEmpty()) {
                    taxa.add(new Taxon(trimmedName));
                }
            }

            // Set the taxon list input
            taxonsetInput.setValue(taxa, this);
        }

        // Call parent's initAndValidate to complete initialization
        super.initAndValidate();
    }
}