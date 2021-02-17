## LocalVariablesSorter performance demo

ASM's LocalVariableSorter currently expects expanded frames.

This Maven project contains a JMH benchmark demonstrating the performance difference win
of replacing LocalVariableSorter with a custom class LocalVariablesPrepender.

LocalVariablesPrepender only supports inserting local variables at the start of methods. In contrast to 
LocalVariablesSorter, it does not require expanded frames.

### Running

    mvn compile exec:exec -Dexec.executable="java" -Dexec.args="-cp %classpath com.github.eirbjo.LocalVariablesDemo"

### Result

Expect 13 minutes for the JMH benchmark to complete.

You should observe a result comparable to this:

    Benchmark                                    Mode  Cnt     Score   Error  Units
    LocalVariablesDemo.localVariablesPrepender  thrpt       3871.131          ops/s
    LocalVariablesDemo.localVariablesSorter     thrpt       2763.150          ops/s

