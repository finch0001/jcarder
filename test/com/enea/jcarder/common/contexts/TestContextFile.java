/*
 * JCarder -- cards Java programs to keep threads disentangled
 *
 * Copyright (C) 2006-2007 Enea AB
 * Copyright (C) 2007 Ulrik Svensson
 * Copyright (C) 2007 Joel Rosdahl
 *
 * This program is made available under the GNU GPL version 2, with a special
 * exception for linking with JUnit. See the accompanying file LICENSE.txt for
 * details.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.enea.jcarder.common.contexts;

import java.io.File;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

import com.enea.jcarder.common.Lock;
import com.enea.jcarder.common.LockingContext;
import com.enea.jcarder.common.contexts.ContextFileReader;
import com.enea.jcarder.common.contexts.ContextFileWriter;
import com.enea.jcarder.util.logging.Logger;


public final class TestContextFile {

    @Test
    public void writeReadTest() throws IOException {
        File file = File.createTempFile(TestContextFile.class.getName(),
                                        null);
        ContextFileWriter writer =
            new ContextFileWriter(new Logger(null), file);
        Lock lock = new Lock("myClassName", 5);
        LockingContext context = new LockingContext("myThreadName",
                                                    "myLockReference",
                                                    "myMethod");
        int lockId = writer.writeLock(lock);
        int contextId = writer.writeContext(context);
        writer.close();
        ContextFileReader reader = new ContextFileReader(new Logger(null), file);
        Assert.assertEquals(lock, reader.readLock(lockId));
        Assert.assertEquals(context, reader.readContext(contextId));
        file.delete();
    }

    @Test
    public void interruptedTest() throws IOException {
        File file = File.createTempFile(TestContextFile.class.getName(),
                                        null);
        ContextFileWriter writer =
            new ContextFileWriter(new Logger(null), file);
        Lock lock = new Lock("myClassName", 5);
        LockingContext context = new LockingContext("myThreadName",
                                                    "myLockReference",
                                                    "myMethod");

        // Start another thread which will interrupt us a lot
        final Thread testThread = Thread.currentThread();
        Thread interrupter = new Thread() {
                public void run() {
                    while (!Thread.interrupted()) {
                        testThread.interrupt();
                    }
                }
            };
        interrupter.start();

        // Write a bunch of things to the context file while getting
        // interrupted
        int numWrites = 500;
        int lockIds[] = new int[numWrites];
        int contextIds[] = new int[numWrites];

        for (int i = 0; i < numWrites; i++) {
            lockIds[i] = writer.writeLock(lock);
            contextIds[i] = writer.writeContext(context);
        }

        // Stop the thread that's interrupting us and wait for it to stop
        interrupter.interrupt();

        while (interrupter.isAlive()) {
            try {
                interrupter.join();
            } catch (InterruptedException ie) {
                // We expect this
            }
        }
        testThread.interrupted(); // clear our interrupt status!

        writer.close();


        ContextFileReader reader = new ContextFileReader(new Logger(null), file);
        for (int i = 0; i < numWrites; i++) {
            Assert.assertEquals(lock, reader.readLock(lockIds[i]));
            Assert.assertEquals(context, reader.readContext(contextIds[i]));
        }
        file.delete();
    }
}
