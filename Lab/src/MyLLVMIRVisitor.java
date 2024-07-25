import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.bytedeco.llvm.LLVM.*;
import org.bytedeco.javacpp.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

import static org.bytedeco.llvm.global.LLVM.*;

public class MyLLVMIRVisitor extends SysYParserBaseVisitor<LLVMValueRef>{
    public final LLVMModuleRef module;

    public final LLVMBuilderRef builder;

    public final LLVMTypeRef i32Type;

    public final LLVMTypeRef i1Type;

    public final LLVMTypeRef voidType;

    private final LLVMValueRef zero;

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

    private HashMap<String, LLVMValueRef> llvmSymbolTable;

    private final HashMap<String, LLVMTypeRef> llvmFuncTypeTable;

    private boolean isGlobal = true;

    private LLVMValueRef currentFunction;

    private Stack<LLVMBasicBlockRef> trueBlock = new Stack<>();

    private Stack<LLVMBasicBlockRef> whileCondBlock = new Stack<>();

    private Stack<LLVMBasicBlockRef> whileNextBlock = new Stack<>();

    public MyLLVMIRVisitor(){
        //初始化LLVM
        LLVMInitializeCore(LLVMGetGlobalPassRegistry());
        LLVMLinkInMCJIT();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMInitializeNativeTarget();
        //创建module
        module = LLVMModuleCreateWithName("module");
        //初始化IRBuilder，后续将使用这个builder去生成LLVM IR
        builder = LLVMCreateBuilder();
        //类型重命名，方便以后使用
        i32Type = LLVMInt32Type();
        i1Type = LLVMInt1Type();
        voidType = LLVMVoidType();
        zero = LLVMConstInt(i32Type, 0, 0);
        //函数类型表，用于处理函数调用
        llvmFuncTypeTable = new HashMap<>();
    }

    //在这里处理输出
    @Override
    public LLVMValueRef visitProgram(SysYParser.ProgramContext ctx) {
        super.visitProgram(ctx);
        BytePointer error = new BytePointer();
        if (LLVMPrintModuleToFile(module, "main.ll", error) != 0) {
            LLVMDisposeMessage(error);
        }
        return null;
    }

    @Override
    public LLVMValueRef visitConstDef(SysYParser.ConstDefContext ctx) {
        if(isGlobal){
            LLVMValueRef pointer = LLVMAddGlobal(module, i32Type, ctx.IDENT().getText());
            LLVMValueRef initVal = visitExp(ctx.constInitVal().constExp().exp());
            LLVMSetInitializer(pointer, initVal);
        }else{
            //申请一块能存放int型的内存
            LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, ctx.IDENT().getText());
            //将数值存入该内存
            LLVMValueRef initVal = visitExp(ctx.constInitVal().constExp().exp());
            LLVMBuildStore(builder, initVal, pointer);
            //同时将该内存对应的LLVMValueRef放入符号表，以备后续使用
            llvmSymbolTable.put(ctx.IDENT().getText(), pointer);
        }
        return null;
    }

    @Override
    public LLVMValueRef visitVarDef(SysYParser.VarDefContext ctx) {
        if(isGlobal){
            LLVMValueRef pointer = LLVMAddGlobal(module, i32Type, ctx.IDENT().getText());
            if(ctx.ASSIGN() != null){
                LLVMValueRef initVal = visitExp(ctx.initVal().exp());
                LLVMSetInitializer(pointer, initVal);
            }else{
                LLVMSetInitializer(pointer, zero);
            }
        }else{
            LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, ctx.IDENT().getText());
            if(ctx.ASSIGN() != null){
                LLVMValueRef initVal = visitExp(ctx.initVal().exp());
                LLVMBuildStore(builder, initVal, pointer);
            }else{
                LLVMBuildStore(builder, zero, pointer);
            }
            llvmSymbolTable.put(ctx.IDENT().getText(), pointer);
        }
        return null;
    }

    // 访问到函数定义时为module添加function, 并为function添加basicBlock
    @Override
    public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx) {
        isGlobal = false;
        llvmSymbolTable = new HashMap<>();
        //生成返回值类型
        LLVMTypeRef returnType = (ctx.funcType().INT() == null ? voidType : i32Type);
        //生成函数参数类型
        int argumentCount = ctx.funcFParams() == null ? 0 : ctx.funcFParams().funcFParam().size();
        PointerPointer<Pointer> argumentTypes = new PointerPointer<>(argumentCount);
        for(int i = 0; i < argumentCount; i++){
            argumentTypes.put(i, i32Type);
        }
        //生成函数类型。LLVMFunctionType的参数依次为：返回值类型，参数类型，参数个数，是否接受可变参数
        LLVMTypeRef ft = LLVMFunctionType(returnType, argumentTypes, argumentCount,  0);
        //生成函数，即向之前创建的module中添加函数。LLVMAddFunction参数依次为：module，函数名，函数类型ft
        LLVMValueRef function = LLVMAddFunction(module, ctx.IDENT().getText(), ft);
        //通过如下语句在函数中加入基本块，一个函数可以加入多个基本块
        LLVMBasicBlockRef block1 = LLVMAppendBasicBlock(function, ctx.IDENT().getText()+"Entry");
        //选择要在哪个基本块后追加指令
        LLVMPositionBuilderAtEnd(builder, block1);
        //将参数加入符号表，函数类型加入函数类型表，currentFunction指向当前的函数
        for(int i = 0; i < argumentCount; i++){
            LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, ctx.funcFParams().funcFParam(i).IDENT().getText());
            LLVMBuildStore(builder, LLVMGetParam(function, i), pointer);
            llvmSymbolTable.put(ctx.funcFParams().funcFParam(i).IDENT().getText(), pointer);
        }
        llvmFuncTypeTable.put(ctx.IDENT().getText(), ft);
        currentFunction = function;
        //访问函数定义节点
        super.visitFuncDef(ctx);
        //hardtest3.sy：lli: out.ir:11:1: error: expected instruction opcode } ^
        if(ctx.funcType().VOID() != null){
            LLVMBuildRetVoid(builder);
        }
        //访问完成，符号表、当前函数重新设为null，回到global
        currentFunction = null;
        llvmSymbolTable = null;
        isGlobal = true;
        return null;
    }

    @Override
    public LLVMValueRef visitBlock(SysYParser.BlockContext ctx) {
        return super.visitBlock(ctx);
    }

    // 访问到return语句时使用IRBuilder在基本块内生成生成ret指令
    @Override
    public LLVMValueRef visitStmt(SysYParser.StmtContext ctx) {
        if(ctx.RETURN() != null){
            if(ctx.exp() != null){
                LLVMValueRef result = visitExp(ctx.exp());
                LLVMBuildRet(builder, result);
            }else{
                LLVMBuildRetVoid(builder);
            }
            return null;
        }else if(ctx.ASSIGN() != null){
            LLVMValueRef value = visitExp(ctx.exp());
            String lValName = ctx.lVal().IDENT().getText();
            if(llvmSymbolTable.get(lValName) != null){
                LLVMBuildStore(builder, value, llvmSymbolTable.get(lValName));
            }else{
                LLVMBuildStore(builder, value, LLVMGetNamedGlobal(module, lValName));
            }
            return null;
        }else if(!ctx.IF().isEmpty()){
            int n = ctx.cond().size();
            LLVMBasicBlockRef ifTrue = null;
            LLVMBasicBlockRef ifFalse = null;
            LLVMBasicBlockRef next = LLVMAppendBasicBlock(currentFunction, "entry");
            //处理if、else if
            for(int i = 0; i < n; i++){
                ifTrue = LLVMAppendBasicBlock(currentFunction, "true");
                ifFalse = LLVMAppendBasicBlock(currentFunction, "false");
                LLVMMoveBasicBlockAfter(next, LLVMGetLastBasicBlock(currentFunction));
                trueBlock.push(ifTrue);
                LLVMValueRef condTemp = visitCond(ctx.cond(i));
                LLVMValueRef condAnsTemp = LLVMBuildICmp(builder, LLVMIntNE, zero, condTemp, "tmp");
                LLVMBuildCondBr(builder, condAnsTemp, ifTrue, ifFalse);
                //True
                LLVMPositionBuilderAtEnd(builder, ifTrue);
                ParseTree trueTree = ctx.getChild(4+i*6);
                if(getNodeType(trueTree).equals("stmt")){
                    visitStmt((SysYParser.StmtContext) trueTree);
                }else{
                    visitBlock((SysYParser.BlockContext) trueTree);
                }
                LLVMBuildBr(builder, next);
                //False
                LLVMPositionBuilderAtEnd(builder, ifFalse);
                trueBlock.pop();
            }
            //处理else
            if(ctx.getChildCount() == n*6+1){
                ParseTree trueTree = ctx.getChild(n*6);
                if(getNodeType(trueTree).equals("stmt")){
                    visitStmt((SysYParser.StmtContext) trueTree);
                }else{
                    visitBlock((SysYParser.BlockContext) trueTree);
                }
            }
            //next
            LLVMBuildBr(builder, next);
            LLVMPositionBuilderAtEnd(builder, next);
            return null;
        }else if(ctx.WHILE() != null){
            LLVMBasicBlockRef whileCond = LLVMAppendBasicBlock(currentFunction, "whileCond");
            LLVMBasicBlockRef whileBody = LLVMAppendBasicBlock(currentFunction, "whileBody");
            LLVMBasicBlockRef next = LLVMAppendBasicBlock(currentFunction, "entry");
            whileCondBlock.add(whileCond);
            whileNextBlock.add(next);
            trueBlock.add(whileBody);
            LLVMBuildBr(builder, whileCond);
            LLVMPositionBuilderAtEnd(builder, whileCond);
            LLVMValueRef condTemp = visitCond(ctx.cond(0));
            LLVMValueRef condAnsTemp = LLVMBuildICmp(builder, LLVMIntNE, zero, condTemp, "tmp");
            LLVMBuildCondBr(builder, condAnsTemp, whileBody, next);
            LLVMPositionBuilderAtEnd(builder, whileBody);
            ParseTree trueTree = ctx.getChild(4);
            if(getNodeType(trueTree).equals("stmt")){
                visitStmt((SysYParser.StmtContext) trueTree);
            }else{
                visitBlock((SysYParser.BlockContext) trueTree);
            }
            LLVMBuildBr(builder, whileCond);
            LLVMPositionBuilderAtEnd(builder, next);
            whileCondBlock.pop();
            whileNextBlock.pop();
            trueBlock.pop();
            return null;
        }else if(ctx.BREAK() != null){
            if(!whileNextBlock.isEmpty()){
                LLVMBasicBlockRef whileNext = whileNextBlock.peek();
                LLVMBuildBr(builder, whileNext);
            }
            return null;
        }else if(ctx.CONTINUE() != null){
            if(!whileCondBlock.isEmpty()){
                LLVMBasicBlockRef whileCond = whileCondBlock.peek();
                LLVMBuildBr(builder, whileCond);
            }
            return null;
        }
        return super.visitStmt(ctx);
    }

    @Override
    public LLVMValueRef visitExp(SysYParser.ExpContext ctx) {
        if(ctx.number() != null){
            String numberText = ctx.number().getText();
            int number;
            if(numberText.startsWith("0x") || numberText.startsWith("0X")){
                number = Integer.parseInt(numberText.substring(2),16);
            }else if(numberText.startsWith("0") && numberText.length()>1){
                number = Integer.parseInt(numberText.substring(1),8);;
            }else{
                number = Integer.parseInt(numberText);
            }
            return LLVMConstInt(i32Type, number, 0);
        }else if(ctx.lVal() != null){
            String lValName = ctx.lVal().IDENT().getText();
            LLVMValueRef value;
            if(llvmSymbolTable.get(lValName) != null){
                value = LLVMBuildLoad(builder, llvmSymbolTable.get(lValName), lValName);
            }else{
                value = LLVMBuildLoad(builder, LLVMGetNamedGlobal(module, lValName), lValName);
            }
            return value;
        }else if(ctx.unaryOp() != null){
            LLVMValueRef value = visitExp(ctx.exp(0));
            if(ctx.unaryOp().NOT() != null){
                value = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(i32Type, 0, 0), value, "tmp");
                value = LLVMBuildXor(builder, value, LLVMConstInt(LLVMInt1Type(), 1, 0), "tmp");
                value = LLVMBuildZExt(builder, value, i32Type, "tmp");
            }else if(ctx.unaryOp().MINUS() != null){
                value = LLVMBuildSub(builder, zero, value, "tmp");
            }
            return value;
        }else if(ctx.exp().size() == 1){
            return visitExp(ctx.exp(0));
        }else if(ctx.IDENT() != null) {
            //访问实参，并将结果加入参数列表
            int argumentCount = ctx.funcRParams().isEmpty() ? 0 : ctx.funcRParams().param().size();
            LLVMValueRef[] params = new LLVMValueRef[argumentCount];
            for(int i = 0; i < argumentCount; i++){
                params[i] = visitExp(ctx.funcRParams().param(i).exp());
            }
            PointerPointer<Pointer> argumentList = new PointerPointer<>(params);
            LLVMValueRef function = LLVMGetNamedFunction(module, ctx.IDENT().getText());
            LLVMTypeRef returnType = LLVMGetReturnType(LLVMGetElementType(LLVMTypeOf(function)));
            String returnName = LLVMGetTypeKind(returnType) == LLVMVoidTypeKind ? "" : "returnValue";
            //生成函数调用IR，参数依次为：所在的builder，函数类型，函数，实参列表，实参数量，存储返回值的变量名

            return LLVMBuildCall2(builder, llvmFuncTypeTable.get(ctx.IDENT().getText()), function, argumentList, argumentCount, returnName);
            //也可以使用LLVMBuildCall，不需要输入函数类型，会自动推断
//            return LLVMBuildCall(builder, function, argumentList, argumentCount, "returnValue");
        }else{
            LLVMValueRef result = null;
            if(ctx.PLUS() != null){
                result = LLVMBuildAdd(builder, visitExp(ctx.exp(0)), visitExp(ctx.exp(1)), "tmp");
            }else if(ctx.MINUS() != null){
                result = LLVMBuildSub(builder, visitExp(ctx.exp(0)), visitExp(ctx.exp(1)), "tmp");
            }else if(ctx.MUL() != null){
                result = LLVMBuildMul(builder, visitExp(ctx.exp(0)), visitExp(ctx.exp(1)), "tmp");
            }else if(ctx.DIV() != null){
                result = LLVMBuildSDiv(builder, visitExp(ctx.exp(0)), visitExp(ctx.exp(1)), "tmp");
            }else if(ctx.MOD() != null){
                result = LLVMBuildSRem(builder, visitExp(ctx.exp(0)), visitExp(ctx.exp(1)), "tmp");
            }
            return result;
        }
    }

    @Override
    public LLVMValueRef visitCond(SysYParser.CondContext ctx) {
        if(ctx.exp() != null){
            return visitExp(ctx.exp());
        }
        LLVMValueRef condLeft = null;
        LLVMValueRef condRight = null;
        LLVMValueRef cond = null;
        if(ctx.AND() == null && ctx.OR() == null){
            condLeft = visitCond(ctx.cond(0));
            condRight = visitCond(ctx.cond(1));
            if(ctx.LT() != null){
                cond = LLVMBuildICmp(builder, LLVMIntSLT, condLeft, condRight, "cond");
            }else if(ctx.GT() != null){
                cond = LLVMBuildICmp(builder, LLVMIntSGT, condLeft, condRight, "cond");
            }else if(ctx.LE() != null){
                cond = LLVMBuildICmp(builder, LLVMIntSLE, condLeft, condRight, "cond");
            }else if(ctx.GE() != null){
                cond = LLVMBuildICmp(builder, LLVMIntSGE, condLeft, condRight, "cond");
            }else if(ctx.EQ() != null){
                cond = LLVMBuildICmp(builder, LLVMIntEQ, condLeft, condRight, "cond");
            }else if(ctx.NEQ() != null){
                cond = LLVMBuildICmp(builder, LLVMIntNE, condLeft, condRight, "cond");
            }
        }else{
            //短路求值
            LLVMBasicBlockRef previousBlock = LLVMGetInsertBlock(builder);
            condLeft = LLVMBuildICmp(builder, LLVMIntNE, zero, visitCond(ctx.cond(0)), "leftTmp");
            LLVMBasicBlockRef blockTemp = LLVMAppendBasicBlock(currentFunction, "blockTemp");
            LLVMBasicBlockRef blockPhi = LLVMAppendBasicBlock(currentFunction, "blockPhi");
            if(ctx.AND() != null){
                LLVMBuildCondBr(builder, condLeft, blockTemp, blockPhi);
            }else{
                LLVMBuildCondBr(builder, condLeft, blockPhi, blockTemp);
            }
            LLVMPositionBuilderAtEnd(builder, blockTemp);
            condRight = LLVMBuildICmp(builder, LLVMIntNE, zero, visitCond(ctx.cond(1)), "rightTmp");
            LLVMBuildBr(builder, blockPhi);
            LLVMMoveBasicBlockBefore(blockTemp, trueBlock.peek());
            LLVMMoveBasicBlockBefore(blockPhi, trueBlock.peek());
            LLVMPositionBuilderAtEnd(builder, blockPhi);
            cond = LLVMBuildPhi(builder, i1Type, "phi");
            LLVMValueRef[] incomingValues = {condLeft, condRight};
            LLVMBasicBlockRef[] incomingBlocks = {previousBlock, blockTemp};
            LLVMAddIncoming(cond, new PointerPointer<>(incomingValues), new PointerPointer<>(incomingBlocks), 2);
        }
        return LLVMBuildZExt(builder, cond, i32Type, "i32cond");
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
