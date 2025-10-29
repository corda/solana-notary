package net.corda.solana.notary.test;

import com.lmax.solana4j.api.PublicKey;
import com.lmax.solana4j.client.jsonrpc.SolanaJsonRpcClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SolanaTestValidator implements AutoCloseable {
    private final Process process;
    private final int rpcPort;

    private SolanaTestValidator(Process process, int rpcPort) {
        this.process = process;
        this.rpcPort = rpcPort;
    }

    public URI getRpcEndpoint() {
        return URI.create("http://127.0.0.1:" + rpcPort);
    }

    public URI getWsEndpoint() {
        return URI.create("ws://127.0.0.1:" + (rpcPort + 1));
    }

    public SolanaTestValidator waitForReadiness() throws InterruptedException {
        while (true) {
            try (var ignored = new Socket("127.0.0.1", rpcPort)) {
                return this;
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
    }

    public SolanaJsonRpcClient connectRpcClient() {
        return new SolanaJsonRpcClient(HttpClient.newHttpClient(), getRpcEndpoint().toString());
    }

    @Override
    public void close() {
        var interrupted = false;
        while (process.isAlive()) {
            process.destroy();
            try {
                if (process.waitFor(1, TimeUnit.MINUTES)) {
                    continue;
                }
            } catch (InterruptedException e) {
                interrupted = true;
            }
            process.destroyForcibly();
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public static Builder builder() {
        return new Builder();
    }


    public static class Builder {
        private boolean reset;
        private boolean quiet;
        private Integer rpcPort;
        private Integer gossipPort;
        private Integer faucetPort;
        private boolean dynamicPorts;
        private Path ledger;
        private final Map<PublicKey, Path> bpfPrograms = new HashMap<>();

        private Builder() { }

        public Builder reset() {
            reset = true;
            return this;
        }

        public Builder quiet() {
            quiet = true;
            return this;
        }

        public Builder rpcPort(int rpcPort) {
            this.rpcPort = rpcPort;
            return this;
        }

        public Builder gossipPort(int gossipPort) {
            this.gossipPort = gossipPort;
            return this;
        }

        public Builder faucetPort(int faucetPort) {
            this.faucetPort = faucetPort;
            return this;
        }

        public Builder dynamicPorts() {
            dynamicPorts = true;
            return this;
        }

        public Builder ledger(Path ledger) {
            this.ledger = ledger;
            return this;
        }

        public Builder bpfProgram(PublicKey programId, Path programFile) {
            bpfPrograms.put(programId, programFile);
            return this;
        }

        private static int availablePort(Integer assignedPort) throws IOException {
            if (assignedPort != null) {
                return assignedPort;
            }
            try (var server = new ServerSocket(0)) {
                return server.getLocalPort();
            }
        }

        private static void addPortArg(String portName, Integer assignedPort, List<String> args) {
            if (assignedPort != null) {
                args.add("--" + portName + "-port");
                args.add(assignedPort.toString());
            }
        }

        public SolanaTestValidator start() throws IOException {
            var rpcPort = this.rpcPort;
            var gossipPort = this.gossipPort;
            var faucetPort = this.faucetPort;
            if (dynamicPorts) {
                rpcPort = availablePort(rpcPort);
                gossipPort = availablePort(gossipPort);
                faucetPort = availablePort(faucetPort);
            }

            final var command = new ArrayList<String>();
            command.add("solana-test-validator");
            addPortArg("rpc", rpcPort, command);
            addPortArg("gossip", gossipPort, command);
            addPortArg("faucet", faucetPort, command);
            if (reset) {
                command.add("--reset");
            }
            if (quiet) {
                command.add("--quiet");
            }
            if (ledger != null) {
                command.add("--ledger");
                command.add(ledger.toAbsolutePath().toString());
            }
            bpfPrograms.forEach((programId, file) -> {
                command.add("--bpf-program");
                command.add(programId.base58());
                command.add(file.toAbsolutePath().toString());
            });
            var process = new ProcessBuilder(command).inheritIO().start();
            return new SolanaTestValidator(process, rpcPort != null ? rpcPort : 8899);
        }
    }
}
