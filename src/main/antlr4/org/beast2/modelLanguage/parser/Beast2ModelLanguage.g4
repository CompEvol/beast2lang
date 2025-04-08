/**
 * Grammar for Beast2 Model Definition Language
 */
grammar Beast2ModelLanguage;

// Parser Rules
program
    : statement+ EOF
    ;

statement
    : variableDeclaration
    | distributionAssignment
    ;

variableDeclaration
    : className identifier EQUALS expression SEMICOLON
    ;

distributionAssignment
    : className identifier TILDE expression SEMICOLON
    ;

expression
    : functionCall                  # FunctionCallExpr
    | identifier                    # IdentifierExpr
    | literal                       # LiteralExpr
    ;

functionCall
    : className LPAREN argumentList? RPAREN
    ;

argumentList
    : argument (COMMA argument)*
    ;

argument
    : identifier EQUALS argumentValue
    ;

argumentValue
    : expression
    | literal
    ;

className
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
DOT         : '.';

BOOLEAN_LITERAL
    : 'true'
    | 'false'
    ;

IDENTIFIER
    : [a-zA-Z][a-zA-Z0-9_]*
    ;

INTEGER_LITERAL
    : [0-9]+
    ;

FLOAT_LITERAL
    : [0-9]+ '.' [0-9]*
    | '.' [0-9]+
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