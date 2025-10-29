package net.corda.solana.notary.test;

import net.corda.solana.notary.client.CordaNotary;
import net.corda.solana.notary.common.PrivateKeyByteBufferSigner;
import net.corda.solana.notary.common.Signer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public interface TestingSupport {
    static Path writeToDir(Signer signer, Path dir) {
        var file = dir.resolve(signer.getAccount().base58() + ".json");
        ((PrivateKeyByteBufferSigner)signer.getByteBufferSigner()).writeToFile(file);
        return file;
    }

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
