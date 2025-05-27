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
- Conversion to/from LinguaPhylo (LPhy) format
- Support for PhyloSpec-compatible syntax
- Built-in functions for common operations
- Intelligent autoboxing between compatible types
- Secondary inputs mechanism for state node configuration

## Philosophy

BEAST2Lang follows these core principles:

1. **Statistical clarity**: Models are expressed using statistical notation, with `~` indicating that a variable follows a distribution and `=` indicating deterministic functions of parameters.

2. **Clear distinction between random and deterministic variables**: Random variables (using `~`) always represent stochastic elements of the model, while deterministic functions (using `=`) represent fixed calculations or transformations.

3. **Minimalist syntax**: The language focuses on the essential elements of model specification without unnecessary boilerplate.

4. **Declarative approach**: Users declare what the model is, not how to build it.

5. **Close integration**: Direct mapping to BEAST2 objects ensures compatibility with the full BEAST2 ecosystem.

6. **Explicit dependencies**: Variables must be declared before use, making the dependency structure of the model clear.

## Syntax Highlights

### Package Requirements

Models should declare required BEAST2 packages using `requires` statements:

```
requires BEAST.base;
requires feast;
```

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

### Secondary Inputs

When a state node (parameter or tree) has additional configuration, these can be passed as secondary inputs through the distribution:

```
Tree tree ~ YuleModel(birthDiffRate=birthRate, taxonset=TaxonSet);
```

This mechanism ensures proper separation between the stochastic specification (distribution) and the configuration of the sampled variable.

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

BEAST2Lang provides built-in functions for common operations:

#### nexus() function
Loads alignment data from a Nexus file:

```
Alignment alignment = nexus(file="primates.nex");
```

Parameters:
- `file`: (Required) Path to the Nexus file

The `nexus()` function provides a clean, simple way to load sequence alignments directly from Nexus files without needing additional imports or classes.

#### AlignmentFromNexus (requires feast package)
Alternative way to load alignment data from the feast package:

```
requires feast;

AlignmentFromNexus alignment_data = AlignmentFromNexus(fileName="primates.nex");
```

`AlignmentFromNexus` is a class from the feast package that provides additional functionality for loading Nexus files. When converted to LPhy, it maps to `readNexus()`. Note that using this class requires the feast package to be available.

## Automatic Type Conversion (Autoboxing)

BEAST2Lang includes an intelligent autoboxing system that automatically converts compatible types, making models more concise and intuitive. This system eliminates the need for manual conversion or intermediate variables in many cases.

### Supported Autoboxing Conversions

The language supports these automatic conversions:

1. **Array to List**: Any array type is automatically converted to an appropriate List.
   ```
   // String array is converted to a List<String>
   String[] taxonNames = ["Homo_sapiens", "Pan"];
   TaxonSet taxa = TaxonSet(taxon=taxonNames);
   ```

2. **Tree to TreeIntervals**: A Tree can be automatically converted to TreeIntervals for coalescent models.
   ```
   // Tree is automatically converted to TreeIntervals for the primary argument of the Coalescent TreeDistribution class
   Tree tree ~ Coalescent(populationModel=constPop);
   ```

3. **Literal to Parameter**: Numeric, Boolean, or String literals are converted to appropriate Parameter types.
   ```
   // This literal value is automatically converted to a RealParameter
   RealParameter birthRate ~ LogNormalDistributionModel(M=1.0, S=1.0);
   ```

4. **ParametricDistribution to Prior**: Any ParametricDistribution automatically becomes a Prior when needed.
   ```
   // The LogNormalDistributionModel is automatically wrapped in a Prior
   RealParameter birthRate ~ LogNormalDistributionModel(M=1.0, S=1.0);
   ```

5. **SubstitutionModel to SiteModel**: A SubstitutionModel can be used directly where a SiteModel is expected.
   ```
   // The JukesCantor model is automatically wrapped in a SiteModel
   @observed(data=alignment)
   Alignment alignment ~ TreeLikelihood(tree=tree, siteModel=jc);
   ```

6. **Alignment to TaxonSet**: An Alignment can be used where a TaxonSet is required.
   ```
   // The alignment is automatically converted to a TaxonSet
   Tree tree ~ YuleModel(birthDiffRate=birthRate, taxonset=alignment);
   ```

7. **String Array to Taxon List**: String arrays are automatically converted to List<Taxon>.
   ```
   // String array is converted to List<Taxon>
   String[] names = ["Human", "Chimp", "Gorilla"];
   TaxonSet taxa = TaxonSet(taxon=names);
   ```

8. **Double Array to RealParameter**: Double arrays are converted to RealParameter.
   ```
   // Double array is converted to RealParameter
   Double[] values = [0.25, 0.25, 0.25, 0.25];
   Frequencies freqs = Frequencies(frequencies=values);
   ```

9. **Integer Array to IntegerParameter**: Integer arrays are converted to IntegerParameter.
   ```
   // Integer array is converted to IntegerParameter
   Integer[] categories = [0, 1, 2, 1];
   IntegerParameter siteCategories = IntegerParameter(value=categories);
   ```

10. **RealParameter to Frequencies**: A RealParameter can be automatically wrapped in a Frequencies object.
    ```
    RealParameter freqParam ~ Dirichlet(alpha=[2.0, 2.0, 2.0, 2.0]);
    // freqParam is automatically wrapped in Frequencies when used in HKY
    HKY hky = HKY(kappa=kappa, frequencies=freqParam);
    ```

11. **Double Array to Frequencies**: Double arrays can be directly converted to Frequencies (combines rules 8 and 10).
    ```
    // Double array is converted directly to Frequencies
    HKY hky = HKY(kappa=kappa, frequencies=[0.25, 0.25, 0.25, 0.25]);
    ```

These autoboxing capabilities make models significantly more concise and readable while maintaining full compatibility with BEAST2's object model.

## Common Annotations

- `@data`: Marks variables that represent data loaded from files
- `@observed(data=dataVariable)`: Indicates that a random variable is observed, using the referenced data

## LinguaPhylo (LPhy) Conversion

BEAST2Lang can convert models to and from LinguaPhylo format, enabling interoperability with the LPhy ecosystem:

### Converting B2L to LPhy

```bash
./target/beast2lang lphy -i model.b2l -o model.lphy
```

The converter handles:
- Mapping BEAST2 class names to LPhy conventions (distributions uppercase, functions lowercase)
- Parameter name mappings (e.g., `birthDiffRate` → `lambda`, `M/S` → `meanlog/sdlog`)
- Data block separation (data declarations vs model specifications)
- Special handling for observed distributions (adding `L` parameter for `PhyloCTMC`)
- Renaming data variables to match observed variable names

Example conversion:

**B2L Input:**
```
requires BEAST.base;
requires feast;

@data
AlignmentFromNexus alignment_data = AlignmentFromNexus(fileName="primates.nex");

RealParameter birthRate ~ LogNormalDistributionModel(M=1, S=1);
Tree tree ~ YuleModel(birthDiffRate=birthRate, taxonset=alignment_data);

@observed(data=alignment_data)
Alignment alignment ~ TreeLikelihood(tree=tree, siteModel=hky);
```

**LPhy Output:**
```
data {
  alignment = readNexus(file="primates.nex");
}

model {
  birthRate ~ LogNormal(meanlog=1, sdlog=1);
  tree ~ Yule(lambda=birthRate, taxa=alignment);
  alignment ~ PhyloCTMC(tree=tree, Q=hky, L=alignment.nchar());
}
```

## XML Conversion

BEAST2Lang can also convert between B2L and BEAST2 XML formats:

### Converting B2L to XML
```bash
./target/beast2lang convert -i model.b2l --from beast2 --to xml -o model.xml
```

### Decompiling XML to B2L
```bash
./target/beast2lang decompile -i model.xml -o model.b2l
```

## Implementation Details

The language implementation manages connections between random variables and their distributions through a careful mapping between the language syntax and BEAST2's internal structure. This mapping relies on knowledge of specific BEAST2 input parameter names:

### Distribution Primary Input Names

When a random variable is declared with the `~` syntax, BEAST2Lang needs to connect it to the appropriate input parameter of the distribution object. The language relies on the following mappings:

| Distribution Class | Primary Input Name | Description |
|--------------------|-------------------|-------------|
| `Prior` | `"x"` | The parameter to which the prior distribution applies |
| `Coalescent` | `"treeIntervals"` | The tree intervals for coalescent calculations |
| `TreeDistribution` | `"tree"` | The tree parameter for tree distributions |
| `MRCAPrior` | `"tree"` | The tree for MRCA constraints |
| `TreeLikelihood` | `"data"` | The observed data for likelihood calculations |

These mappings are centralized in the implementation to minimize hard-coded dependencies while enabling the language to automatically establish the correct connections between random variables and their distributions. The primary input is what appears on the left side of the `~` operator and is automatically excluded from the function arguments on the right side.

## Example Models

### Standard BEAST2Lang Syntax
```
requires BEAST.base;

// Data using the built-in nexus() function
@data
Alignment alignment = nexus(file="primates.nex");

// Define a log-normal distribution as prior for birth rate
ParametricDistribution lognorm = LogNormalDistributionModel(M=1, S=1);

// Define birth rate parameter with the log-normal prior
RealParameter birthRate ~ Prior(distr=lognorm);

// Define a Yule tree prior that uses the birth rate
Tree tree ~ YuleModel(birthDiffRate=birthRate, taxonset=alignment);

// Define a JC69 substitution model
JukesCantor jc = JukesCantor();

// Define site model with the JC69 substitution model
SiteModel siteModel = SiteModel(substModel=jc);

// Alignment sampled from TreeLikelihood
@observed(data=alignment)
Alignment alignment ~ TreeLikelihood(tree=tree, siteModel=siteModel);
```

### Simplified Syntax with Direct Distribution References and Autoboxing
```
requires BEAST.base;

// Data using the built-in nexus() function
@data
Alignment alignment = nexus(file="primates.nex");

// Define birth rate parameter directly with log-normal distribution
RealParameter birthRate ~ LogNormalDistributionModel(M=1, S=1);

// Define a Yule tree prior with the alignment autoboxed to taxonset
Tree tree ~ YuleModel(birthDiffRate=birthRate, taxonset=alignment);

// Define a JC69 substitution model
JukesCantor jc = JukesCantor();

// Alignment sampled from TreeLikelihood with JC autoboxed to SiteModel
@observed(data=alignment)
Alignment alignment ~ TreeLikelihood(tree=tree, siteModel=jc);
```

### HKY Model Example with feast Package
```
requires BEAST.base;
requires feast;

// Using AlignmentFromNexus from feast package
@data
AlignmentFromNexus alignment_data = AlignmentFromNexus(fileName="primates.nex");

// Parameters with priors
RealParameter birthRate ~ LogNormalDistributionModel(M=1, S=1);
RealParameter kappa ~ LogNormalDistributionModel(M=1, S=1);
RealParameter freqs ~ Dirichlet(alpha=[2.0, 2.0, 2.0, 2.0]);

// Tree with secondary input for taxonset
Tree tree ~ YuleModel(birthDiffRate=birthRate, taxonset=alignment_data);

// HKY substitution model with frequencies
HKY hky = HKY(kappa=kappa, frequencies=freqs);

// Observed alignment
@observed(data=alignment_data)
Alignment alignment ~ TreeLikelihood(tree=tree, siteModel=hky);
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

### Running Models
```
./target/beast2lang run --input examples/example_model.b2l --chainLength 1000000 --logEvery 1000
```

This runs the model directly in BEAST2 with the specified chain length and logging frequency.

### Converting to LPhy
```
./target/beast2lang lphy -i examples/example_model.b2l -o examples/example_model.lphy
```

### Converting to XML
```
./target/beast2lang convert -i examples/example_model.b2l --from beast2 --to xml -o examples/example_model.xml
```

### Decompiling XML to B2L
```
./target/beast2lang decompile -i examples/example_model.xml -o examples/example_model.b2l
```

### Validating Models
```
./target/beast2lang validate examples/example_model.b2l
```

## Mapping to BEAST2

BEAST2Lang automatically handles:

1. Creating appropriate BEAST2 objects for variables
2. Connecting parameters to their distributions via primary inputs
3. Managing dependencies between model components
4. Setting up proper likelihood calculations
5. Converting all elements to BEAST2's XML format
6. Handling secondary inputs through distributions

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
- LinguaPhylo team for the LPhy modeling framework