package net.corda.solana.notary.test;

import net.corda.solana.notary.client.instructions.AuthorizeNotary;
import net.corda.solana.notary.client.instructions.CreateNetwork;
import net.corda.solana.notary.client.instructions.Initialize;
import net.corda.solana.notary.common.SolanaClient;
import net.corda.solana.notary.common.SolanaTransactionException;
import net.corda.solana.notary.common.SolanaTransactionExpiredException;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.Signer;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static net.corda.solana.notary.common.SolanaUtils.randomSigner;

public class NotaryEnvironment {
    private static final long LAMPORTS_PER_SOL = 1_000_000_000;

    private final SolanaClient client;
    private final Signer admin;
    private short nextNetworkId;

    public NotaryEnvironment(SolanaClient client) {
        this.client = Objects.requireNonNull(client);
        admin = randomSigner();
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
        var signature = client.call("requestAirdrop", String.class, admin.publicKey(), 100 * LAMPORTS_PER_SOL);
        try {
            client.asyncConfirm(signature).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause instanceof SolanaTransactionException ste) {
                throw ste;
            } else if (cause instanceof SolanaTransactionExpiredException stee) {
                throw stee;
            } else if (cause instanceof RuntimeException re) {
                throw re;
            } else {
                throw new RuntimeException(e);
            }
        }
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
}
