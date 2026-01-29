# Solana CLI Documentation

## Overview

The Solana CLI tool is a command-line interface for managing Corda Admin Notary operations on the Solana blockchain. This tool provides
functionality to initialize, authorize, revoke, and list notaries within the Corda-Solana integration.

## Prerequisites

Before installation, ensure you have:

- Access to Solana blockchain network
- Proper configuration file setup
- Rust programming language installed

There are some general solana setup instructions that can be found [here](https://solana.com/docs/intro/installation).

## Installation

### Install Rust (if not already installed)

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

### Install Solana CLI

```bash
curl --proto '=https' --tlsv1.2 -sSfL https://solana-install.solana.workers.dev | bash
```

## Configuration

The CLI reads configuration settings from the default Solana configuration file located at `~/.config/solana/cli/config.yml`.

### Sample Configuration File

```yaml
json_rpc_url: http://localhost:8899
websocket_url: ''
keypair_path: /Users/username/.config/solana/id.json
address_labels:
  '11111111111111111111111111111111': System Program
commitment: confirmed
```

**Note:** Replace `/Users/username/` with your actual username path.

**Note:** In order to use a different wallet, replace the `keypair_path` with the path to your desired keypair file.

## Setup (For LocalNet Testing)

### 1. Build the Notary Program and start Solana Test Validator

```bash
# Build the Notary Program
cd solana-aggregator/notary-program
anchor build

# Start the Solana Test Validator and deploy the Notary Program
solana-test-validator --reset --ledger ../admin-cli/build/test-ledger --bpf-program notary95bwkGXj74HV2CXeCn4CgBzRVv5nmEVfqonVY target/deploy/corda_notary.so

```

### 2. Deploy Notary Program

```bash
ADMIN_KEYPAIR_FILE="../solana-program/target/deploy/corda_notary-keypair.json"
```

```bash
# Airdrop SOL to notary program admin development key
solana airdrop -k $ADMIN_KEYPAIR_FILE --commitment confirmed 10

# Airdrop SOL to notary account development key, replace <NOTARY_ACCOUNT_DEV_KEY> with the notary key
solana airdrop -k <NOTARY_ACCOUNT_DEV_KEY> --commitment confirmed 10
```

## Usage

From the admin-cli directory, you can run the Admin CLI commands to manage the Corda Notary on Solana.

### Basic Command Structure

```bash
java -jar build/libs/admin-cli-0.1.3-SNAPSHOT-all.jar [COMMAND] [OPTIONS]
```

### Available Commands

#### Initialize

Initializes a new Corda Notary on the Solana blockchain.

```bash
java -jar build/libs/admin-cli-0.1.3-SNAPSHOT-all.jar initialize [OPTIONS]
```

Sets up the initial notary configuration and deploys necessary smart contracts to the Solana blockchain.

**Example:**

```bash
# Initializes the Corda Notary program on the Solana blockchain
java -jar build/libs/admin-cli-0.1.3-SNAPSHOT-all.jar initialize --rpc http://localhost:8899 --rpc http://localhost:8900 -k $ADMIN_KEYPAIR_FILE
```

```bash
# Creates a new network with the specified ID so the notaries can be authorized to
java -jar build/libs/admin-cli-0.1.3-SNAPSHOT-all.jar create-network --rpc http://localhost:8899 --rpc http://localhost:8900 -k $ADMIN_KEYPAIR_FILE
```


```bash
# Authorizes the given notary account (specified by the --address option) on the given network ID (specified by the --network option)
java -jar build/libs/admin-cli-0.1.3-SNAPSHOT-all.jar authorize --address DevNMdtQW3Q4ybKQvxgwpJj84h5mb7JE218qTpZQnoA3 --network 0 --rpc http://localhost:8899 --rpc http://localhost:8900 -k $ADMIN_KEYPAIR_FILE
```

```bash
# Lists all the notaries in the network
java -jar build/libs/admin-cli-0.1.3-SNAPSHOT-all.jar list-notaries --rpc http://localhost:8899 --rpc http://localhost:8900 -k $ADMIN_KEYPAIR_FILE
```

```bash
# Revokes the notary account authorization
java -jar build/libs/admin-cli-0.1.3-SNAPSHOT-all.jar revoke --address DevNMdtQW3Q4ybKQvxgwpJj84h5mb7JE218qTpZQnoA3 --rpc http://localhost:8899 --rpc http://localhost:8900 -k $ADMIN_KEYPAIR_FILE
```

#### Help
Displays help information for the CLI commands.

```bash
java -jar build/libs/admin-cli-0.1.3-SNAPSHOT-all.jar --help
```
