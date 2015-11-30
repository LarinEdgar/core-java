/*
 * Copyright 2015, TeamDev Ltd. All rights reserved.
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.spine3.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

import static com.google.common.base.Throwables.propagate;

/**
 * Utility class working with I/O: streams, files etc.
 *
 * @author Alexander Litus
 */
@SuppressWarnings("UtilityClass")
public class IoUtil {

    private IoUtil() {}

    /**
     * Closes passed closeables one by one silently.
     * <p>
     * Logs each {@link java.io.IOException} if it occurs.
     */
    @SuppressWarnings("ConstantConditions"/*check for null is ok*/)
    public static void closeSilently(Closeable... closeables) {
        try {
            for (Closeable c : closeables) {
                if (c != null) {
                    c.close();
                }
            }
        } catch (IOException e) {
            if (log().isErrorEnabled()) {
                log().error("Exception while closing stream", e);
            }
        }
    }

    /**
     * Flushes passed streams one by one.
     * <p>
     * Logs each {@link IOException} if it occurs.
     */
    public static void flushSilently(@Nullable Flushable... flushables) {
        try {
            flush(flushables);
        } catch (IOException e) {
            if (log().isErrorEnabled()) {
                log().error("Exception while flushing stream", e);
            }
        }
    }

    /**
     * Flushes streams in turn.
     *
     * @throws java.lang.RuntimeException if {@link IOException} occurs
     */
    public static void tryToFlush(@Nullable Flushable... flushables) {
        try {
            flush(flushables);
        } catch (IOException e) {
            propagate(e);
        }
    }

    @SuppressWarnings("ConstantConditions"/*check for null is ok*/)
    private static void flush(@Nullable Flushable[] flushables) throws IOException {
        if (flushables == null) {
            return;
        }
        for (Flushable f : flushables) {
            if (f != null) {
                f.flush();
            }
        }
    }

    /**
     * Flushes and closes output streams in turn silently. Logs IOException if occurs.
     */
    public static void flushAndCloseSilently(@Nullable OutputStream... streams) {
        if (streams == null) {
            return;
        }
        flushSilently(streams);
        closeSilently(streams);
    }

    /**
     * Checks if the file exists.
     *
     * @param file file to check
     * @param fileDescription the description for error message
     * @throws IllegalStateException if there is no such file
     */
    public static void checkFileExists(File file, String fileDescription) {
        if (!file.exists()) {
            final FileNotFoundException fileNotFound = new FileNotFoundException(fileDescription + ": " + file.getAbsolutePath());
            throw propagate(fileNotFound);
        }
    }

    /**
     * Tries to open {@code FileInputStream} from file.
     *
     * @throws RuntimeException if there is no such file
     */
    public static FileInputStream open(File file) {

        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            propagate(e);
        }
        return fileInputStream;
    }

    /**
     * Creates a file with the given path if it does not exist or returns the existed one.
     * @return the created or existed file
     * @throws java.io.IOException - If an I/O error occurred
     */
    @SuppressWarnings("ResultOfMethodCallIgnored") // the result is redundant in this case
    public static File createIfDoesNotExist(String path) throws IOException {

        final File file = new File(path);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }
        return file;
    }

    /**
     * Deletes a non-directory file using {@link Files#deleteIfExists(Path)}.
     *
     * <p>If it is the path to the directory, deletes all the files in it recursively and then deletes this directory.
     *
     * @param path the path to the file to delete.
     * @throws RuntimeException if an I/O error occurs.
     */
    public static void deleteIfExists(Path path) {
        try {
            if (path.toFile().isDirectory()) {
                Files.walkFileTree(path, newFilesEliminator());
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            propagate(e);
        }
    }

    private static FileVisitor<Path> newFilesEliminator() {
        return new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                super.visitFile(file, attributes);
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException exception) throws IOException {
                super.postVisitDirectory(path, exception);
                Files.delete(path);
                return FileVisitResult.CONTINUE;
            }
        };
    }

    private static Logger log() {
        return LogSingleton.INSTANCE.value;
    }

    private enum LogSingleton {
        INSTANCE;
        @SuppressWarnings("NonSerializableFieldInSerializableClass")
        private final Logger value = LoggerFactory.getLogger(IoUtil.class);
    }
}