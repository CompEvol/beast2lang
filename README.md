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
- Bidirectional conversion with BEAST2 XML
- Conversion to LinguaPhylo (LPhy) format
- Support for PhyloSpec-compatible syntax
- Built-in `nexus()` function for data loading
- Intelligent autoboxing between compatible types
- Secondary inputs mechanism for state node configuration
- Comment support (single-line and multi-line)

## Philosophy

BEAST2Lang follows these core principles:

1. **Statistical clarity**: Models are expressed using statistical notation, with `~` indicating that a variable follows a distribution and `=` indicating deterministic functions of parameters.

2. **Clear distinction between random and deterministic variables**: Random variables (using `~`) always represent stochastic elements of the model, while deterministic functions (using `=`) represent fixed calculations or transformations.

3. **Minimalist syntax**: The language focuses on the essential elements of model specification without unnecessary boilerplate.

4. **Declarative approach**: Users declare what the model is, not how to build it.

5. **Close integration**: Direct mapping to BEAST2 objects ensures compatibility with the full BEAST2 ecosystem.

6. **Explicit dependencies**: Variables must be declared before use, making the dependency structure of the model clear.

## Requirements

- Java 17 or higher
- Maven 3.6 or higher
- BEAST 2.7.x installed (the script will auto-detect BEAST installation)

## Installation

1. Clone the repository:
```bash
git clone https://github.com/CompEvol/beast2lang.git
cd beast2lang
```

2. Clone the beastlauncher dependency:
```bash
git clone https://github.com/CompEvol/beastlauncher.git
cd beastlauncher
mvn clean install
cd ..
```

3. Install BEAST2 jars to local Maven repository:
```bash
./install-beast-jars.sh
```

4. Build with Maven:
```bash
mvn clean package
```

This will create:
- A JAR file with dependencies in the `target/` directory
- An executable script `./target/beast2lang` that handles BEAST2 class loading

## Usage

BEAST2Lang uses a shell script wrapper that ensures proper BEAST2 class loading. The script will automatically detect your BEAST2 installation.

### Running Models
```bash
./target/beast2lang run --input examples/example_model.b2l [options]
```

Options:
- `--chainLength` - MCMC chain length (default: 10000000)
- `--logEvery` - Logging interval (default: 1000)
- `--traceFileName` - Trace log file name (default: trace.log)
- `--treeFileName` - Tree log file name (default: tree.trees)
- `--seed` - Random seed for MCMC run
- `--threads` - Number of threads (default: 1)
- `--resume` - Resume from previous run
- `--phylospec` - Use PhyloSpec syntax
- `--debug` - Enable debug logging
- `--output` - Output XML file (default: model.xml)

### Validating Models
```bash
./target/beast2lang validate examples/example_model.b2l
```

Options:
- `--phylospec` - Use PhyloSpec syntax

### Converting to LinguaPhylo (LPhy)
```bash
./target/beast2lang lphy -i examples/example_model.b2l -o examples/example_model.lphy
```

Options:
- `--debug` - Enable debug logging

### Converting Between Formats
```bash
./target/beast2lang convert -i input.b2l --from beast2 --to xml -o output.xml
```

Supported formats:
- `beast2` - Beast2Lang format
- `phylospec` - PhyloSpec JSON format
- `lphy` - LinguaPhylo format
- `xml` - BEAST2 XML format

Options:
- `--chainLength` - Default MCMC chain length for XML generation
- `--logEvery` - Default logging interval for XML generation
- `--traceFileName` - Default trace log file name for XML generation
- `--debug` - Enable debug logging

### Decompiling XML to Beast2Lang
```bash
./target/beast2lang decompile -i model.xml -o model.b2l
```

This converts existing BEAST2 XML files back to Beast2Lang format.

Options:
- `--debug` - Enable debug logging

## Environment Setup

The `beast2lang` script automatically:
- Detects BEAST2 installations (looks for BEAST 2.7.x in standard locations)
- Sets up the correct classpath for BEAST2 packages
- Configures Java heap size (-Xmx4g) and stack size (-Xss256m)
- Uses the BEAST2 bundled Java runtime if available

If BEAST2 is not in a standard location, set the `BEAST` environment variable:
```bash
export BEAST="/path/to/BEAST 2.7.7"
./target/beast2lang run --input model.b2l
```

## Syntax Guide

### Comments
Beast2Lang supports both single-line and multi-line comments:

```
// This is a single-line comment

/* This is a
   multi-line comment */
```

### Package Requirements

Models should declare required BEAST2 packages using `requires` statements:

```
requires BEAST.base;
requires feast;
```

### Import Statements

For using specific Java classes:

```
import beast.evolution.alignment.*;
import beast.evolution.tree.*;
```

### Variable Declarations

**Deterministic Variables** (using `=`):
```
ClassName variableName = ConstructorOrFunction(param1=value1, param2=value2);
```

**Random Variables** (using `~`):
```
ClassName variableName ~ Distribution(param1=value1, param2=value2);
```

### Annotations

```
@annotation(param=value)
ClassName variableName = Constructor(...);
```

Common annotations:
- `@data` - Marks variables that represent data loaded from files
- `@observed(data=dataVariable)` - Indicates that a random variable is observed

### Arrays

Arrays are defined using square brackets:
```
String[] taxonNames = ["Homo_sapiens", "Pan", "Gorilla"];
Double[] frequencies = [0.25, 0.25, 0.25, 0.25];
Integer[] categories = [0, 1, 2, 1];
```

### Built-in Functions

#### nexus() function
The only built-in function for loading alignment data from Nexus files:

```
// Basic usage
Alignment alignment = nexus(file="primates.nex");

// With custom ID
Alignment myData = nexus(file="data.nex", id="myData");
```

Parameters:
- `file` - (Required) Path to the Nexus file
- `id` - (Optional) ID for the alignment object

## Automatic Type Conversion (Autoboxing)

BEAST2Lang includes an intelligent autoboxing system that automatically converts between compatible types:

1. **Array to List**: Arrays automatically convert to Lists
2. **Tree to TreeIntervals**: For coalescent models
3. **Literal to Parameter**: Numeric/boolean/string literals to Parameters
4. **ParametricDistribution to Prior**: Automatic Prior wrapping
5. **SubstitutionModel to SiteModel**: Automatic SiteModel wrapping
6. **Alignment to TaxonSet**: For tree prior specifications
7. **String Array to Taxon List**: For taxon specifications
8. **Double Array to RealParameter**: For numeric parameters
9. **Integer Array to IntegerParameter**: For integer parameters
10. **RealParameter to Frequencies**: For frequency specifications
11. **Double Array to Frequencies**: Direct frequency specification

## Example Models

### Simple Jukes-Cantor Model
```
requires BEAST.base;

// Load data using built-in nexus() function
@data
Alignment alignment = nexus(file="primates.nex");

// Define birth rate parameter with log-normal prior
RealParameter birthRate ~ LogNormalDistributionModel(M=1, S=1);

// Define tree with Yule prior (alignment autoboxed to taxonset)
Tree tree ~ YuleModel(birthDiffRate=birthRate, taxonset=alignment);

// Define JC69 substitution model
JukesCantor jc = JukesCantor();

// Observed alignment (JC autoboxed to SiteModel)
@observed(data=alignment)
Alignment alignment ~ TreeLikelihood(tree=tree, siteModel=jc);
```

### HKY Model with Frequencies
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

// Tree with Yule prior
Tree tree ~ YuleModel(birthDiffRate=birthRate, taxonset=alignment_data);

// HKY substitution model
HKY hky = HKY(kappa=kappa, frequencies=freqs);

// Observed alignment
@observed(data=alignment_data)
Alignment alignment ~ TreeLikelihood(tree=tree, siteModel=hky);
```

### Coalescent Model
```
requires BEAST.base;

@data
Alignment alignment = nexus(file="hcv.nex");

// Coalescent population size
RealParameter popSize ~ LogNormalDistributionModel(M=0, S=1);
ConstantPopulation constPop = ConstantPopulation(popSize=popSize);

// Tree with coalescent prior (tree autoboxed to TreeIntervals)
Tree tree ~ Coalescent(populationModel=constPop);

// Site model
RealParameter kappa ~ LogNormalDistributionModel(M=2, S=0.5);
HKY hky = HKY(kappa=kappa, frequencies=[0.25, 0.25, 0.25, 0.25]);

@observed(data=alignment)
Alignment alignment ~ TreeLikelihood(tree=tree, siteModel=hky);
```

## Debug Mode

All commands support a `--debug` flag that enables detailed logging:

```bash
./target/beast2lang run --input model.b2l --debug
```

This will:
- Show detailed error messages with full stack traces
- Display model structure before XML generation
- Log all parsing and conversion steps
- Show the classpath and BEAST package path being used
- Dump the complete model structure for debugging

## PhyloSpec Support

Beast2Lang supports PhyloSpec-compatible syntax for increased interoperability. Enable it with the `--phylospec` flag:

```bash
./target/beast2lang run --input model.b2l --phylospec
./target/beast2lang validate model.b2l --phylospec
```

## Example Workflow

1. Create a model in Beast2Lang format (`model.b2l`)
2. Validate the model:
   ```bash
   ./target/beast2lang validate model.b2l
   ```
3. Run the analysis:
   ```bash
   ./target/beast2lang run --input model.b2l --chainLength 1000000
   ```
4. Or convert to other formats:
   ```bash
   # To XML
   ./target/beast2lang convert -i model.b2l --from beast2 --to xml -o model.xml
   
   # To LPhy
   ./target/beast2lang lphy -i model.b2l -o model.lphy
   
   # From XML back to Beast2Lang
   ./target/beast2lang decompile -i model.xml -o model.b2l
   ```

## Project Structure

```
beast2lang/
├── src/
│   ├── main/
│   │   ├── antlr4/          # ANTLR grammar definition
│   │   ├── java/
│   │   │   └── org/beast2/modelLanguage/
│   │   │       ├── parser/   # Generated parser
│   │   │       ├── builder/  # Model construction
│   │   │       ├── converter/# Format converters
│   │   │       ├── model/    # AST representations
│   │   │       └── phylospec/# PhyloSpec support
│   │   └── resources/
│   └── test/
├── examples/                 # Example B2L models
├── pom.xml                  # Maven configuration
└── README.md
```

## Implementation Notes

### Distribution Primary Input Names

When using the `~` syntax, BEAST2Lang automatically connects random variables to distribution inputs:

| Distribution Class        | Primary Input | Description                                       |
|---------------------------|---------------|---------------------------------------------------|
| `Prior`                   | `"x"` | The parameter being assigned a prior              |
| `Coalescent`              | `"treeIntervals"` | Tree intervals for coalescent                     |
| `BayesianSkyline`         | `"treeIntervals"` | Tree intervals for Bayesian skyline               |
| `TreeDistribution`        | `"tree"` | Tree for tree distributions                       |
| `MRCAPrior`               | `"tree"` | Tree for MRCA constraints                         |
| `TreeLikelihood`          | `"data"` | (observed) alignment data                         |
| `MarkovChainDistribution` | `"parameter"` | The parameter being assigned a Markov chain prior |

### Parser Technology

BEAST2Lang uses ANTLR4 for parsing, providing robust error messages and flexible syntax handling.

## Contributing

We welcome contributions! Please:
1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- The BEAST2 development team for their work on Bayesian evolutionary analysis
- ANTLR project for the parsing infrastructure
- LinguaPhylo team for the LPhy modeling framework
- Contributors to the PhyloSpec standard