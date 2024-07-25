import org.antlr.v4.runtime.*;

//与MyLexerErrorListener基本相同。
public class MyParserErrorListener extends BaseErrorListener {
    private static final MyParserErrorListener listener = new MyParserErrorListener();
    private MyParserErrorListener(){}
    public static MyParserErrorListener getMyParserErrorListener() {
        return listener;
    }

    public static boolean hasParserError = false;

    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        hasParserError = true;
        String parserErrorInformation = String.format("Error type B at Line %d: %s",line,msg);
        System.out.println(parserErrorInformation);
    }
}
