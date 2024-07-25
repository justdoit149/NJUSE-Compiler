lexer grammar SysYLexer;//.g4文件第一行，说明我要写SysYLexer的词法和语法（与文件名一致）
//.g4文件每行的语句格式为：“规则名:选项1|选项2|……|选项n;”前面的选项优先级高。
CONST: 'const';
INT: 'int';
VOID: 'void';
IF: 'if';
ELSE: 'else';
WHILE: 'while';
BREAK: 'break';
CONTINUE: 'continue';
RETURN: 'return';
PLUS: '+';
MINUS: '-';
MUL: '*';
DIV: '/';
MOD: '%';
ASSIGN: '=';
EQ: '==';
NEQ: '!=';
LT: '<';
GT: '>';
LE: '<=';
GE: '>=';
NOT: '!';
AND: '&&';
OR: '||';
L_PAREN: '(';
R_PAREN: ')';
L_BRACE: '{';
R_BRACE: '}';
L_BRACKT: '[';
R_BRACKT: ']';
COMMA: ',';
SEMICOLON: ';';
IDENT: ('_' | LETTER) WORD* ;//标识符
INTEGER_CONST: ('0' | [1-9]NUMBER*)
             | ('0'[0-7]+)
             | (('0x'|'0X')[0-9A-Fa-f]+);//数字常量，包含十进制数，0开头的八进制数，0x或0X开头的十六进制数
WS: [ \r\n\t]+ -> skip; // 空白符，->skip表示碰到后忽略（词法分析阶段就解决）
LINE_COMMENT: '//' .*? '\n' -> skip; // 单行注释，‘.’为通配符，可匹配任何字符；‘?’表示非贪婪匹配，选择能匹配的最短串（不加问号是贪婪匹配，选择能匹配的最长串）
MULTILINE_COMMENT: '/*' .*? '*/' -> skip; //多行注释。

//fragment的规则类似于private的，不会作为文法规则，用于辅助前面的规则。
fragment LETTER: [a-zA-Z];
fragment NUMBER: [0-9];
fragment WORD: '_' | LETTER | NUMBER;