package net.corda.solana.notary.testing;

import com.r3.corda.lib.solana.core.*;
import com.r3.corda.lib.solana.testing.SolanaTestValidator;
import net.corda.solana.notary.client.CordaNotary;
import net.corda.solana.notary.client.instructions.AuthorizeNotary;
import net.corda.solana.notary.client.instructions.CreateNetwork;
import net.corda.solana.notary.client.instructions.Initialize;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.Signer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class NotaryEnvironment {
    private final SolanaClient client;
    private final Signer admin;
    private short nextNetworkId;

    public NotaryEnvironment(SolanaClient client, Signer admin) {
        this.client = Objects.requireNonNull(client);
        this.admin = Objects.requireNonNull(admin);
    }

    public NotaryEnvironment(SolanaClient client) {
        this(client, SolanaUtils.randomSigner());
    }

    public SolanaClient client() {
        return client;
    }

    public Signer admin() {
        return admin;
    }

    public short nextNetworkId() {
        return nextNetworkId;
    }

    public void initializeProgram() throws SolanaTransactionException, SolanaTransactionExpiredException {
        new AccountManagement(client).airdropSol(admin.publicKey(), 10);
        client.sendAndConfirm(
            (builder) -> builder.createTransaction(Initialize.instruction(admin.publicKey())),
            admin
        );
    }

    public short createNewCordaNetwork() throws SolanaTransactionException, SolanaTransactionExpiredException {
        var networkId = nextNetworkId++;
        client.sendAndConfirm(
            (builder) -> builder.createTransaction(CreateNetwork.instruction(admin.publicKey(), networkId)),
            admin
        );
        return networkId;
    }

    /**
     * Authorise the given notary key onto an existing network.
     */
    public void addCordaNotary(short networkId, PublicKey cordaNotary)
        throws SolanaTransactionException, SolanaTransactionExpiredException
    {
        client.sendAndConfirm(
            (builder) -> builder.createTransaction(AuthorizeNotary.instruction(cordaNotary, admin.publicKey(), networkId)),
            admin
        );
    }

    /**
     * Authorise the given notary key onto a new network.
     */
    public short addCordaNotary(PublicKey cordaNotary)
        throws SolanaTransactionException, SolanaTransactionExpiredException
    {
        var networkId = createNewCordaNetwork();
        addCordaNotary(networkId, cordaNotary);
        return networkId;
    }

    public static SolanaTestValidator.Builder addNotaryProgram(SolanaTestValidator.Builder builder) {
        builder.bpfProgram(CordaNotary.PROGRAM_ID, NotaryProgram.file);
        return builder;
    }

    // By putting the notary file in a separate class we avoid doing a file copy when NotaryEnvironment is first loaded.
    private static class NotaryProgram {
        private static final Path file;
        static {
            try (var stream = Objects.requireNonNull(
                NotaryEnvironment.class.getResourceAsStream("/net/corda/solana/notary/program/corda_notary.so")
            )) {
                file = Files.createTempFile("corda_notary", ".so");
                file.toFile().deleteOnExit();
                Files.copy(stream, file, REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
