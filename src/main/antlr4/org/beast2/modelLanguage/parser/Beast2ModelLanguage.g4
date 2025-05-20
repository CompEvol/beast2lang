/**
 * Grammar for Beast2 Model Definition Language
 */
grammar Beast2ModelLanguage;

// Parser Rules
program
    : (importStatement | requiresStatement)* statement+ EOF
    ;

importStatement
    : IMPORT importName SEMICOLON
    ;

requiresStatement
    : REQUIRES pluginName SEMICOLON
    ;

importName
    : qualifiedName (DOT STAR)?
    ;

pluginName
    : IDENTIFIER (DOT IDENTIFIER)*   // Allow periods for plugin names like "BEAST.base"
    ;

statement
    : annotation? variableDeclaration
    | annotation? distributionAssignment
    ;

annotation
    : AT annotationName annotationBody?
    ;

annotationName
    : identifier
    ;

annotationBody
    : LPAREN annotationParameter (COMMA annotationParameter)* RPAREN
    ;

annotationParameter
    : identifier EQUALS (literal | identifier)
    ;

variableDeclaration
    : className identifier EQUALS expression SEMICOLON
    ;

distributionAssignment
    : className identifier TILDE expression SEMICOLON
    ;

expression
    : functionCall                  # FunctionCallExpr
    | nexusFunction                 # NexusFunctionExpr
    | identifier                    # IdentifierExpr
    | literal                       # LiteralExpr
    | arrayLiteral                  # ArrayLiteralExpr
    ;

functionCall
    : className LPAREN argumentList? RPAREN
    ;

nexusFunction
    : NEXUS LPAREN argumentList? RPAREN
    ;

argumentList
    : argument (COMMA argument)*
    ;

argument
    : argumentName EQUALS argumentValue
    ;

argumentName
    : identifier (DOT identifier)*  // Allows dot-separated names like "clock.rate"
    ;

argumentValue
    : expression
    | literal
    | arrayLiteral
    ;

arrayLiteral
    : LBRACKET (arrayElement (COMMA arrayElement)*)? RBRACKET
    ;

arrayElement
    : literal
    | identifier
    | functionCall
    ;

className
    : qualifiedName (LBRACKET RBRACKET)*  // Allow multiple dimensions like Type[][]
    ;

qualifiedName
    : identifier (DOT identifier)*
    ;

identifier
    : IDENTIFIER
    ;

literal
    : FLOAT_LITERAL
    | INTEGER_LITERAL
    | STRING_LITERAL
    | BOOLEAN_LITERAL
    ;

// Lexer Rules
TILDE       : '~';
EQUALS      : '=';
SEMICOLON   : ';';
COMMA       : ',';
LPAREN      : '(';
RPAREN      : ')';
LBRACE      : '{';
RBRACE      : '}';
LBRACKET    : '[';
RBRACKET    : ']';
DOT         : '.';
AT          : '@';
STAR        : '*';
IMPORT      : 'import';
REQUIRES    : 'requires';
NEXUS       : 'nexus';

BOOLEAN_LITERAL
    : 'true'
    | 'false'
    ;

IDENTIFIER
    : [a-zA-Z][a-zA-Z0-9_]*
    ;

INTEGER_LITERAL
    : '-'? [0-9]+
    ;

FLOAT_LITERAL
    : '-'? [0-9]+ '.' [0-9]*
    | '-'? '.' [0-9]+
    ;

STRING_LITERAL
    : '"' ('\\"' | ~["])* '"'
    ;

LINE_COMMENT
    : '//' ~[\r\n]* -> skip
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> skip
    ;

WS
    : [ \t\r\n]+ -> skip
    ;