import org.bytedeco.llvm.LLVM.*;
import org.bytedeco.llvm.global.LLVM;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.bytedeco.llvm.global.LLVM.*;
import static org.bytedeco.llvm.global.LLVM.LLVMGetValueName;

public class MyRISCVGenerator {
    public final LLVMModuleRef module;
    public final String destPath;
    private AsmBuilder asmBuilder;
    private int stackSize;
    private int stackArraySize;
    private int totalLineNum;

    private static final List<String> allRegs = Arrays.asList(
            "a0","a1","a2","a3" ,"a4","a5","a6","a7",
            "s0","s1","s2","s3","s4","s5","s6","s7","s8","s9","s10","s11",
            "t2","t3","t4","t5","t6"
    );

    private String[] allocateArray = new String[allRegs.size()];
    private String[] stackArray = new String[1000];

    private HashMap<String, int[]> liveSpace;
    private HashMap<String, LLVMValueRef> llvmSymbolTable;

    MyRISCVGenerator(LLVMModuleRef module, String destPath){
        this.module = module;
        this.destPath = destPath;
        this.asmBuilder = new AsmBuilder();
        this.liveSpace = new HashMap<>();
    }

    public void generateRiscVCode(){
        //遍历所有全局变量
        asmBuilder.op(".data");
        for (LLVMValueRef value = LLVMGetFirstGlobal(module); value != null; value = LLVMGetNextGlobal(value)) {
            asmBuilder.label(LLVMGetValueName(value).getString());
            asmBuilder.op0(".word", String.valueOf(LLVMConstIntGetSExtValue(LLVMGetInitializer(value))));
        }
        asmBuilder.newline();
        //遍历所有函数
        asmBuilder.op(".text");
        for (LLVMValueRef func = LLVMGetFirstFunction(module); func != null; func = LLVMGetNextFunction(func)) {
            asmBuilder.op0(".globl", LLVMGetValueName(func).getString());
            asmBuilder.label(LLVMGetValueName(func).getString());
            //第一遍扫描，检查活跃区间。
            int line = 0;
            for (LLVMBasicBlockRef bb = LLVMGetFirstBasicBlock(func); bb != null; bb = LLVMGetNextBasicBlock(bb)) {
                for (LLVMValueRef inst = LLVMGetFirstInstruction(bb); inst != null; inst = LLVMGetNextInstruction(inst)) {
                    //定义新变量
                    String varName = LLVMGetValueName(inst).getString();
                    if(!varName.isEmpty()){
                        int[] startToEnd = {line, line};
                        liveSpace.put(varName, startToEnd);
                    }
                    //使用变量
                    int operandNum = LLVMGetNumOperands(inst);
                    LLVMValueRef op1 = (operandNum >= 1 ? LLVMGetOperand(inst, 0) : null);
                    if(op1 != null && LLVMIsAGlobalValue(op1) == null){
                        varName = LLVMGetValueName(op1).getString();
                        if(!varName.isEmpty()){
                            liveSpace.get(varName)[1] = line;
                        }
                    }
                    LLVMValueRef op2 = (operandNum >= 2 ? LLVMGetOperand(inst, 1) : null);
                    if(op2 != null && LLVMIsAGlobalValue(op2) == null){
                        varName = LLVMGetValueName(op2).getString();
                        if(!varName.isEmpty()){
                            liveSpace.get(varName)[1] = line;
                        }
                    }
                    line++;
                }
            }
            totalLineNum = line;
            //第二遍扫描，确定最大活跃变量个数，从而确定预留栈空间大小
            stackSize = Math.max((getMaxLiveNum() - allRegs.size() + 3) / 4 * 16, 0);
            stackArraySize = stackSize / 4;
            //第三遍扫描，遍历当前函数的所有基本块
            line = 0;
            asmBuilder.op2("addi", "sp", "sp", String.valueOf(-stackSize));
            for (LLVMBasicBlockRef bb = LLVMGetFirstBasicBlock(func); bb != null; bb = LLVMGetNextBasicBlock(bb)) {
                asmBuilder.label(LLVMGetBasicBlockName(bb).getString());
                //遍历当前基本块的所有指令
                for (LLVMValueRef inst = LLVMGetFirstInstruction(bb); inst != null; inst = LLVMGetNextInstruction(inst)) {
                    generateInstruction(inst);
                    asmBuilder.newline();
                    line++;
                    cancelRegs(line);
                }
            }
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(destPath))) {
            writer.write(asmBuilder.getStringBuffer().toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getMaxLiveNum() {
        int maxLiveNum = 0;
        for(int i = 0; i < totalLineNum; i++){
            int maxLiveNumTemp = 0;
            for(Map.Entry<String, int[]> entry : liveSpace.entrySet()){
                int[] space = entry.getValue();
                if(space[0] <= i && space[1] >= i){
                    maxLiveNumTemp++;
                }
            }
            maxLiveNum = Math.max(maxLiveNum, maxLiveNumTemp);
        }
        return maxLiveNum;
    }

    private void generateInstruction(LLVMValueRef inst){
        //对于一条 LLVM 指令，获取它的操作码、操作数数量和具体的操作数：
        int opcode = LLVMGetInstructionOpcode(inst);
        int operandNum = LLVMGetNumOperands(inst);
        LLVMValueRef op1 = (operandNum >= 1 ? LLVMGetOperand(inst, 0) : null);
        LLVMValueRef op2 = (operandNum >= 2 ? LLVMGetOperand(inst, 1) : null);
        if(opcode == LLVMRet){
            generateLLVMRet(inst, op1, op2);
        }else if(opcode == LLVMAlloca){
            generateLLVMAllocate(inst);
        }else if(opcode == LLVMLoad){
            generateLLVMLoad(inst, op1);
        }else if(opcode == LLVMStore){
            generateLLVMStore(inst, op1, op2);
        }else if(opcode == LLVMAdd){
            generateLLVMOp2(inst, "add", op1, op2);
        }else if(opcode == LLVMSub){
            generateLLVMOp2(inst, "sub", op1, op2);
        }else if(opcode == LLVMMul){
            generateLLVMOp2(inst, "mul", op1, op2);
        }else if(opcode == LLVMSDiv){
            generateLLVMOp2(inst, "div", op1, op2);
        }else if(opcode == LLVMSRem){
            generateLLVMOp2(inst, "rem", op1, op2);
        }
    }

    private void generateLLVMRet(LLVMValueRef inst, LLVMValueRef op1, LLVMValueRef op2){
        if(LLVMIsAConstantInt(op1) != null){
            asmBuilder.op1("li", "a0", String.valueOf(LLVMConstIntGetSExtValue(op1)));
        }else{
            asmBuilder.op1("mv", "a0", getReg(LLVMGetValueName(op1).getString()));
        }
        asmBuilder.op2("addi", "sp", "sp", String.valueOf(stackSize));
        asmBuilder.op1("li", "a7", "93");
        asmBuilder.op("ecall");
    }

    private void generateLLVMAllocate(LLVMValueRef inst){
        allocateReg(LLVMGetValueName(inst).getString());
    }

    private void generateLLVMLoad(LLVMValueRef inst, LLVMValueRef op1){
        String reg = allocateReg(LLVMGetValueName(inst).getString());
        if(LLVMIsAGlobalValue(op1) != null){
            asmBuilder.op1("la", "t0", LLVMGetValueName(op1).getString());
            asmBuilder.op1("lw", reg, "0(t0)");
        }else{
            String regL = getReg(LLVMGetValueName(op1).getString());
            asmBuilder.op1("mv", reg, regL);
        }
    }

    private void generateLLVMStore(LLVMValueRef inst, LLVMValueRef op1, LLVMValueRef op2){
        if(LLVMIsAConstantInt(op1) != null){
            asmBuilder.op1("li", "t0", String.valueOf(LLVMConstIntGetSExtValue(op1)));
        }
        String reg = (LLVMIsAConstantInt(op1) != null ? "t0" : getReg(LLVMGetValueName(op1).getString()));
        if(LLVMIsAGlobalValue(op2) != null){
            asmBuilder.op1("la", "t1", LLVMGetValueName(op2).getString());
            asmBuilder.op1("sw", reg, "0(t1)");
        }else{
            asmBuilder.op1("mv", getReg(LLVMGetValueName(op2).getString()), reg);
        }
    }

    private void generateLLVMOp2(LLVMValueRef inst, String opText, LLVMValueRef op1, LLVMValueRef op2){
        String regL, regR, reg;
        if(LLVMIsAConstantInt(op1) != null){
            asmBuilder.op1("li", "t0", String.valueOf(LLVMConstIntGetSExtValue(op1)));
        }
        regL = (LLVMIsAConstantInt(op1) != null ? "t0" : getReg(LLVMGetValueName(op1).getString()));
        if(LLVMIsAConstantInt(op2) != null){
            asmBuilder.op1("li", "t1", String.valueOf(LLVMConstIntGetSExtValue(op2)));
        }
        regR = (LLVMIsAConstantInt(op2) != null ? "t1" : getReg(LLVMGetValueName(op2).getString()));
        reg = allocateReg(LLVMGetValueName(inst).getString());
        asmBuilder.op2(opText, reg, regL, regR);
    }

    private String allocateReg(String varName){
        int farEnd = -1, index = 0;
        for(int i = 0; i < allRegs.size(); i++){
            if(allocateArray[i] == null){
                allocateArray[i] = varName;
                return allRegs.get(i);
            }else if(liveSpace.get(allocateArray[i])[1] > farEnd){
                farEnd = liveSpace.get(allocateArray[i])[1];
                index = i;
            }
        }
        int bias = 0;
        for(int i = stackArraySize-1; i >= 0; i--){
            if(stackArray[i] == null){
                stackArray[i] = allocateArray[index];
                bias = i * 4;
                break;
            }
        }
        asmBuilder.op1("sw", allRegs.get(index), String.format("%d(sp)",bias));
        allocateArray[index] = varName;
        return allRegs.get(index);
    }

    private String getReg(String varName){
        int farEnd = -1, index = 0, emptyIndex = -1;
        for(int i = 0; i < allRegs.size(); i++){
            if(allocateArray[i] != null && allocateArray[i].equals(varName)){
                return allRegs.get(i);
            }else if(allocateArray[i] != null && liveSpace.get(allocateArray[i])[1] > farEnd){
                farEnd = liveSpace.get(allocateArray[i])[1];
                index = i;
            }else if(allocateArray[i] == null){
                emptyIndex = i;
            }
        }
        if(emptyIndex >= 0){
            for(int i = stackArraySize-1; i >= 0; i--){
                if(stackArray[i] != null && stackArray[i].equals(varName)){
                    stackArray[i] = null;
                    allocateArray[emptyIndex] = varName;
                    asmBuilder.op1("lw", allRegs.get(emptyIndex), String.format("%d(sp)",i*4));
                    return allRegs.get(emptyIndex);
                }
            }
        }
        int bias = 0;
        for(int i = stackArraySize-1; i >= 0; i--){
            if(stackArray[i] == null){
                stackArray[i] = allocateArray[index];
                bias = i * 4;
                break;
            }
        }
        asmBuilder.op1("sw", allRegs.get(index), String.format("%d(sp)",bias));
        for(int i = stackArraySize-1; i >= 0; i--){
            if(stackArray[i] != null && stackArray[i].equals(varName)){
                stackArray[i] = null;
                bias = i * 4;
                break;
            }
        }
        asmBuilder.op1("lw", allRegs.get(index), String.format("%d(sp)",bias));
        allocateArray[index] = varName;
        return allRegs.get(index);
    }

    private void cancelRegs(int line){
        for (Map.Entry<String, int[]> entry : liveSpace.entrySet()){
            if(entry.getValue()[1] < line){
                String varName = entry.getKey();
                for(int i = 0; i < allRegs.size(); i++){
                    if(allocateArray[i] != null && allocateArray[i].equals(varName)){
                        allocateArray[i] = null;
                        break;
                    }
                }
                for(int i = 0; i < stackArraySize; i++){
                    if(stackArray[i] != null && stackArray[i].equals(varName)){
                        stackArray[i] = null;
                        break;
                    }
                }
            }
        }
    }
}
