package org.beast2.modelLanguage.builder;

import org.beast2.modelLanguage.model.*;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for the Beast2ModelBuilder class
 */
public class Beast2ModelBuilderTest {

    private Beast2ModelBuilder builder;

    @Before
    public void setUp() {
        builder = new Beast2ModelBuilder();
    }

    @Test
    public void testBuildSimpleModel() {
        String modelString = "beast.base.inference.distribution.ParametricDistribution lognorm = " +
                "beast.base.inference.distribution.LogNormalDistributionModel(M=1, S=1);";

        Beast2Model model = builder.buildFromString(modelString);

        // Verify model structure
        assertNotNull("Model should not be null", model);
        assertEquals("Model should have 1 statement", 1, model.getStatements().size());

        // Check the statement
        Statement statement = model.getStatements().get(0);
        assertTrue("Statement should be a VariableDeclaration", statement instanceof VariableDeclaration);

        VariableDeclaration varDecl = (VariableDeclaration) statement;
        assertEquals("Class name should match", "beast.base.inference.distribution.ParametricDistribution", varDecl.getClassName());
        assertEquals("Variable name should match", "lognorm", varDecl.getVariableName());

        // Check the expression
        Expression expr = varDecl.getValue();
        assertTrue("Expression should be a FunctionCall", expr instanceof FunctionCall);

        FunctionCall functionCall = (FunctionCall) expr;
        assertEquals("Function class name should match", "beast.base.inference.distribution.LogNormalDistributionModel", 
                    functionCall.getClassName());
        assertEquals("Function should have 2 arguments", 2, functionCall.getArguments().size());

        // Check arguments
        List<Argument> args = functionCall.getArguments();
        assertEquals("First argument name should be M", "M", args.get(0).getName());
        assertEquals("Second argument name should be S", "S", args.get(1).getName());

        // Check argument values
        assertTrue("Argument value should be a Literal", args.get(0).getValue() instanceof Literal);
        Literal mValue = (Literal) args.get(0).getValue();
        assertEquals("M value should be 1", 1, mValue.getValue());
        assertEquals("M type should be INTEGER", Literal.LiteralType.INTEGER, mValue.getType());
    }

    @Test
    public void testBuildFromInputStream() throws IOException {
        String modelString = "beast.base.inference.distribution.ParametricDistribution lognorm = " +
                "beast.base.inference.distribution.LogNormalDistributionModel(M=1, S=1);";
        InputStream inputStream = new ByteArrayInputStream(modelString.getBytes(StandardCharsets.UTF_8));

        Beast2Model model = builder.buildFromStream(inputStream);

        // Basic verification
        assertNotNull("Model should not be null", model);
        assertEquals("Model should have 1 statement", 1, model.getStatements().size());
    }

    @Test
    public void testDistributionAssignment() {
        String modelString = "beast.base.inference.parameter.RealParameter lambda ~ Prior(distr=lognorm);";

        Beast2Model model = builder.buildFromString(modelString);

        // Verify model structure
        assertNotNull("Model should not be null", model);
        assertEquals("Model should have 1 statement", 1, model.getStatements().size());

        // Check the statement
        Statement statement = model.getStatements().get(0);
        assertTrue("Statement should be a DistributionAssignment", statement instanceof DistributionAssignment);

        DistributionAssignment distAssign = (DistributionAssignment) statement;
        assertEquals("Class name should match", "beast.base.inference.parameter.RealParameter", distAssign.getClassName());
        assertEquals("Variable name should match", "lambda", distAssign.getVariableName());

        // Check the distribution
        Expression dist = distAssign.getDistribution();
        assertTrue("Distribution should be a FunctionCall", dist instanceof FunctionCall);

        FunctionCall functionCall = (FunctionCall) dist;
        assertEquals("Function class name should match", "Prior", functionCall.getClassName());
        assertEquals("Function should have 1 argument", 1, functionCall.getArguments().size());

        // Check arguments
        Argument arg = functionCall.getArguments().get(0);
        assertEquals("Argument name should be distr", "distr", arg.getName());
        assertTrue("Argument value should be an Identifier", arg.getValue() instanceof Identifier);
        
        Identifier identifier = (Identifier) arg.getValue();
        assertEquals("Identifier name should match", "lognorm", identifier.getName());
    }

    @Test
    public void testCompleteModel() {
        String modelString = 
                "beast.base.inference.distribution.ParametricDistribution lognorm = beast.base.inference.distribution.LogNormalDistributionModel(M=1, S=1);\n" +
                "beast.base.inference.parameter.RealParameter lambda ~ Prior(distr=lognorm);\n" +
                "beast.base.evolution.tree.Tree tree ~ beast.base.evolution.speciation.YuleModel(birthDiffRate=lambda);\n" +
                "beast.base.evolution.alignment.Alignment D ~ beast.base.evolution.likelihood.TreeLikelihood(tree=tree);";

        Beast2Model model = builder.buildFromString(modelString);

        // Verify model structure
        assertNotNull("Model should not be null", model);
        assertEquals("Model should have 4 statements", 4, model.getStatements().size());

        // Check statement types
        assertTrue("First statement should be a VariableDeclaration", 
                  model.getStatements().get(0) instanceof VariableDeclaration);
        assertTrue("Second statement should be a DistributionAssignment", 
                  model.getStatements().get(1) instanceof DistributionAssignment);
        assertTrue("Third statement should be a DistributionAssignment", 
                  model.getStatements().get(2) instanceof DistributionAssignment);
        assertTrue("Fourth statement should be a DistributionAssignment", 
                  model.getStatements().get(3) instanceof DistributionAssignment);

        // Verify references in the model
        DistributionAssignment lambdaAssign = (DistributionAssignment) model.getStatements().get(1);
        FunctionCall priorCall = (FunctionCall) lambdaAssign.getDistribution();
        Identifier lognormRef = (Identifier) priorCall.getArguments().get(0).getValue();
        assertEquals("Lambda prior should reference lognorm", "lognorm", lognormRef.getName());

        DistributionAssignment treeAssign = (DistributionAssignment) model.getStatements().get(2);
        FunctionCall yuleCall = (FunctionCall) treeAssign.getDistribution();
        Identifier lambdaRef = (Identifier) yuleCall.getArguments().get(0).getValue();
        assertEquals("Yule model should reference lambda", "lambda", lambdaRef.getName());

        DistributionAssignment dAssign = (DistributionAssignment) model.getStatements().get(3);
        FunctionCall likelihoodCall = (FunctionCall) dAssign.getDistribution();
        Identifier treeRef = (Identifier) likelihoodCall.getArguments().get(0).getValue();
        assertEquals("Tree likelihood should reference tree", "tree", treeRef.getName());
    }

    @Test
    public void testLiteralTypes() {
        String modelString = 
                "beast.base.inference.parameter.RealParameter param = beast.base.inference.parameter.RealParameter(" +
                "intValue=42, floatValue=3.14, stringValue=\"hello\", boolValue=true);";

        Beast2Model model = builder.buildFromString(modelString);
        
        // Get the function call
        VariableDeclaration varDecl = (VariableDeclaration) model.getStatements().get(0);
        FunctionCall functionCall = (FunctionCall) varDecl.getValue();
        List<Argument> args = functionCall.getArguments();
        
        // Check integer literal
        Literal intLiteral = (Literal) args.get(0).getValue();
        assertEquals("Integer literal value should be 42", 42, intLiteral.getValue());
        assertEquals("Integer literal type should be INTEGER", Literal.LiteralType.INTEGER, intLiteral.getType());
        
        // Check float literal
        Literal floatLiteral = (Literal) args.get(1).getValue();
        assertEquals("Float literal value should be 3.14", 3.14f, floatLiteral.getValue());
        assertEquals("Float literal type should be FLOAT", Literal.LiteralType.FLOAT, floatLiteral.getType());
        
        // Check string literal
        Literal stringLiteral = (Literal) args.get(2).getValue();
        assertEquals("String literal value should be hello", "hello", stringLiteral.getValue());
        assertEquals("String literal type should be STRING", Literal.LiteralType.STRING, stringLiteral.getType());
        
        // Check boolean literal
        Literal boolLiteral = (Literal) args.get(3).getValue();
        assertEquals("Boolean literal value should be true", true, boolLiteral.getValue());
        assertEquals("Boolean literal type should be BOOLEAN", Literal.LiteralType.BOOLEAN, boolLiteral.getType());
    }

    @Test
    public void testModelNodeAccess() {
        String modelString = 
                "beast.base.inference.distribution.ParametricDistribution lognorm = beast.base.inference.distribution.LogNormalDistributionModel(M=1, S=1);\n" +
                "beast.base.inference.parameter.RealParameter lambda ~ Prior(distr=lognorm);";

        Beast2Model model = builder.buildFromString(modelString);
        
        // Access nodes by ID
        ModelNode lognormNode = model.getNodeById("lognorm");
        assertNotNull("Should be able to retrieve lognorm node", lognormNode);
        assertTrue("Lognorm node should be a VariableDeclaration", lognormNode instanceof VariableDeclaration);
        
        ModelNode lambdaNode = model.getNodeById("lambda");
        assertNotNull("Should be able to retrieve lambda node", lambdaNode);
        assertTrue("Lambda node should be a DistributionAssignment", lambdaNode instanceof DistributionAssignment);
        
        // Nonexistent node
        ModelNode nonexistentNode = model.getNodeById("nonexistent");
        assertNull("Nonexistent node should return null", nonexistentNode);
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidSyntax() {
        String invalidModel = "beast.base.inference.parameter.RealParameter lambda ~ ;";
        builder.buildFromString(invalidModel);
        // Should throw an exception
    }
}
