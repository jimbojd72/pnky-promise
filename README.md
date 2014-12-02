## PnkyPromise

The [PnkyPromise](src/main/java/com/jive/foss/pnky/PnkyPromise.java) class is a 
custom implementation of a promise or futures framework. It provides the ability to work with short 
asynchronous tasks, chaining actions one after the other with custom handling for actions that 
succeed or fail.

In addition to the methods provided on the interface for listening to the results of a task, the 
[Pnky](src/main/java/com/jive/foss/pnky/Pnky.java) implementation provides utilities 
to initiate a process that will run on an executor and return a future that you can use to start 
listening for the result when it completes.

The *PnkyPromise* framework grew out of a need for capabilities that were found lacking in Guava's 
*ListenableFuture* and Java 8's *CompletableFuture* classes.

Refer to the JavaDoc on 
[PnkyPromise](src/main/java/com/jive/foss/pnky/PnkyPromise.java) and the utility 
methods on [Pnky](src/main/java/com/jive/foss/pnky/Pnky.java) for more information on 
capabilities. Also refer to the example usages in 
[PnkyExamples](src/test/java/com/jive/foss/pnky/PnkyExamples.java).