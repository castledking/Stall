package codes.castled.stall.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Writes files off the server thread, one at a time, in a way a crash can't leave half-written.
 *
 * <p>Each write goes to a temporary file beside the real one, is forced to disk, then moved over the real
 * file in one step. A crash at any point leaves either the old file or the new one, never a mix.
 *
 * <p>Writes to the same file coalesce: if a file is written again before the first write ran, only the
 * newest content is written. Writes to different files keep their order.
 */
final class FileStore implements AutoCloseable
{
    /** Pending content per file; an empty optional means delete it. */
    private final Map<Path, Optional<String>> pending = new ConcurrentHashMap<>();
    private final ExecutorService io;
    private final StoreLog log;

    FileStore(StoreLog log)
    {
        this.log = Objects.requireNonNull(log, "log");
        this.io = Executors.newSingleThreadExecutor(task ->
        {
            Thread thread = new Thread(task, "Stall storage");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Writes {@code content} to {@code file} soon, replacing it completely. */
    void write(Path file, String content)
    {
        schedule(file, Optional.of(content));
    }

    /** Deletes {@code file} soon, after any write to it that is still waiting. */
    void delete(Path file)
    {
        schedule(file, Optional.empty());
    }

    /** Ledger writes must keep their order relative to orphan archival, rather than coalescing. */
    void writeOrdered(Path file, String content)
    {
        io.execute(() ->
        {
            try { writeAtomically(file, content); }
            catch (IOException | RuntimeException failure) { log.warn("Could not save " + file, failure); }
        });
    }

    /** Save the refund first. A crash before deletion is recovered using the ledger's shop IDs. */
    void archive(Path ledger, String refunds, Path archive, String shop, Path original)
    {
        io.execute(() ->
        {
            try
            {
                writeAtomically(ledger, refunds);
                writeAtomically(archive, shop);
                Files.deleteIfExists(original);
            }
            catch (IOException | RuntimeException failure)
            {
                log.warn("Could not finish archiving " + original + "; the next startup will recover it.", failure);
            }
        });
    }

    private void schedule(Path file, Optional<String> content)
    {
        if (pending.put(file, content) == null)
        {
            io.execute(() -> drain(file));
        }
    }

    private void drain(Path file)
    {
        Optional<String> content = pending.remove(file);
        if (content == null)
        {
            return;
        }
        try
        {
            if (content.isPresent())
            {
                writeAtomically(file, content.get());
            }
            else
            {
                Files.deleteIfExists(file);
            }
        }
        catch (IOException | RuntimeException failure)
        {
            log.warn("Could not save " + file + ". The change is only in memory until the next save.", failure);
        }
    }

    /** Blocks until everything written or deleted so far is on disk. */
    void flush()
    {
        try
        {
            io.submit(() -> { }).get();
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException impossible)
        {
            throw new IllegalStateException(impossible);
        }
    }

    /** Finishes every pending write, then stops the storage thread. */
    @Override
    public void close()
    {
        flush();
        io.shutdown();
        try
        {
            if (!io.awaitTermination(30, TimeUnit.SECONDS))
            {
                log.warn("Stall's storage thread did not finish in time; some changes may not be saved.", null);
            }
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
        }
    }

    static void writeAtomically(Path file, String content) throws IOException
    {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
        {
            ByteBuffer bytes = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining())
            {
                channel.write(bytes);
            }
            channel.force(true);
        }
        try
        {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException unsupported)
        {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
