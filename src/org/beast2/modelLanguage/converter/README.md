# BEAST2 to Beast2Lang Converter

The converter package within the Beast2Lang project provides decompilation functionality to transform BEAST2 XML models back into Beast2Lang (.b2l) format. This is part of the larger Beast2Lang ecosystem that enables bidirectional conversion between BEAST2 XML and the more readable Beast2Lang domain-specific language.

## Overview

The converter package (`org.beast2.modelLanguage.converter`) implements a sophisticated decompilation system that analyzes BEAST2 XML files and reconstructs equivalent Beast2Lang code. This enables users to:

- Convert existing BEAST2 XML analyses to the more readable Beast2Lang format
- Understand complex BEAST2 models through cleaner syntax
- Modify and maintain phylogenetic models more easily
- Integrate with other formats (LinguaPhylo, PhyloSpec)

## Features

- **Pipeline Architecture**: Modular conversion process with 11 specialized phases
- **Intelligent Distribution Detection**: Automatically identifies and converts distribution assignments
- **Identifier Normalization**: Converts BEAST identifiers to valid Java identifiers
- **RandomComposition Support**: Detects integer parameters requiring RandomComposition priors
- **Alignment Handling**: Smart processing of sequence alignments (inline vs external files)
- **Tree Calibration Support**: Handles MRCAPrior calibrations with annotations
- **Dependency Resolution**: Automatic topological sorting of statements

## Architecture

### Pipeline Phases

1. **IdentifierNormalizationPhase**: Converts BEAST identifiers (with dots, colons, etc.) to valid Java identifiers
2. **ObjectIdentificationPhase**: Traverses the BEAST object graph and assigns unique identifiers
3. **RandomCompositionDetectionPhase**: Identifies IntegerParameters operated on only by DeltaExchange operators
4. **DistributionAnalysisPhase**: Identifies distributions used in ~ statements and inlined distributions
5. **AlignmentProcessingPhase**: Processes alignment objects and creates nexus() or alignment() expressions
6. **RandomCompositionProcessingPhase**: Creates RandomComposition distribution statements
7. **StateNodeProcessingPhase**: Processes state nodes with their distributions
8. **RemainingObjectsPhase**: Handles all objects not processed by previous phases
9. **DependencySortingPhase**: Sorts statements based on dependencies
10. **ObservedAlignmentPhase**: Creates @observed statements for alignments in TreeLikelihood

### Key Components

- **ConversionContext**: Shared state for all pipeline phases
- **StatementCreator**: Creates Beast2Lang statements from BEAST objects
- **ExpressionCreator**: Handles expression creation with proper input filtering
- **BeastConversionUtilities**: Core utilities for BEAST-specific operations
- **Beast2ModelWriter**: Converts the model to Beast2Lang script text

## Integration with Beast2Lang

This converter is a core component of the Beast2Lang project. To use it:

### As Part of Beast2Lang

```bash
# Decompile BEAST2 XML to Beast2Lang
./target/beast2lang decompile -i model.xml -o model.b2l

# Convert to other formats
./target/beast2lang convert -i model.b2l --from beast2 --to lphy -o model.lphy
```

### Programmatic Usage

```java
import org.beast2.modelLanguage.converter.Beast2ToBeast2LangConverter;
import beast.base.inference.Distribution;
import beast.base.inference.State;

// Create converter
Beast2ToBeast2LangConverter converter = new Beast2ToBeast2LangConverter();

        // Convert BEAST2 model
        Beast2Model model = converter.convertToBeast2Model(posterior, state, mcmc);

        // Write to Beast2Lang script
        Beast2ModelWriter writer = new Beast2ModelWriter();
        String script = writer.writeModel(model);
```

## Example Conversions

### Distribution Assignment

BEAST2 XML:
```xml
<parameter id="kappa" spec="parameter.RealParameter" lower="0.0" name="stateNode">1.0</parameter>
...
<prior id="KappaPrior" name="distribution" x="@kappa">
    <LogNormal id="LogNormal.0" name="distr">
        <parameter id="RealParameter.1" spec="parameter.RealParameter" estimate="false" name="M">1.0</parameter>
        <parameter id="RealParameter.2" spec="parameter.RealParameter" estimate="false" name="S">1.25</parameter>
    </LogNormal>
</prior>
```

Beast2Lang:
```java
RealParameter kappa ~ LogNormalDistributionModel(M=1.0, S=1.25);
```

### Tree with Calibrations

BEAST2 XML:
```xml
<tree id="Tree.t:alignment" spec="beast.base.evolution.tree.Tree" name="stateNode">
    <taxonset id="TaxonSet.alignment" spec="TaxonSet">
        <alignment idref="alignment"/>
    </taxonset>
</tree>
...
<distribution id="CoalescentConstant.t:alignment" spec="Coalescent">
    <populationModel id="ConstantPopulation.t:alignment" spec="ConstantPopulation" popSize="@popSize.t:alignment"/>
    <treeIntervals id="TreeIntervals.t:alignment" spec="beast.base.evolution.tree.TreeIntervals" tree="@Tree.t:alignment"/>
</distribution>
```

Beast2Lang:
```java
Tree tree ~ Coalescent(populationModel=ConstantPopulation(popSize=popSize));
```

### RandomComposition for Constrained Integer Parameters

BEAST2 XML:
```xml
<stateNode id="bGroupSizes.t:hcv" spec="parameter.IntegerParameter" dimension="4">1</stateNode>
...
<operator id="groupSizesDelta.t:hcv" spec="operator.kernel.BactrianDeltaExchangeOperator" integer="true" weight="6.0">
    <intparameter idref="bGroupSizes.t:hcv"/>
</operator>
```

Beast2Lang:
```java
IntegerParameter bGroupSizes_t_hcv ~ RandomComposition(n=62, k=4);
```

## Special Features

### Alignment Processing

The converter intelligently decides whether to inline small alignments or create external nexus files:

```java
// Small alignment - inlined
@data
Alignment dna = alignment(
    sequences: {
        taxon1: "ACGT...",
        taxon2: "ACGG..."
    }
);

// Large alignment - external file
@data
Alignment dna = nexus(file="alignment.nex");
```

For alignments with sequences in the XML, the converter will:
- Extract sequences from `<sequence>` elements
- Inline small alignments (< 80 sequences, < 1000 sites)
- Create external `.nex` files for large alignments

### Natural Domain Bounds

The converter suppresses natural domain bounds for distributions:
- Gamma, Exponential, LogNormal: `lower=0` is suppressed
- Beta, Dirichlet: `lower=0, upper=1` are suppressed

### Fixed Parameter Detection

Parameters not in the state or with `estimate="false"` are automatically converted to literal values:
```java
// BEAST2 XML:
// <parameter id="clockRate.c:hcv" spec="parameter.RealParameter" estimate="false" lower="0.0" name="clock.rate">7.9E-4</parameter>

// Beast2Lang output:
StrictClockModel StrictClock_c_hcv = StrictClockModel(clock.rate=7.9E-4);
```

## Requirements

- Java 11 or higher
- BEAST2 core libraries
- Model Language builder components

## Integration

The converter is designed to integrate with the broader BEAST2 ecosystem:

1. **BeastConversionHandler**: Main entry point for BEAST XML file conversion
2. **ModelObjectFactory**: Interface for creating model objects
3. **Beast2Model**: Internal representation of the converted model

## Error Handling

The converter includes comprehensive error handling:
- Invalid identifiers are automatically normalized
- Missing dependencies are detected and reported
- Circular dependencies are prevented
- Detailed logging available in debug mode

## Extending the Converter

### Adding a New Phase

```java
public class CustomPhase implements ConversionPhase {
    @Override
    public void execute(ConversionContext context) {
        // Your conversion logic here
    }
    
    @Override
    public String getName() {
        return "Custom Phase";
    }
    
    @Override
    public String getDescription() {
        return "Description of what this phase does";
    }
}
```

### Custom Distribution Handling

Extend `BeastConversionUtilities` to add special handling for custom distributions.

## Known Limitations

1. **Operators**: Operator specifications are not converted (Beast2Lang handles operator selection automatically)
2. **Complex Nested Structures**: Some deeply nested BEAST configurations may require manual adjustment
3. **Custom BEAST Packages**: Third-party BEAST packages may need custom conversion rules

## Additional Converters

The converter package also includes experimental support for other formats:

### LinguaPhylo (LPhy) Converter

Converts Beast2Lang models to LinguaPhylo format:

```java
Beast2ToLPHYConverter lphyConverter = new Beast2ToLPHYConverter();
String lphyCode = lphyConverter.convert("model.b2l");
```

Features:
- Maps Beast2Lang functions to LPhy equivalents
- Handles data/model block separation
- Renames variables for consistency with LPhy conventions

### PhyloSpec Converter

Experimental bidirectional conversion with PhyloSpec JSON format:

```java
// Beast2Lang to PhyloSpec
Beast2ToPhyloSpecConverter toPhyloSpec = new Beast2ToPhyloSpecConverter();
JSONObject phyloSpec = toPhyloSpec.convert(model);

// PhyloSpec to Beast2Lang
PhyloSpecToBeast2Converter fromPhyloSpec = new PhyloSpecToBeast2Converter();
Beast2Model model = fromPhyloSpec.convert(phyloSpec);
```