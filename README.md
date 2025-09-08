# Corda Solana Notary

The Corda Solana [notary](https://docs.r3.com/en/platform/corda/4.12/community/key-concepts-notaries.html) is an
on-chain Solana program that records consumed
[`StateRef`](https://docs.r3.com/en/api-ref/corda/4.12/community/javadoc/net/corda/core/contracts/StateRef.html)s. 
An appropriately configured Corda notary node can delegate the tracking of spent states to this program.

## Overview

There are two components in this repo:

1. `notary-program`: The on-chain Solana program written using Anchor. This also the Kotlin client code generator 
   for the program IDL.
2. `admin-cli`: A CLI tool for managing access to the notary program. More information can be found
   [here](admin-cli/README.md). This is written in Kotlin.

// TODO Add a note about Gradle

## Notary Program

The on-chain notary program acts is a single global database for all Corda networks which integrate with Solana. 
Each notary(s) from every separate Corda network is assigned its own separate namespace, so notarisation of state 
data cannot overlap across different networks. An admin authority is in charge of creating these namespaces and 
giving access to Corda notary nodes.

The notary program has the following instructions:

- `initialize`: Called immediately after the program is first deployed. The signer of this transaction is assigned to be
  the admin of the program.
- `create_network`: Creates a new Corda network namespace. All Corda notaries assigned to the same namespace can be
  consumed the states of that namesapce. Only the admin can call this instruction.
- `authorize`: Authorises access for a Corda notary address to the specific network. The same address cannot be part 
  of multiple networks. Only the admin can call this instruction.
- `revoke`: Revokes access for a Corda notary.
- `commit`: The notarisation instruction which spends the given input states. Only authorised notary address can 
  access this instruction.

The ability to change the admin authority has not yet been implemented.
