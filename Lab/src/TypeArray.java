public class TypeArray extends Type {
    private Type elementType;

    public TypeArray(Type elementType) {
        this.elementType = elementType;
    }

    public Type getElementType() {
        return elementType;
    }
}

