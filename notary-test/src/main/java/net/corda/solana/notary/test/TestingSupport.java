package net.corda.solana.notary.test;

import net.corda.solana.notary.client.CordaNotary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public interface TestingSupport {
    static SolanaTestValidator.Builder addNotaryProgram(SolanaTestValidator.Builder builder) {
        Path programFile;
        try (var stream = Objects.requireNonNull(
            TestingSupport.class.getResourceAsStream("/net/corda/solana/notary/program/corda_notary.so")
        )) {
            programFile = Files.createTempFile("corda_notary", ".so");
            Files.copy(stream, programFile, REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        builder.bpfProgram(CordaNotary.PROGRAM_ID, programFile);
        return builder;
    }
}
