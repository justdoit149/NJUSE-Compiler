parser grammar SysYParser;

options {
    tokenVocab = SysYLexer;
}

program //程序
    : compUnit
    ;

compUnit //编译单元
    : (funcDef | decl)+ EOF
    ;

decl //声明
    : (constDecl | varDecl)
    ;

constDecl //常量声明
    : CONST bType constDef (COMMA constDef)* SEMICOLON
    ;

bType //基本类型
    : INT
    ;

constDef //常数定义
    : IDENT (L_BRACKT constExp R_BRACKT)* ASSIGN constInitVal
    ;

constInitVal //常量初值
    : constExp
    | L_BRACE (constInitVal (COMMA constInitVal)*)? R_BRACE
    ;

varDecl //变量声明
    : bType varDef (COMMA varDef)* SEMICOLON
    ;

varDef //变量定义
    : IDENT (L_BRACKT constExp R_BRACKT)*
    | IDENT (L_BRACKT constExp R_BRACKT)* ASSIGN initVal
    ;

initVal //变量初值
    : exp
    | L_BRACE (initVal (COMMA initVal)*)? R_BRACE
    ;

funcDef //函数定义
    : funcType IDENT L_PAREN (funcFParams)? R_PAREN block
    ;

funcType //函数类型
    : (VOID | INT)
    ;

funcFParams //函数形参表
    : funcFParam (COMMA funcFParam)*
    ;

funcFParam //函数形参
    : bType IDENT (L_BRACKT R_BRACKT (L_BRACKT exp R_BRACKT)*)?
    ;

block //语句块
    : L_BRACE (blockItem)* R_BRACE
    ;

blockItem //语句块项，将block从stmt中提取出来，处理单独的代码块，便于处理格式
    : (decl | stmt | block)
    ;

stmt //语句，提取出stmt后，将原先的stmt替换为(block|stmt)，并将ELSE IF替换进去，得到的树更好处理
    : lVal ASSIGN exp SEMICOLON
    | (exp)? SEMICOLON
    | IF L_PAREN cond R_PAREN (stmt | block) (ELSE IF L_PAREN cond R_PAREN (stmt | block))* (ELSE (stmt | block))?
    | WHILE L_PAREN cond R_PAREN (stmt | block)
    | BREAK SEMICOLON
    | CONTINUE SEMICOLON
    | RETURN (exp)? SEMICOLON
    ;

//以下是抄助教代码，将文档里的文法改写为左递归的形式，antlr可以帮我们处理左递归

exp //表达式
    : L_PAREN exp R_PAREN
    | lVal
    | number
    | IDENT L_PAREN funcRParams R_PAREN
    | unaryOp exp
    | exp (MUL | DIV | MOD) exp
    | exp (PLUS | MINUS) exp
    ;

cond //条件表达式
    : exp
    | cond (LT | GT | LE | GE) cond
    | cond (EQ | NEQ) cond
    | cond AND cond
    | cond OR cond
    ;

lVal //左值表达式
    : IDENT (L_BRACKT exp R_BRACKT)*
    ;

number //数值
    : INTEGER_CONST
    ;

unaryOp //单目运算符
    : PLUS
    | MINUS
    | NOT
    ;

funcRParams //函数实参表
    : (param (COMMA param)*)?
    ;

param //函数实参
    : exp
    ;

constExp //常量表达式
    : exp
    ;
