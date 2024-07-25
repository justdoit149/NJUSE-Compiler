include Makefile.git

LLVM_JAR = $(shell echo `find /usr/local/lib -name "llvm-*.jar"` | sed  "s/\s\+/:/g")
JAVACPP_JAR = $(shell echo `find /usr/local/lib -name "javacpp-*.jar"` | sed  "s/\s\+/:/g")
ANTLR_PATH = $(shell find /usr/local/lib -name "antlr-*-complete.jar")

export CLASSPATH=$(ANTLR_PATH):$(LLVM_JAR):$(JAVACPP_JAR)

FILEPATH = /mnt/hgfs/CompilationPrincipleLAB/Lab/main.c
IRPATH = /mnt/hgfs/CompilationPrincipleLAB/Lab/main.asm

DOMAINNAME = oj.compilers.cpl.icu
ANTLR = java -jar $(ANTLR_PATH) -listener -visitor -long-messages
JAVAC = javac -g
JAVA = java


PFILE = $(shell find . -name "SysYParser.g4")
LFILE = $(shell find . -name "SysYLexer.g4")
JAVAFILE = $(shell find . -name "*.java")

compile: antlr
	$(call git_commit,"make")
	mkdir -p classes
	$(JAVAC) -classpath $(CLASSPATH) $(JAVAFILE) -d classes

run: compile
	java -classpath ./classes:$(CLASSPATH) Main $(FILEPATH) $(IRPATH)

runll:
	lli $(IRPATH); echo $$?

clang:
	clang -S -emit-llvm $(FILEPATH) -o $(IRPATH) -O0

stdrun: clang runll

myrun: run runll

antlr: $(LFILE) $(PFILE)
	$(ANTLR) $(PFILE) $(LFILE)

test: compile
	$(call git_commit, "test")
	if [ -e nohup.out ]; then rm nohup.out; fi
	nohup java -classpath ./classes:$(CLASSPATH) Main ./tests/test1.sysy ./tests/test1.ll &

clean:
	rm -f src/*.tokens
	rm -f src/*.interp
	rm -f src/SysYLexer.java src/SysYParser.java src/SysYParserBaseListener.java src/SysYParserBaseVisitor.java src/SysYParserListener.java src/SysYParserVisitor.java
	rm -rf classes
	rm -rf out

submit: clean
	git gc
	bash submit.sh

.PHONY: compile antlr test run clean submit