package net.corda.solana.notary.test;

import com.lmax.solana4j.api.PublicKey;
import com.lmax.solana4j.client.jsonrpc.SolanaJsonRpcClient;
import com.lmax.solana4j.client.jsonrpc.SolanaJsonRpcClientException;
import net.corda.solana.notary.client.CordaNotary;
import net.corda.solana.notary.common.AnchorInstruction;
import net.corda.solana.notary.common.Signer;
import net.corda.solana.notary.common.rpc.DefaultRpcParams;
import net.corda.solana.notary.common.rpc.SolanaApiExt;

import java.util.List;
import java.util.Objects;

import static net.corda.solana.notary.common.rpc.SolanaApiExt.checkResponse;

public class NotaryEnvironment {
    private static final DefaultRpcParams RPC_PARAMS = new DefaultRpcParams();
    private static final long LAMPORTS_PER_SOL = 1_000_000_000;

    private final SolanaJsonRpcClient rpcClient;
    private final Signer admin;
    private short nextNetworkId;

    public NotaryEnvironment(SolanaJsonRpcClient rpcClient) {
        this.rpcClient = Objects.requireNonNull(rpcClient);
        admin = Signer.random();
    }

    public SolanaJsonRpcClient getRpcClient() {
        return rpcClient;
    }

    public Signer getAdmin() {
        return admin;
    }

    public short getNextNetworkId() {
        return nextNetworkId;
    }

    public void initialiseProgram() throws SolanaJsonRpcClientException {
        fundAdmin();
        sendAndConfirm(new CordaNotary.Initialize(admin, admin));
    }

    public void fundAdmin() throws SolanaJsonRpcClientException {
        checkResponse(
            rpcClient.requestAirdrop(admin.getAccount().base58(), 100 * LAMPORTS_PER_SOL, RPC_PARAMS),
            "requestAirdrop"
        );
    }

    public short createNewCordaNetwork() {
        var networkId = nextNetworkId++;
        sendAndConfirm(new CordaNotary.CreateNetwork(admin, networkId, admin));
        return networkId;
    }

    public void addCordaNotary(short networkId, PublicKey cordaNotary) {
        sendAndConfirm(new CordaNotary.AuthorizeNotary(cordaNotary, admin, networkId, admin));
    }

    private void sendAndConfirm(AnchorInstruction instruction) {
        SolanaApiExt.sendAndConfirm(rpcClient, instruction, List.of(), RPC_PARAMS);
    }
}
