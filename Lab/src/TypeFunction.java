import java.util.*;

public class TypeFunction extends Type{
    private final Type retType;
    private final ArrayList<Type> paramsType;
    public TypeFunction(Type retType, ArrayList<Type> paramsType){
        this.retType = retType;
        this.paramsType = paramsType;
    }
    public Type getRetType(){
        return retType;
    }
    public ArrayList<Type> getParamsType(){
        return paramsType;
    }
}
