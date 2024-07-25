import org.antlr.v4.runtime.*;

public class MyLexerErrorListener extends BaseErrorListener{
    //单例模式
    private static final MyLexerErrorListener listener = new MyLexerErrorListener();
    private MyLexerErrorListener(){}
    public static MyLexerErrorListener getMyLexerErrorListener(){
        return listener;
    }

    //是否有词法错误
    public static boolean hasLexerError = false;

    //方法重写，阅读并理解antlr源码后，很容易写出。
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        hasLexerError = true;
        String lexerErrorInformation = String.format("Error type A at Line %d: %s",line,msg);
        System.err.println(lexerErrorInformation);
    }
}
