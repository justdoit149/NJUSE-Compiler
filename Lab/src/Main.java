import java.io.IOException;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.util.*;

//把前面实验的输出注释掉，避免影响后续实验。最后完工后可以把这些输出全部取消注释
public  class Main {
    public static void main(String[] args) throws IOException {
        //Part1：词法分析
        //从命令行获得待解析的文件路径，并输入词法分析器
        if (args.length < 1) {
            System.err.println("input path is required");
        }
        String source = args[0];
        CharStream input = CharStreams.fromFileName(source);
        SysYLexer sysYLexer = new SysYLexer(input);
        //将默认的监听器替换为自定义的监听器，用来监听词法错误
        sysYLexer.removeErrorListeners();
        sysYLexer.addErrorListener(MyLexerErrorListener.getMyLexerErrorListener());
        //获取词法分析的结果，若有错误，则直接返回(上面的监听器的syntaxError已经完成了错误输出)
        List<? extends Token> myTokens = sysYLexer.getAllTokens();
        if (MyLexerErrorListener.hasLexerError) {
            return;
        }
        for (Token t : myTokens) {
//            printSysYTokenInformation(t);
        }
        sysYLexer.reset();//需要重置输入流，要不然下面语法分析读不到东西

        //Part2 & Part3：语法分析、语义分析
        //获取词法单元流，传入语法分析器
        CommonTokenStream tokens = new CommonTokenStream(sysYLexer);
        SysYParser sysYParser = new SysYParser(tokens);
        //添加语法监听器
        sysYParser.removeErrorListeners();
        sysYParser.addErrorListener(MyParserErrorListener.getMyParserErrorListener());
        ParseTree tree = sysYParser.program();
        if(MyParserErrorListener.hasParserError){
            return;
        }
        //语法访问者
        MySysYParserVisitor<Void> parserVisitor = MySysYParserVisitor.getMySysYParserVisitor();
        Void visitResult = parserVisitor.visit(tree);
        parserVisitor.handleOutput();
        //语义访问者
        MySysYSemanticVisitor<Void> semanticVisitor = MySysYSemanticVisitor.getMySysYSemanticVisitor();
        semanticVisitor.visit(tree);
        if(!semanticVisitor.hasSemanticError){
//            System.err.println("No semantic errors in the program!");
        }

        //Part4：中间代码生成
//        if (args.length < 2) {
//            System.err.println("input path is required");
//        }
//        String IRPath = args[1];
//        MyLLVMIRVisitor llvmIRVisitor = new MyLLVMIRVisitor(args[1]);
        MyLLVMIRVisitor llvmIRVisitor = new MyLLVMIRVisitor();
        llvmIRVisitor.visit(tree);

        //Part5：目标代码生成
        if (args.length < 2) {
            System.err.println("input path is required");
        }
        String RiscVPath = args[1];
        MyRISCVGenerator riscvGenerator = new MyRISCVGenerator(llvmIRVisitor.module, args[1]);
        riscvGenerator.generateRiscVCode();

    }

    //Part1输出函数，用于格式化输出词法分析结果，注意如果是数字常量需要转换为十进制输出。
    private static void printSysYTokenInformation(Token t){
        int index = t.getType()-1;//注意数组是从0开始、但t.getType是从1开始的。
        String tokenInformation = "";
        if(SysYLexer.ruleNames[index].equals("INTEGER_CONST")){
            if(t.getText().startsWith("0x") || t.getText().startsWith("0X")){
                tokenInformation = String.format("%s %d at Line %d.",SysYLexer.ruleNames[index],Integer.parseInt(t.getText().substring(2),16),t.getLine());
            }else if(t.getText().startsWith("0") && t.getText().length()>1){
                tokenInformation = String.format("%s %d at Line %d.",SysYLexer.ruleNames[index],Integer.parseInt(t.getText().substring(1),8),t.getLine());
            }else{
                tokenInformation = String.format("%s %d at Line %d.",SysYLexer.ruleNames[index],Integer.parseInt(t.getText(),10),t.getLine());
            }

        }else{
            tokenInformation = String.format("%s %s at Line %d.",SysYLexer.ruleNames[index],t.getText(),t.getLine());
        }
        System.err.println(tokenInformation);
    }
}