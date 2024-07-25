public class AsmBuilder {
    AsmBuilder(){
        this.buffer = new StringBuffer();
    }

    private StringBuffer buffer;

    public StringBuffer getStringBuffer(){
        return this.buffer;
    }

    public void label(String labelText){
        buffer.append(String.format("%s:\n", labelText));
    }
    public void op2(String op, String dest, String lhs, String rhs) {
        buffer.append(String.format("  %s %s, %s, %s\n", op, dest, lhs, rhs));
    }
    public void op1(String op, String dest, String lhs) {
        buffer.append(String.format("  %s %s, %s\n", op, dest, lhs));
    }
    public void op0(String op, String dest) {
        buffer.append(String.format("  %s %s\n", op, dest));
    }
    public void op(String op) {
        buffer.append(String.format("  %s\n", op));
    }
    public void newline() {
        buffer.append("\n");
    }
}
