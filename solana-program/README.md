# Notary Program

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
