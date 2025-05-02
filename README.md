# BEAST2Lang

BEAST2Lang is a domain-specific language (DSL) for creating statistical phylogenetic models in BEAST2, with a focus on clarity, expressiveness, and statistical modeling principles.

## Overview

BEAST2Lang provides a concise, readable syntax for defining Bayesian evolutionary models, making it easier to construct complex analyses without dealing directly with BEAST2's XML configuration. The language is designed to bridge the gap between statistical thinking and BEAST2 implementation, allowing researchers to express models in a way that's closer to mathematical notation.

Key features:
- Simple, declarative syntax for model specification
- Statistical notation using `~` for random variables
- Support for observed data and latent parameters
- Direct integration with BEAST2's Java objects
- Import mechanism for BEAST2 packages and classes
- Support for PhyloSpec-compatible syntax
- Built-in functions for common operations
- Intelligent autoboxing between compatible types

## Philosophy

BEAST2Lang follows these core principles:

1. **Statistical clarity**: Models are expressed using statistical notation, with `~` indicating that a variable follows a distribution and `=` indicating deterministic functions of parameters.

2. **Clear distinction between random and deterministic variables**: Random variables (using `~`) always represent stochastic elements of the model, while deterministic functions (using `=`) represent fixed calculations or transformations.

3. **Minimalist syntax**: The language focuses on the essential elements of model specification without unnecessary boilerplate.

4. **Declarative approach**: Users declare what the model is, not how to build it.

5. **Close integration**: Direct mapping to BEAST2 objects ensures compatibility with the full BEAST2 ecosystem.

6. **Explicit dependencies**: Variables must be declared before use, making the dependency structure of the model clear.

## Syntax Highlights

### Deterministic Variable Declarations
Used for deterministic functions of parameters (like substitution and site model components):

```
ClassName variableName = CalculationNode(inputName1=value1, inputName2=value2);
```

### Random Variable Declarations
Used for variables that follow a distribution, with two possible patterns:

**Direct Distribution Pattern**:
```
ClassName variableName ~ Distribution(inputName1=value1, inputName2=value2);
```

BEAST2Lang supports both `Distribution` and `ParametricDistribution` subclasses directly after the `~` operator. When a `ParametricDistribution` is used (like LogNormal, Exponential, etc.), BEAST2Lang automatically wraps it in a `Prior` object.

**Prior Pattern**:
```
ClassName variableName ~ Prior(distr=ParametricDistribution(...));
```

### Annotations

```
@annotation(param=value)
ClassName variableName = Constructor(...);
```

### Imports

```
import beast.package.name.*;
```

### Built-in Functions

BEAST2Lang currently provides one built-in function to allow reading an alignment from file:

#### nexus() function
Loads alignment data from a Nexus file:

```
Alignment alignment = nexus(file="primates.nex");
```

Parameters:
- `file`: (Required) Path to the Nexus file

The `nexus()` function provides a clean, simple way to load sequence alignments directly from Nexus files without needing additional imports or classes.

## Automatic Type Conversion (Autoboxing)

BEAST2Lang includes an intelligent autoboxing system that automatically converts compatible types, making models more concise and intuitive. This system eliminates the need for manual conversion or intermediate variables in many cases.

### Supported Autoboxing Conversions

The language supports these automatic conversions:

1. **Literal to Parameter**: Numeric, Boolean, or String literals are converted to appropriate Parameter types.
   ```
   // This literal value is automatically converted to a RealParameter
   RealParameter birthRate ~ LogNormalDistributionModel(M=1.0, S=1.0);
   ```

2. **ParametricDistribution to Prior**: Any ParametricDistribution automatically becomes a Prior when needed.
   ```
   // The LogNormalDistributionModel is automatically wrapped in a Prior
   RealParameter birthRate ~ LogNormalDistributionModel(M=1.0, S=1.0);
   ```

3. **SubstitutionModel to SiteModel**: A SubstitutionModel can be used directly where a SiteModel is expected.
   ```
   // The JukesCantor model is automatically wrapped in a SiteModel
   @observed(data=alignment_data)
   Alignment alignment ~ TreeLikelihood(tree=tree, siteModel=jc);
   ```

4. **Alignment to TaxonSet**: An Alignment can be used where a TaxonSet is required.
   ```
   // The alignment_data is automatically converted to a TaxonSet
   Tree tree ~ YuleModel(birthDiffRate=birthRate, taxonset=alignment_data);
   ```

5. **Array to List**: Any array type is automatically converted to an appropriate List.
   ```
   // String array is converted to a List<String>
   String[] taxonNames = ["Homo_sapiens", "Pan"];
   // Array elements are also autoboxed when needed
   TaxonSet taxa = TaxonSet(taxon=taxonNames);
   ```

These autoboxing capabilities make models significantly more concise and readable while maintaining full compatibility with BEAST2's object model.

## Common Annotations

- `@data`: Marks variables that represent data loaded from files
- `@observed(data=dataVariable)`: Indicates that a random variable is observed, using the referenced data

## Implementation Details

The language implementation manages connections between random variables and their distributions through a careful mapping between the language syntax and BEAST2's internal structure. This mapping relies on knowledge of specific BEAST2 input parameter names:

### Distribution Argument Input Names

When a random variable is declared with the `~` syntax, BEAST2Lang needs to connect it to the appropriate input parameter of the distribution object. The language relies on the following hard-coded mappings:

| Distribution Class | Argument Input Name | Description |
|--------------------|---------------------|-------------|
| `Prior` | `"x"` | The parameter to which the prior distribution applies |
| `SpeciesTreeDistribution` | `"tree"` | The tree parameter for tree distributions |
| `TreeLikelihood` | `"data"` | The observed data for likelihood calculations |

These mappings are centralized in the implementation to minimize hard-coded dependencies while enabling the language to automatically establish the correct connections between random variables and their distributions.

## Example Models

### Standard BEAST2Lang Syntax
```
// Import BEAST2 types, distributions and calculation nodes
import beast.base.inference.parameter.*;
import beast.base.inference.distribution.*;
import beast.base.evolution.tree.*;
import beast.base.evolution.speciation.*;
import beast.base.evolution.substitutionmodel.*;
import beast.base.evolution.sitemodel.*;
import beast.base.evolution.alignment.*;
import beast.base.evolution.likelihood.*;

// Data using the built-in nexus() function
@data
Alignment alignment_data = nexus(file="primates.nex");

@data
TaxonSet taxa = TaxonSet(alignment=alignment_data);

// Define a log-normal distribution as prior for birth rate
ParametricDistribution lognorm = LogNormalDistributionModel(M=1, S=1);

// Define birth rate parameter with the log-normal prior
RealParameter birthRate ~ Prior(distr=lognorm);

// Define a Yule tree prior that uses the birth rate
Tree tree ~ YuleModel(birthDiffRate=birthRate, taxonset=taxa);

// Define a JC69 substitution model
JukesCantor jc = JukesCantor();

// Define site model with the JC69 substitution model
SiteModel siteModel = SiteModel(substModel=jc);

// Alignment sampled from TreeLikelihood
@observed(data=alignment_data)
Alignment alignment ~ TreeLikelihood(tree=tree, siteModel=siteModel);
```

### Simplified Syntax with Direct Distribution References and Autoboxing of SubstitutionModel and Alignment
```
// Import BEAST2 types, distributions and calculation nodes
import beast.base.inference.parameter.*;
import beast.base.inference.distribution.*;
import beast.base.evolution.tree.*;
import beast.base.evolution.speciation.*;
import beast.base.evolution.substitutionmodel.*;
import beast.base.evolution.sitemodel.*;
import beast.base.evolution.alignment.*;
import beast.base.evolution.likelihood.*;

// Data using the built-in nexus() function
@data
Alignment alignment_data = nexus(file="primates.nex");

// Define birth rate parameter directly with log-normal distribution
RealParameter birthRate ~ LogNormalDistributionModel(M=1, S=1);

// Define a Yule tree prior that uses the birth rate
Tree tree ~ YuleModel(birthDiffRate=birthRate, taxonset=alignment_data);

// Define a JC69 substitution model
JukesCantor jc = JukesCantor();

// Alignment sampled from TreeLikelihood
@observed(data=alignment_data)
Alignment alignment ~ TreeLikelihood(tree=tree, siteModel=jc);
```

## PhyloSpec Support

BEAST2Lang supports PhyloSpec-compatible syntax for increased interoperability with other modeling frameworks. To use PhyloSpec syntax, add the `--phylospec` flag to your commands:

```
./target/beast2lang run --input examples/example_model.b2l --phylospec
```

PhyloSpec syntax simplifies and standardizes distribution names and parameters, automatically mapping them to BEAST2 equivalents.

## Installation

1. Clone the repository:
```
git clone https://github.com/CompEvol/beast2lang.git
cd beast2lang
```

2. Clone the beastlauncher dependency:
```
git clone https://github.com/CompEvol/beastlauncher.git
cd beastlauncher
mvn clean install
cd ..
```

3. Install BEAST2 jars to local Maven repository:
```
./install-beast-jars.sh
```

4. Build with Maven:
```
mvn clean package
```

## Usage

```
./target/beast2lang run --input examples/example_model.b2l --chainLength 1000000 --logEvery 1000
```

This runs the model directly in BEAST2 with the specified chain length and logging frequency.

## Mapping to BEAST2

BEAST2Lang automatically handles:

1. Creating appropriate BEAST2 objects for variables
2. Connecting parameters to their distributions
3. Managing dependencies between model components
4. Setting up proper likelihood calculations
5. Converting all elements to BEAST2's XML format

## Dependencies

- Java 11 or higher
- BEAST2 (for running the generated XML)
- Maven (for building)
- beastlauncher (for launching BEAST2 instances)

The beastlauncher project provides infrastructure for launching BEAST2 instances from Java applications. It is a required dependency for running BEAST2Lang models directly without going through the XML generation step.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- The BEAST2 development team for their work on Bayesian evolutionary analysis
- ANTLR project for the parsing infrastructure