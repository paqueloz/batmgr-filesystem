/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 paqueloz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.batmgr.filesystem;

import java.awt.Toolkit;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Creates indexes in a directory tree
 */
@Command
@Slf4j
public class Indexer implements Runnable {

    @Option(names = { "-r", "--recurse"}, description = "Recurse")
    boolean recurse = false;

    @Option(names = { "-c", "--cleanup"}, description = "Cleanup, remove .index")
    boolean cleanup = false;

    @Parameters(paramLabel = "<path>", description = "Path to index")
    Path path;

    @Override
    public void run() {
        try {
            if (!cleanup) {
                DirChecker checker = new DirChecker();
                if (recurse) {
                    checker.indexTree(path);
                } else {
                    checker.indexFolder(path);
                }
            } else {
                DirCleaner cleaner = new DirCleaner();
                if (recurse) {
                    cleaner.cleanTree(path);
                } else {
                    cleaner.cleanFolder(path);
                }
            }
        } catch (Throwable t) {
            log.error("program aborted", t);
        } finally {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    public static void main(String[] args)
    {
        System.exit(new CommandLine(new Indexer()).execute(args));
    }

}
