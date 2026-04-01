# Corda Notary Program

The Corda notary program is an on-chain Solana program, written using [Anchor](https://www.anchor-lang.com/), that
tracks consumed [Corda](https://docs.r3.com/en/platform/corda/4.14/community/key-concepts-notaries.html) contract
states. It acts as a single global double-spend registry that independent Corda networks can delegate state tracking to.

---

## Overview

The program is structured around a simple access-control and state-tracking model:

1. An **admin** initialises the program and manages access.
2. The admin creates **network** namespaces — one per Corda network — to isolate state spaces.
3. The admin authorises specific **notary** key-pairs to submit transactions within a given network.
4. Authorised notaries call **commit** to atomically mark Corda input states as spent and record the new output
   Corda transaction.

Each Corda transaction's output states are tracked in a [`CordaTxAccount`](#cordatxaccount) PDA using a 128-bit
bitmask. A bit set to `1` means the corresponding output state is unspent; a bit cleared to `0` means it has been
consumed. The program enforces double-spend prevention: once a state is spent it cannot be spent again.

### Limitations

- A Corda transaction can have at most **128 output states** tracked by this program (indices 0–127).
- The admin key cannot be changed after initialisation (not yet implemented).

---

## Accounts

All accounts use a 1-byte custom Anchor discriminator and carry a `version` field set to the value of
`ACCOUNT_SCHEMA_VERSION`.

### `Administration`

Singleton account holding global program configuration.

| Field             | Type     | Description                                |
|-------------------|----------|--------------------------------------------|
| `version`         | `u8`     | Schema version                             |
| `bump`            | `u8`     | PDA canonical bump                         |
| `admin`           | `Pubkey` | The admin public key                       |
| `next_network_id` | `u16`    | Counter used to assign IDs to new networks |

**Discriminator:** `[1]`

**PDA seeds:** `["admin"]`

### `Network`

Represents an isolated namespace for a single Corda network. All `CordaTxAccount` PDAs are scoped to a network by
including the `network_id` in their seeds.

| Field        | Type  | Description                  |
|--------------|-------|------------------------------|
| `version`    | `u8`  | Schema version               |
| `bump`       | `u8`  | PDA canonical bump           |
| `network_id` | `u16` | The assigned network ID      |

**Discriminator:** `[4]`

**PDA seeds:** `["network_account", network_id as u16 LE]`

Network IDs are assigned sequentially starting at `0`. The `Administration.next_network_id` field is incremented
each time `create_network` is called.

### `NotaryAuthorization`

Records that a given notary key-pair is permitted to submit `commit` transactions for a specific network.

| Field        | Type     | Description                               |
|--------------|----------|-------------------------------------------|
| `version`    | `u8`     | Schema version                            |
| `bump`       | `u8`     | PDA canonical bump                        |
| `notary`     | `Pubkey` | The authorised notary address             |
| `network_id` | `u16`    | The network this notary is authorised for |

**Discriminator:** `[3]`

**PDA seeds:** `["notary_authorization", notary_pubkey]`

A notary address can only be authorised for one network at a time. The admin cannot authorise itself as a notary.
Revoking a notary closes the `NotaryAuthorization` account, returning its rent to the admin.

### `CordaTxAccount`

Tracks the spent/unspent status of up to 128 output states from a single Corda transaction within a given network.

| Field            | Type   | Description                                                                |
|------------------|--------|----------------------------------------------------------------------------|
| `version`        | `u8`   | Schema version                                                             |
| `unspent_bitset` | `u128` | Bitmask where bit `i` being `1` means output state at index `i` is unspent |

**Discriminator:** `[2]`

**PDA seeds:** `["corda_tx", tx_id as [u8; 32], network_id as u16 LE]`

When created the `unspent_bitset` is initialised to `u128::MAX` (all bits set), meaning all output states are
unspent. When an input state is consumed, its corresponding bit is cleared to `0`.
[Reference states](https://docs.r3.com/en/platform/corda/4.14/enterprise/key-concepts-transactions.html#reference-states)
are checked for conflicts but their bits are **not** cleared.

`CordaTxAccount` PDAs are created on demand — they are created by the `commit` instruction if they do not already
exist, which is the case when a transaction's outputs are being spent for the first time.

---

## Instructions

### `initialize` (discriminator: 0)

One-time program initialisation. Creates the singleton [`Administration`](#administration) account and assigns the
transaction signer as admin.

**Accounts:**

| Name             | Writable | Signer | Description                               |
|------------------|:--------:|:------:|-------------------------------------------|
| `admin`          |    ✅     |   ✅    | Payer and initial admin                   |
| `administration` |    ✅     |        | Administration PDA (created by this call) |
| `system_program` |          |        | Solana System Program                     |

**Arguments:** none

This instruction is not idempotent. Calling it a second time fails because the `administration` account already
exists.

### `authorize_notary` (discriminator: 1)

Authorises a Corda notary address to submit `commit` transactions for a specific network. Creates a
[`NotaryAuthorization`](#notaryauthorization) PDA.

**Accounts:**

| Name             | Writable | Signer | Description                                                       |
|------------------|:--------:|:------:|-------------------------------------------------------------------|
| `admin`          |    ✅     |   ✅    | Must match `administration.admin`                                 |
| `administration` |          |        | Administration PDA                                                |
| `authorization`  |    ✅     |        | NotaryAuthorization PDA for `address_to_authorize` (created here) |
| `network`        |          |        | The Network PDA for the target network                            |
| `system_program` |          |        | Solana System Program                                             |

**Arguments:**

| Name                   | Type     | Description                           |
|------------------------|----------|---------------------------------------|
| `address_to_authorize` | `Pubkey` | The notary public key to authorise    |

**Constraints:**
- Caller must be the admin: `admin.key() == administration.admin`
- Admin cannot authorise itself: `administration.admin != address_to_authorize`

### `revoke_notary` (discriminator: 2)

Revokes a notary's authorisation by closing its [`NotaryAuthorization`](#notaryauthorization) PDA. The account's
rent is returned to the admin.

**Accounts:**

| Name             | Writable | Signer | Description                                                   |
|------------------|:--------:|:------:|---------------------------------------------------------------|
| `admin`          |    ✅     |   ✅    | Must match `administration.admin`; receives reclaimed rent    |
| `administration` |          |        | Administration PDA                                            |
| `authorization`  |    ✅     |        | NotaryAuthorization PDA for `address_to_revoke` (closed here) |

**Arguments:**

| Name                | Type     | Description                     |
|---------------------|----------|---------------------------------|
| `address_to_revoke` | `Pubkey` | The notary public key to revoke |

**Constraints:**
- Caller must be the admin: `admin.key() == administration.admin`

Once revoked, the notary's key-pair can no longer call `commit`. A revoked notary can be re-authorised by calling
`authorize_notary` again.

### `create_network` (discriminator: 3)

Creates a new [`Network`](#network) PDA representing an isolated namespace for a Corda network. The network's ID is
assigned from `administration.next_network_id`, which is then incremented.

**Accounts:**

| Name             | Writable | Signer | Description                            |
|------------------|:--------:|:------:|----------------------------------------|
| `admin`          |    ✅     |   ✅    | Must match `administration.admin`      |
| `administration` |    ✅     |        | Administration PDA (updated)           |
| `network`        |    ✅     |        | New Network PDA (created by this call) |
| `system_program` |          |        | Solana System Program                  |

**Arguments:** none

**Constraints:**
- Caller must be the admin: `admin.key() == administration.admin`

### `commit` (discriminator: 4)

The core notarisation instruction. Atomically:
1. Creates a new [`CordaTxAccount`](#cordatxaccount) PDA for the transaction being notarised.
2. Checks all input and reference states for double-spend conflicts.
3. Marks all input states as spent by clearing their bits in the relevant `CordaTxAccount`s.

**Accounts:**

| Name             | Writable | Signer | Description                                                   |
|------------------|:--------:|:------:|---------------------------------------------------------------|
| `notary`         |    ✅     |   ✅    | Must match `authorization.notary`; pays rent for new accounts |
| `authorization`  |          |        | NotaryAuthorization PDA for the calling notary                |
| `system_program` |          |        | Solana System Program                                         |
| *(remaining)*    |    ✅     |        | `CordaTxAccount` PDAs — see below                             |

**Remaining accounts** must be provided in the following order:

1. The `CordaTxAccount` PDA for `tx_id` — the transaction being notarised (the **output account**). Must not
   already exist.
2. One `CordaTxAccount` PDA per entry in `inputs_and_references`, in the same order. These are the accounts for
   the transactions whose states are being consumed (the **input/reference accounts**). They are created on demand
   if they do not yet exist (e.g. when spending the output states of a freshly issued transaction).

Total remaining accounts: `1 + len(inputs_and_references)`.

**Arguments:**

| Name                    | Type                   | Description                                                 |
|-------------------------|------------------------|-------------------------------------------------------------|
| `tx_id`                 | `TxId`                 | 32-byte ID of the Corda transaction being notarised         |
| `inputs_and_references` | `Vec<StateRefGroup>`   | Grouped input and reference states to check/consume         |

**Constraints:**
- Caller must be an authorised notary: `notary.key() == authorization.notary`

**Idempotency:** The program itself is not idempotent with respect to `commit` — re-submitting the same `tx_id`
returns `Resubmission`. Idempotency (treating `Resubmission` as success) is handled by the Corda notary node.

---

## Errors

| Code | Name                  | Description                                                                                               |
|------|-----------------------|-----------------------------------------------------------------------------------------------------------|
| 6000 | `Unauthorized`        | The caller is not the authorised admin or notary                                                          |
| 6001 | `CannotAuthorizeSelf` | The admin attempted to authorise itself as a notary                                                       |
| 6002 | `InvalidAccounts`     | The number of remaining accounts does not match the arguments, or duplicate `txhash` values were provided |
| 6003 | `InvalidAccountOwner` | A provided `CordaTxAccount` is owned by a different program                                               |
| 6004 | `Conflict`            | One or more input or reference states have already been consumed                                          |
| 6005 | `InvalidStateRef`     | A state index is >= 128 (outside the `u128` tracking range)                                               |
| 6006 | `Resubmission`        | The transaction has already been notarised (output account already exists)                                |

---

## Types

### `TxId`

A 32-byte fixed-size array representing a transaction ID (typically a SHA-256 hash).

```rust
pub struct TxId(pub [u8; 32]);
```

### `FlaggedU8`

A single byte encoding a 7-bit state index and a 1-bit reference flag in the MSB.

```rust
pub struct FlaggedU8(u8);  // bit 7: reference flag, bits 0–6: state index (0–127)
```

### `StateRef`

Identifies a single Corda output state.

```rust
pub struct StateRef {
    pub txhash: TxId,  // Corda transaction ID
    pub index: u8,     // output index within the transaction
}
```

### `StateRefGroup`

Groups multiple states from the same transaction. This is the unit passed to `commit`'s
`inputs_and_references` argument. One `StateRefGroup` corresponds to one remaining account (one
`CordaTxAccount` PDA).

```rust
pub struct StateRefGroup {
    pub txhash: TxId,
    pub flagged_indices: Vec<FlaggedU8>,
}
```

---

## Multi-network model

The program serves as a shared global registry for multiple independent Corda networks. Each network gets its own
namespace enforced by including the `network_id` as a seed when deriving `CordaTxAccount` PDAs:

```
PDA seeds: ["corda_tx", tx_id (32 bytes), network_id (u16 LE)]
```

This means state consumption in one network has no effect on any other.

Each notary is bound to exactly one network via its `NotaryAuthorization` account, which stores the `network_id`.
The `commit` instruction reads `network_id` from the notary's authorization account and uses it to derive all
`CordaTxAccount` addresses, **ensuring a notary can only consume states within its own network**.

```
Admin
 └─ create_network  →  Network (network_id=0)
                    →  Network (network_id=1)
                    →  ...
 └─ authorize_notary(notaryA, network=0)  →  NotaryAuthorization(notary=notaryA, network_id=0)
 └─ authorize_notary(notaryB, network=1)  →  NotaryAuthorization(notary=notaryB, network_id=1)
```

---

## Corda Enterprise integration

[Corda Enterprise](https://docs.r3.com/en/platform/corda/4.14/enterprise/notary/solana-notary.html) is the only
client that calls the `commit` instruction (when configured as a Solana notary).

### Transaction ID hashing

Before submitting a `commit` transaction to Solana, Corda re-hashes all transaction IDs (both the notarised
transaction ID and those of the input `StateRef`s). The Solana program is unaware of this transformation — it
receives only the hashed values.

Unlike most distributed ledgers, Corda shares transaction data only on a
[need-to-know basis](https://docs.r3.com/en/platform/corda/4.14/community/key-concepts-ecosystem.html#what-is-a-network),
point-to-point between the parties directly involved — a `StateRef` is private information known only to
participants. Posting raw values to a public blockchain would expose `StateRef`s to any observer, including
unrelated nodes on the same Corda network. Beyond the privacy violation itself, a CorDapp designed around this
confidentiality assumption could be vulnerable: an adversary who learns a `StateRef` could attempt to construct a
legitimate Corda transaction spending that state ahead of its rightful owner. Hashing makes the `CordaTxAccount`
PDA addresses opaque, so the original `StateRef`s cannot be recovered from the on-chain data.

### Processing conflicts

When the program returns a `Conflict` error, it emits one log message per conflicted state in the format:

```
Program log: Conflict: {group_index}.{flagged_indices_index}
```

The Corda Enterprise client parses these log messages to reconstruct the set of already-consumed states and return
a structured `NotaryError.Conflict` to the requesting Corda node.

### Resubmission handling

A `Resubmission` error means the output account for the given `tx_id` already exists, i.e. the transaction was
already successfully notarised in a previous attempt. Corda treats this as success and signs the notarisation
response, making the `commit` flow idempotent from the perspective of Corda.
