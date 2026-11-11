package eu.gabier.daemon;

import java.time.Duration;

/** What the daemon waits on, while a client watches, to know that the files of the project changed on disk. */
interface DiskChanges extends AutoCloseable {

    /** What a wait ended with. */
    enum Signal {
        /** Nothing changed in the time given. */
        QUIET,
        /** A file that jdtls watches changed. */
        CHANGED,
        /**
         * The system cannot follow the disk any more (events lost, or no watch to register a folder with): the files are
         * looked at at a fixed pace from now on, and the clients are told. A file may have changed, so the project is read
         * again.
         */
        LOST
    }

    /** Waits for the disk to change, for the time given at most. */
    Signal await(Duration timeout) throws InterruptedException;

    @Override
    void close();
}
