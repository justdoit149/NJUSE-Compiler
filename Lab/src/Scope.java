import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class Scope {
    private LinkedHashMap<String, Type> symbolTable;

    private Scope fatherScope;

    public Scope(Scope fatherScope){
        this.fatherScope = fatherScope;
        symbolTable = new LinkedHashMap<>();
    }

    public void addSymbol(String symbolName, Type symbolType){
        symbolTable.put(symbolName, symbolType);
    }

    public Type getSymbol(String symbolName){
        if(symbolTable.get(symbolName) != null){
            return symbolTable.get(symbolName);
        }else if(fatherScope != null){
            return fatherScope.getSymbol(symbolName);
        }else{
            return null;
        }
    }

    public Type getSymbolInThisScope(String symbolName){
        return symbolTable.get(symbolName);
    }

    public ArrayList<Type> getAllSymbolInThisScope(){
        ArrayList<Type> ans = new ArrayList<>();
        for(Map.Entry<String, Type> entry: symbolTable.entrySet()){
            ans.add(entry.getValue());
        }
        return ans;
    }

    public Scope getFatherScope(){
        return fatherScope;
    }

    public Scope getGlobalScope(){
        return fatherScope == null ? this : this.getFatherScope().getGlobalScope();
    }

}
