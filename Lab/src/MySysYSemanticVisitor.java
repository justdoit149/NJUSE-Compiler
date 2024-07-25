import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

public class MySysYSemanticVisitor<T extends Void> extends SysYParserBaseVisitor<Void>{
    private static final MySysYSemanticVisitor<Void> visitor = new MySysYSemanticVisitor<>();
    private MySysYSemanticVisitor(){}
    public static MySysYSemanticVisitor<Void> getMySysYSemanticVisitor(){
        return visitor;
    }

    public boolean hasSemanticError = false;

    private Scope currentScope;

    private Stack<SemanticErrorType> lastErrorType = new Stack<>();

    private Stack<Integer> lastErrorLine = new Stack<>();

    //词法类型
    private static final List<String> lexerTypeList = Arrays.asList(
            "NOTHING", "CONST", "INT", "VOID", "IF", "ELSE", "WHILE", "BREAK", "CONTINUE", "RETURN",
            "PLUS", "MINUS", "MUL", "DIV", "MOD", "ASSIGN", "EQ", "NEQ", "LT", "GT",
            "LE", "GE", "NOT", "AND", "OR", "L_PAREN", "R_PAREN", "L_BRACE", "R_BRACE", "L_BRACKT",
            "R_BRACKT", "COMMA", "SEMICOLON", "IDENT", "INTEGER_CONST", "WS", "LINE_COMMENT", "MULTILINE_COMMENT"
    );//从1开始，所以数组的0位置要填充一个东西

    //语法类型
    private static final List<String> parserTypeList = Arrays.asList(
            "program", "compUnit", "decl", "constDecl", "bType", "constDef", "constInitVal", "varDecl", "varDef", "initVal",
            "funcDef", "funcType", "funcFParams", "funcFParam", "block", "blockItem", "stmt", "exp", "cond", "lVal",
            "number", "unaryOp", "funcRParams", "funcRParam", "constExp", "funcName"
    );//从0开始，不填充

    //.ordinal()返回枚举对象的序数（从0开始），.toString()返回该枚举对象字符串形式的名称
    private enum SemanticErrorType{
        VarNotDeclare, FuncNotDefine, VarRepeatDeclare, FuncRepeatDefine,
        AssignNotMatch, OpNotMatch, RetNotMatch, ParamNotMatch,
        IndexNotMatch, UseVarAsFunc, LValueNotMatch
    }

    //注意，访问者模式一旦重写这些Visit方法，就会把原来默认的DFS访问给覆盖掉，因此必须自己访问子节点，或是调用父类的Visit方法
    @Override
    public Void visitProgram(SysYParser.ProgramContext ctx) {
        //全局作用域
        currentScope = new Scope(null);
        return super.visitProgram(ctx);
    }

    @Override
    public Void visitConstDef(SysYParser.ConstDefContext ctx) {
        String constName = ctx.IDENT().getText();
        if(currentScope.getSymbolInThisScope(constName) != null){
            handleSemanticError(SemanticErrorType.VarRepeatDeclare, ctx.IDENT().getSymbol().getLine());
            return null;
        }
        Type l;
        if(ctx.constExp().isEmpty()){
            l = new TypeBasic();
        }else{//数组
            int n = ctx.constExp().size();
            l = new TypeBasic();
            for(int i = 0; i < n; i++){
                l = new TypeArray(l);
            }
        }
        super.visitConstDef(ctx);
        if(alreadyHandleError(SemanticErrorType.AssignNotMatch, ctx.ASSIGN().getSymbol().getLine())) return null;
        Type r = getInitType(ctx.constInitVal());
        if(!typeIsMatched(l, r) && !(isTypeArray(l) && isTypeArray(r))){
            handleSemanticError(SemanticErrorType.AssignNotMatch, ctx.ASSIGN().getSymbol().getLine());
        }else{
            currentScope.addSymbol(constName, l);
        }
        return null;
    }

    @Override
    public Void visitVarDef(SysYParser.VarDefContext ctx) {
        String constName = ctx.IDENT().getText();
        if(currentScope.getSymbolInThisScope(constName) != null){
            handleSemanticError(SemanticErrorType.VarRepeatDeclare, ctx.IDENT().getSymbol().getLine());
            return null;
        }
        Type l;
        if(ctx.constExp().isEmpty()){
            l = new TypeBasic();
        }else{//数组
            int n = ctx.constExp().size();
            l = new TypeBasic();
            for(int i = 0; i < n; i++){
                l = new TypeArray(l);
            }
        }
        if(ctx.ASSIGN() != null){
            super.visitVarDef(ctx);
            if(alreadyHandleError(SemanticErrorType.AssignNotMatch, ctx.ASSIGN().getSymbol().getLine())) return null;
            Type r = getInitType(ctx.initVal());
            if(!typeIsMatched(l, r) && !(isTypeArray(r) && isTypeArray(l))){
                handleSemanticError(SemanticErrorType.AssignNotMatch, ctx.ASSIGN().getSymbol().getLine());
            }else {
                currentScope.addSymbol(constName, l);
            }
            return null;
        }
        currentScope.addSymbol(constName, l);
        return super.visitVarDef(ctx);
    }


    @Override
    public Void visitFuncDef(SysYParser.FuncDefContext ctx) {
        String constName = ctx.IDENT().getText();
        if(currentScope.getSymbolInThisScope(constName) != null){
            handleSemanticError(SemanticErrorType.FuncRepeatDefine, ctx.IDENT().getSymbol().getLine());
            return null;
        }
        Type retType = (ctx.funcType().VOID() == null ? new TypeBasic() : new TypeVoid());
        ArrayList<Type> paramsType;
        currentScope = new Scope(currentScope);
        if(ctx.funcFParams() != null){
            visit(ctx.funcFParams());
            paramsType = currentScope.getAllSymbolInThisScope();
        }else{
            paramsType = new ArrayList<>();
        }
        //必须先将此函数加入符号表，才能访问block。反过来的话无法处理函数的递归调用等情况
        currentScope.getFatherScope().addSymbol(ctx.IDENT().getText(), new TypeFunction(retType, paramsType));
        visit(ctx.block());
        currentScope = currentScope.getFatherScope();
        return null; //上面已经Visit过了，这里不能再Visit，因此直接return null
    }

    //为了降低实验难度，我们保证测试用例中的函数参数不会为多维（二维及以上）数组
    @Override
    public Void visitFuncFParam(SysYParser.FuncFParamContext ctx) {
        String constName = ctx.IDENT().getText();
        if(currentScope.getSymbolInThisScope(constName) != null){
            handleSemanticError(SemanticErrorType.VarRepeatDeclare, ctx.IDENT().getSymbol().getLine());
            return null;
        }
        Type type = (ctx.getChildCount() == 2 ? new TypeBasic() : new TypeArray(new TypeBasic()));
        currentScope.addSymbol(constName, type);
        return super.visitFuncFParam(ctx);
    }

    @Override
    public Void visitBlock(SysYParser.BlockContext ctx) {
        currentScope = new Scope(currentScope);
        super.visitBlock(ctx);
        currentScope = currentScope.getFatherScope();
        return null;
    }

    @Override
    public Void visitStmt(SysYParser.StmtContext ctx) {
        if(ctx.RETURN() != null){
            ArrayList<Type> globalSymbolTable = currentScope.getGlobalScope().getAllSymbolInThisScope();
            TypeFunction funcType = (TypeFunction) globalSymbolTable.get(globalSymbolTable.size()-1);
            Type retType = funcType.getRetType();
            if(ctx.exp() == null){
                if(!isTypeVoid(retType)){
                    handleSemanticError(SemanticErrorType.RetNotMatch, ctx.RETURN().getSymbol().getLine());
                    return null;
                }
            } else {
                super.visitStmt(ctx);
                if (alreadyHandleError(SemanticErrorType.RetNotMatch, ctx.exp().getStart().getLine())) return null;
                if (!typeIsMatched(retType, getExpType(ctx.exp()))) {
                    handleSemanticError(SemanticErrorType.RetNotMatch, ctx.exp().getStart().getLine());
                }
                return null;
            }
        }else if(getNodeType(ctx.getParent()).equals("stmt")){
            currentScope = new Scope(currentScope);
            super.visitStmt(ctx);
            currentScope = currentScope.getFatherScope();
            return null;
        }else if(ctx.ASSIGN() != null){
            if(isTypeFunction(currentScope.getSymbol(ctx.lVal().IDENT().getText()))){
                handleSemanticError(SemanticErrorType.LValueNotMatch, ctx.ASSIGN().getSymbol().getLine());
                return null;
            }
            super.visitStmt(ctx);
            if(alreadyHandleError(SemanticErrorType.AssignNotMatch, ctx.ASSIGN().getSymbol().getLine())) return null;
            Type l = getLValType(ctx.lVal()), r = getExpType(ctx.exp());
            if(!typeIsMatched(l, r)){
                handleSemanticError(SemanticErrorType.AssignNotMatch, ctx.ASSIGN().getSymbol().getLine());
            }
            return null;
        }
        return super.visitStmt(ctx);
    }

    @Override
    public Void visitExp(SysYParser.ExpContext ctx) {
        if(ctx.IDENT() != null){
            Type type = currentScope.getSymbol(ctx.IDENT().getText());
            if(type == null){
                handleSemanticError(SemanticErrorType.FuncNotDefine, ctx.IDENT().getSymbol().getLine());
                return null;
            }else if(!isTypeFunction(type)){
                handleSemanticError(SemanticErrorType.UseVarAsFunc, ctx.IDENT().getSymbol().getLine());
                return null;
            }
        }else if(ctx.unaryOp() != null){
            super.visitExp(ctx);
            if(alreadyHandleError(null, ctx.getStart().getLine())) return null;
            Type expType = getExpType(ctx.exp(0));
            if(!isTypeBasic(expType)){
                handleSemanticError(SemanticErrorType.OpNotMatch, ctx.unaryOp().getStart().getLine());
            }
            return null;
        }else if(ctx.exp().size() == 2){
            super.visitExp(ctx);
            if(alreadyHandleError(null, ctx.getStart().getLine())) return null;
            if(!isTypeBasic(getExpType(ctx.exp(0))) || !isTypeBasic(getExpType(ctx.exp(1)))){
                handleSemanticError(SemanticErrorType.OpNotMatch, ctx.getStart().getLine());
            }
            return null;
        }
        return super.visitExp(ctx);
    }

    @Override
    public Void visitCond(SysYParser.CondContext ctx) {
        super.visitCond(ctx);
        if(alreadyHandleError(null, ctx.getStart().getLine())) return null;
        Type condType = getCondType(ctx);
        if(!isTypeBasic(condType)){
            handleSemanticError(SemanticErrorType.OpNotMatch, ctx.getStart().getLine());
        }
        return null;
    }

    @Override
    public Void visitLVal(SysYParser.LValContext ctx) {
        if(currentScope.getSymbol(ctx.IDENT().getText()) == null){
            handleSemanticError(SemanticErrorType.VarNotDeclare, ctx.IDENT().getSymbol().getLine());
            return null;
        }
        Type lValTypeTemp = getLValType(ctx);
        if(lValTypeTemp == null){
            handleSemanticError(SemanticErrorType.IndexNotMatch, ctx.IDENT().getSymbol().getLine());
            return null;
        }
        return super.visitLVal(ctx);
    }

    @Override
    public Void visitFuncRParams(SysYParser.FuncRParamsContext ctx) {
        //变量未定义应该先于函数参数类型不匹配来检测，所以这里打了补丁
        super.visitFuncRParams(ctx);
        if(alreadyHandleError(SemanticErrorType.ParamNotMatch, ctx.getStart().getLine())) return null;
        SysYParser.ExpContext exp = (SysYParser.ExpContext) ctx.getParent();
        TypeFunction funcType = (TypeFunction) currentScope.getSymbol(exp.IDENT().getText());
        ArrayList<Type> fParamsType = funcType.getParamsType();
        //空List不能直接转ArrayList！！
        ArrayList<SysYParser.ParamContext> rParamsTypeTemp = (ctx.param().isEmpty() ? new ArrayList<>() : (ArrayList<SysYParser.ParamContext>) ctx.param());
        if(fParamsType.size() != rParamsTypeTemp.size()){
            handleSemanticError(SemanticErrorType.ParamNotMatch, ctx.getStart().getLine());
            return null;
        }
        for(int i = 0; i < rParamsTypeTemp.size(); i++){
            Type rParamTypeTemp = getExpType(rParamsTypeTemp.get(i).exp());
            if(!typeIsMatched(fParamsType.get(i), rParamTypeTemp)){
                handleSemanticError(SemanticErrorType.ParamNotMatch, ctx.getStart().getLine());
                return null;
            }
        }
        return null;
    }

    private void handleSemanticError(SemanticErrorType errorType, int line){
        hasSemanticError = true;
        lastErrorLine.add(line);
        lastErrorType.add(errorType);
        String semanticErrorInformation = String.format("Error type %d at Line %d: %s",errorType.ordinal()+1,line,errorType);
        System.err.println(semanticErrorInformation);
    }

    //若返回true，说明本行已经有其他类型的错误，不需要再报告了
    //主要用于5、6、7、8等，这些规则的优先级应该低于“变量或函数未定义”
    //如果传入的errortype是null，则表示不看类型、只看本行有没有处理过
    private boolean alreadyHandleError(SemanticErrorType errorType, int line){
        if(lastErrorLine.isEmpty()){
            return false;
        }else if(lastErrorLine.peek() == line && lastErrorType.peek() != errorType){
            return true;
        }else{
            return false;
        }
    }

    private void visitAllChild(ParseTree parseTree){
        int n = parseTree.getChildCount();
        for(int i = 0; i < n; i++){
            visit(parseTree.getChild(i));
        }
    }

    //获取常量或变量的初始值类型（initVal或constInitVal）
    //只可能返回TypeArray、TypeBasic、null
    //TODO:目前如果是带大括号的，统一看做一维数组，后续看看这里要不要展开处理
    private Type getInitType(ParseTree tree){
        if(getNodeType(tree).equals("constInitVal")){
            SysYParser.ConstInitValContext constInitVal = (SysYParser.ConstInitValContext) tree;
            if(constInitVal.constExp() != null){
                return getExpType(constInitVal.constExp().exp());
            }
            return new TypeArray(new TypeBasic());
        }else if(getNodeType(tree).equals("initVal")){
            SysYParser.InitValContext initVal = (SysYParser.InitValContext) tree;
            if(initVal.exp() != null){
                return getExpType(initVal.exp());
            }
            return new TypeArray(new TypeBasic());
        }
        return null;
    }

    //这里只获取类型，且会检查运算符类型、函数返回值类型，不匹配返回Null。类型的校验在Visit函数里进行
    //但若标识符是函数，则会返回函数类型，等后续处理
    private Type getExpType(SysYParser.ExpContext exp){
        if(exp == null) return null;
        if(exp.PLUS() != null || exp.MINUS() != null
        || exp.MUL() != null || exp.DIV() != null || exp.MOD() != null){
            Type l = getExpType(exp.exp(0)), r = getExpType(exp.exp(1));
            return typeIsMatched(l, r) ? l : null;
        }else if(exp.unaryOp() != null){
            Type expType = getExpType(exp.exp(0));
            return isTypeBasic(expType) ? expType : null;
        }else if(exp.IDENT() != null){
            Type funcTypeTemp = currentScope.getSymbol(exp.IDENT().getText());
            if(!isTypeFunction(funcTypeTemp)) return null;
            TypeFunction funcType = (TypeFunction) funcTypeTemp;
            return isTypeBasic(funcType.getRetType()) ? funcType.getRetType() : null;
        }else if(exp.L_PAREN() != null){
            return getExpType(exp.exp(0));
        }else if(exp.lVal() != null){
            return getLValType(exp.lVal());
        }else{
            return new TypeBasic();
        }
    }

    //若是二元条件式，则两个条件的类型不一致则返回null；若是一个exp，则返回exp的类型
    private Type getCondType(SysYParser.CondContext cond){
        if(cond == null) return null;
        if(cond.exp() != null){
            return getExpType(cond.exp());
        }else{
            Type l = getCondType(cond.cond(0)), r = getCondType(cond.cond(1));
            return typeIsMatched(l, r) ? l : null;
        }
    }

    //标识符未定义、取下标次数超过数组维数，都会返回null，但若标识符是函数，则会返回函数类型，等后续处理
    private Type getLValType(SysYParser.LValContext lVal){
        if(lVal == null) return null;
        Type lValType = currentScope.getSymbol(lVal.IDENT().getText());
        int n = lVal.exp().size();
        if(isTypeArray(lValType)){
            TypeArray lValTypeTemp = (TypeArray) lValType;
            for( ; n > 0; n--){
                lValType = lValTypeTemp.getElementType();
                if(isTypeBasic(lValType) && n > 1){
                    return null;
                }else if(isTypeBasic(lValType)){
                    break;
                }else{
                    lValTypeTemp = (TypeArray) lValType;
                }
            }
            return lValType;
        }else if(isTypeBasic(lValType)){
            return n > 0 ? null : lValType;
        }else if(isTypeFunction(lValType)){
            return lValType;
        }
        return null;
    }

    private boolean isTypeVoid(Type type){
        return type instanceof TypeVoid;
    }

    private boolean isTypeBasic(Type type){
        return type instanceof TypeBasic;
    }

    private boolean isTypeFunction(Type type){
        return type instanceof TypeFunction;
    }

    private boolean isTypeArray(Type type){
        return type instanceof TypeArray;
    }

    //检测类型是否匹配
    private boolean typeIsMatched(Type l, Type r){
        if(isTypeVoid(l) && isTypeVoid(r)){
            return true;
        }else if(isTypeBasic(l) && isTypeBasic(r)){
            return true;
        }else if(isTypeArray(l) && isTypeArray(r)){
            return typeIsMatched(((TypeArray)l).getElementType(), ((TypeArray)r).getElementType());
        }else if(isTypeFunction(l) && isTypeFunction(r)){
            TypeFunction lf = (TypeFunction)l, rf = (TypeFunction)r;
            if(!typeIsMatched(lf.getRetType(), rf.getRetType())) return false;
            if(lf.getParamsType().size() != rf.getParamsType().size()) return false;
            for(int i = 0; i < lf.getParamsType().size(); i++){
                if(!typeIsMatched(lf.getParamsType().get(i), rf.getParamsType().get(i))) return false;
            }
            return true;
        }
        return false;
    }

    //获取节点的类型（返回字符串，字符串内容在lexerTypeList和parserTypeList中）
    private String getNodeType(ParseTree node) {
        if (node instanceof RuleNode) {
            return parserTypeList.get(((RuleNode) node).getRuleContext().getRuleIndex());
        } else if (node instanceof TerminalNode) {
            if(((TerminalNode) node).getSymbol().getType() > -1){
                return lexerTypeList.get(((TerminalNode) node).getSymbol().getType());
            }
        }
        return null;
    }
}
