import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.*;



public class MySysYParserVisitor<T extends Void> extends SysYParserBaseVisitor<Void>{
    private class colorStackItem{
        ParseTree node;
        String parserRuleName;
        int otherCode;
        int colorCodeFront;
        int colorCodeEnd;
        public colorStackItem(ParseTree node, String parserRuleName, int otherCode, int colorCodeFront, int colorCodeEnd){
            this.node = node;
            this.parserRuleName = parserRuleName;
            this.otherCode = otherCode;
            this.colorCodeFront = colorCodeFront;
            this.colorCodeEnd = colorCodeEnd;
        }
    }

    private static final MySysYParserVisitor<Void> visitor = new MySysYParserVisitor<>();
    private MySysYParserVisitor(){}
    public static MySysYParserVisitor<Void> getMySysYParserVisitor(){
        return visitor;
    }

    private StringBuilder outputCode = new StringBuilder();

    //记录括号层级
    private int rainbowLevel = 0;

    //记录缩进层级
    private int indentLevel = 0;

    //记录DFS的节点栈
    private Stack<colorStackItem> colorStack = new Stack<>();

    //记录颜色与对应的ANSI值
    private static final Map<String, Integer> colorMap = new HashMap<String, Integer>() {{
        put("Underlined", 4);
        put("Magenta", 35);
        put("BrightRed", 91);
        put("BrightGreen", 92);
        put("BrightYellow", 93);
        put("BrightBlue", 94);
        put("BrightMagenta", 95);
        put("BrightCyan", 96);
        put("White", 97);
    }};

    //括号颜色数组
    private static final String[] colorModMap = {
            "BrightRed", "BrightGreen", "BrightYellow", "BrightBlue", "BrightMagenta", "BrightCyan"
    };

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

    public void handleOutput(){
        while (outputCode.substring(0,9).equals("\u001B[0m\n\u001B[0m")){
            outputCode.delete(0,9);
        }
        System.out.println(outputCode.toString());
    }

    //方法重写，DFS访问语法树
    @Override
    public Void visitChildren(RuleNode node) {
        Void result = defaultResult();
        int n = node.getChildCount();
        enterNode(node);
        for (int i = 0; i < n && this.shouldVisitNextChild(node, result); i++) {
            ParseTree child = node.getChild(i);
            this.visit(child);
        }
        exitNode(node);
        return null;
    }

    //进入节点时的行为，注意，开头换行比结尾换行更好，其中一个原因是一换行就要接着跟缩进
    private void enterNode(RuleNode node) {
        highlight(node);
        String ruleNameTemp = getNodeType(node);
        if(ruleNameTemp.equals("decl")){
            newLine();
        }else if(ruleNameTemp.equals("funcDef")){
            newLine();
            newLine();
        }else if(ruleNameTemp.equals("stmt")){
            if(getNodeType(node.getParent()).equals("stmt")){
                indentLevel++;
            }
            newLine();
        }else if(ruleNameTemp.equals("block")){
            if(getNodeType(node.getParent()).equals("blockItem")){
                newLine();
            }
        }
    }

    //退出节点时的行为
    private void exitNode(RuleNode node) {
        colorStack.pop();
        if(getNodeType(node).equals("stmt")){
            if(getNodeType(node.getParent()).equals("stmt")){
                indentLevel--;
            }
        }
    }

    //换行同时缩进
    private void newLine(){
        int len = outputCode.length();
        String lastNode = (len >= 22 ? outputCode.substring(len-22,len) : "");
        if(lastNode.equals("\u001B[96melse\u001B[0m\u001B[0m \u001B[0m")){
            outputCode.delete(len-9, len);
        }
        outputCode.append("\u001B[0m\n\u001B[0m");
        outputCode.append("\u001B[0m    \u001B[0m".repeat(Math.max(0, indentLevel)));
    }

    //方法重写，访问终结符
    @Override
    public Void visitTerminal(TerminalNode node) {
        String nodeType = getNodeType(node);
        if(nodeType == null){
            return null;
        }
        String highlightedNode = highlight(node);
        if(getNodeType(node.getParent()).equals("block") && nodeType.equals("R_BRACE")){
            indentLevel--;
            newLine();
        }else if(nodeType.equals("ELSE")){
            newLine();
        }
        if(highlightedNode != null){
            outputCode.append(hasFrontSpace(node) ? "\u001B[0m \u001B[0m" : "");
            outputCode.append(highlightedNode);
            outputCode.append(hasEndSpace(node) ? "\u001B[0m \u001B[0m" : "");
        }
        if(getNodeType(node.getParent()).equals("block") && getNodeType(node).equals("L_BRACE")){
            indentLevel++;
        }
        return null;
    }

    //染色。对终结符，先看能匹配的规则，再看栈顶的规则；对非终结符，看有没有能匹配的规则，有则匹配、无则继承栈顶的规则
    private String highlight(ParseTree node){
        if(node instanceof TerminalNode){
            TerminalNode Tnode = (TerminalNode) node;
            if(Tnode.getSymbol().getType() == -1){
                return null;
            }
            String nodeText = Tnode.getText();
            colorStackItem fatherColor = colorStack.peek();
            int otherCode = fatherColor.otherCode, colorCodeFront = fatherColor.colorCodeFront, colorCodeEnd = fatherColor.colorCodeEnd;
            if(isKeyWord(Tnode)){
                colorCodeFront = colorMap.get("BrightCyan");
            }else if(isOpCode(Tnode)){
                colorCodeFront = colorMap.get("BrightRed");
            }else if(isIntConst(Tnode)){
                colorCodeFront = colorMap.get("Magenta");
            }else if(isLeftBracket(Tnode)){
                colorCodeFront = colorMap.get(colorModMap[rainbowLevel]);
                rainbowLevel = (rainbowLevel + 1) % colorModMap.length;
            }else if(isRightBracket(Tnode)){
                rainbowLevel = (rainbowLevel + colorModMap.length - 1) % colorModMap.length;
                colorCodeFront = colorMap.get(colorModMap[rainbowLevel]);
            }else if(isIdent(Tnode)){
                String ruleNameTemp = getNodeType(node.getParent());
                if(ruleNameTemp.equals("funcDef") || ruleNameTemp.equals("exp")){
                    colorCodeFront = colorMap.get("BrightYellow");
                }
            }
            String nodeColorANSI = generateNodeColorANSI(otherCode, colorCodeFront, colorCodeEnd);
            nodeText = "\u001B[" + nodeColorANSI +"m" + nodeText + "\u001B[0m";
            return nodeText;
        }else{
            String ruleNameTemp = getNodeType(node);
            if(ruleNameTemp.equals("program")){
                colorStack.add(new colorStackItem(node, "program", -1, -1, -1));
            }else if(ruleNameTemp.equals("stmt")){
                colorStack.add(new colorStackItem(node, "stmt", colorStack.peek().otherCode, colorMap.get("White"), colorStack.peek().colorCodeEnd));
            }else if(ruleNameTemp.equals("decl")){
                colorStack.add(new colorStackItem(node, "decl", colorMap.get("Underlined"), colorMap.get("BrightMagenta"), colorStack.peek().colorCodeEnd));
            }else{
                colorStack.add(new colorStackItem(node, ruleNameTemp, colorStack.peek().otherCode, colorStack.peek().colorCodeFront, colorStack.peek().colorCodeEnd));
            }
            return null;
        }
    }

    //辅助方法，获取当前节点应该染色的ANSI号
    private String generateNodeColorANSI(int otherCode, int colorCodeFront, int colorCodeEnd){
        String nodeColorANSI = "";
        if(otherCode == -1 && colorCodeFront == -1 && colorCodeEnd == -1){
            nodeColorANSI = "0";
        }else{
            nodeColorANSI = nodeColorANSI + (otherCode==-1 ? "" : otherCode);
            if(nodeColorANSI.isEmpty()){
                nodeColorANSI = nodeColorANSI + (colorCodeFront==-1 ? "" : colorCodeFront);
            }else{
                nodeColorANSI = nodeColorANSI + (colorCodeFront==-1 ? "" : ";"+colorCodeFront);
            }
            if(nodeColorANSI.isEmpty()){
                nodeColorANSI = nodeColorANSI + colorCodeEnd;
            }else{
                nodeColorANSI = nodeColorANSI + (colorCodeEnd==-1 ? "" : ";"+colorCodeEnd);
            }
        }
        return nodeColorANSI;
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

    //几个判断类型的辅助函数
    private boolean isKeyWord(TerminalNode node){
        int typeIndex = node.getSymbol().getType();
        return (typeIndex >= lexerTypeList.indexOf("CONST") && typeIndex <= lexerTypeList.indexOf("RETURN"));
    }

    private boolean isOpCode(TerminalNode node){
        int typeIndex = node.getSymbol().getType();
        return ((typeIndex >= lexerTypeList.indexOf("PLUS") && typeIndex <= lexerTypeList.indexOf("OR"))
                || lexerTypeList.get(typeIndex).equals("COMMA") || lexerTypeList.get(typeIndex).equals("SEMICOLON"));
    }

    private boolean isIntConst(TerminalNode node){
        return getNodeType(node).equals("INTEGER_CONST");
    }

    private boolean isLeftBracket(TerminalNode node){
        String s = getNodeType(node);
        return (s.equals("L_PAREN") || s.equals("L_BRACE") || s.equals("L_BRACKT"));
    }

    private boolean isRightBracket(TerminalNode node){
        String s = getNodeType(node);
        return (s.equals("R_PAREN") || s.equals("R_BRACE") || s.equals("R_BRACKT"));
    }

    private boolean isIdent(TerminalNode node){
        return getNodeType(node).equals("IDENT");
    }

    //判断终结符前面有没有空格
    private boolean hasFrontSpace(TerminalNode node){
        String s = getNodeType(node);
        if(isOpCode(node) && !s.equals("COMMA") && !s.equals("SEMICOLON") && !getNodeType(node.getParent()).equals("unaryOp")){
            return true;
        }else if(s.equals("L_BRACE") && getNodeType(node.getParent()).equals("block")){
            if(!getNodeType(node.getParent().getParent()).equals("blockItem")){
                int len = outputCode.length();
                String lastNode = (len >= 22 ? outputCode.substring(len-22,len) : "");
                if(!lastNode.equals("\u001B[96melse\u001B[0m\u001B[0m \u001B[0m")){//这个东西长度是22，因为\u001b是一个字符
                    return true;
                }
            }
        }
        return false;
    }

    //判断终结符后面有没有空格
    private boolean hasEndSpace(TerminalNode node){
        String s = getNodeType(node);
        if(isKeyWord(node)){
            RuleNode parentTemp = (RuleNode)node.getParent();
            if(getNodeType(parentTemp).equals("stmt")){
                int n = parentTemp.getChildCount();
                if(n == 2 && (s.equals("BREAK") || s.equals("CONTINUE") || s.equals("RETURN"))){
                    return false;
                }
            }
            return true;
        }else if(isOpCode(node) && !s.equals("SEMICOLON") && !getNodeType(node.getParent()).equals("unaryOp")){
            return true;
        }
        return false;
    }
}