#![allow(unexpected_cfgs)]

mod instructions;
pub mod states;
pub mod types;

use anchor_lang::prelude::{thiserror::Error, *};

pub use instructions::*;
pub use states::{Administration, CordaTxAccount, Network, NotaryAuthorization};
pub use types::StateRefGroup;
pub use types::TxId;

mod commit;

use commit::do_commit;

declare_id!("notary95bwkGXj74HV2CXeCn4CgBzRVv5nmEVfqonVY");

pub const ACCOUNT_SCHEMA_VERSION: u8 = 1;
pub const SEED_ADMIN: &[u8] = b"admin";
pub const SEED_NETWORK_ACCOUNT: &[u8] = b"network_account";
pub const SEED_NOTARY_AUTHORIZATION: &[u8] = b"notary_authorization";
pub const SEED_CORDA_TX: &[u8] = b"corda_tx";

#[program]
pub mod corda_notary {
    use super::*;

    #[instruction(discriminator = 0)]
    pub fn initialize(ctx: Context<Initialize>) -> Result<()> {
        let administration = &mut ctx.accounts.administration;
        administration.admin = ctx.accounts.admin.key(); // Set the admin to the signer
        administration.next_network_id = 0; // Initialize the next network ID
        administration.bump = ctx.bumps.administration; // Use the correct bump for the admin config
        administration.version = ACCOUNT_SCHEMA_VERSION;
        msg!(
            "Initialized Corda Notary with admin: {}",
            ctx.accounts.admin.key()
        );
        Ok(())
    }

    #[instruction(discriminator = 1)]
    pub fn authorize_notary(
        ctx: Context<AuthorizeNotary>,
        address_to_authorize: Pubkey,
    ) -> Result<()> {
        let auth = &mut ctx.accounts.authorization;
        auth.bump = ctx.bumps.authorization;
        auth.notary = address_to_authorize; // Store the notary address that is authorized
        auth.network_id = ctx.accounts.network.network_id; // Store the network ID this authorization is valid for
        auth.version = ACCOUNT_SCHEMA_VERSION;
        msg!("Authorized notary: {}", ctx.accounts.admin.key());
        Ok(())
    }

    #[instruction(discriminator = 2)]
    pub fn revoke_notary(ctx: Context<RevokeNotary>, address_to_revoke: Pubkey) -> Result<()> {
        msg!("Revoked notary: {}", ctx.accounts.authorization.key());
        Ok(())
    }

    #[instruction(discriminator = 3)]
    pub fn create_network(ctx: Context<CreateNetwork>) -> Result<()> {
        let administration = &mut ctx.accounts.administration;
        let network = &mut ctx.accounts.network;
        network.bump = ctx.bumps.network;
        network.network_id = administration.next_network_id; // Store the network ID
        network.version = ACCOUNT_SCHEMA_VERSION;
        administration.next_network_id = administration.next_network_id.checked_add(1).unwrap(); // Increment the next network ID
        msg!(
            "Network account created for network ID: {}",
            network.network_id
        );
        Ok(())
    }

    /// Instruction for committing/notarising a Corda transaction on-chain.
    ///
    /// A PDA for the transaction ID is created (the first of the remaining accounts). If one already exists then it
    /// means that transaction has already been notarised and an error is returned, i.e. it's not idempotent. The
    /// idempotency rule is implemented by the Corda notary node.
    ///
    /// The rest of the remaining accounts are taken up by the PDAs for each StateRefGroup.
    #[instruction(discriminator = 4)]
    pub fn commit<'a, 'b, 'c, 'info>(
        ctx: Context<'a, 'b, 'c, 'info, Commit<'info>>,
        tx_id: TxId,
        inputs_and_references: Vec<StateRefGroup>,
    ) -> Result<()> {
        do_commit(&ctx, inputs_and_references, &tx_id)
    }
}

#[error_code]
#[derive(PartialEq, Error)]
pub enum NotaryError {
    #[msg("Not authorized to perform action")]
    Unauthorized,
    CannotAuthorizeSelf,
    #[msg("Instruction accounts do not line up with arguments")]
    InvalidAccounts,
    #[msg("Trying to use an account not owned by this program")]
    InvalidAccountOwner,
    #[msg("Input or reference state already consumed")]
    Conflict,
    InvalidStateRef,
    #[msg("Corda transaction has already been notarized")]
    Resubmission,
}
