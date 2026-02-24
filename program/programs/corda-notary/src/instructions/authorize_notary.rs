use crate::SEED_ADMIN;
use crate::SEED_NETWORK_ACCOUNT;
use crate::SEED_NOTARY_AUTHORIZATION;
use crate::{Administration, Network, NotaryAuthorization, NotaryError};
use anchor_lang::prelude::*;

#[derive(Accounts)]
#[instruction(address_to_authorize: Pubkey)]
pub struct AuthorizeNotary<'info> {
    #[account(mut)]
    pub admin: Signer<'info>,

    #[account(
        seeds = [SEED_ADMIN],
        bump = administration.bump,
        constraint = admin.key() == administration.admin @ NotaryError::Unauthorized,
        constraint = administration.admin != address_to_authorize @ NotaryError::CannotAuthorizeSelf
    )]
    pub administration: Account<'info, Administration>,

    #[account(
        init,
        payer = admin,
        space = NotaryAuthorization::DISCRIMINATOR.len() + NotaryAuthorization::INIT_SPACE,
        seeds = [SEED_NOTARY_AUTHORIZATION, address_to_authorize.as_ref()],
        bump
    )]
    pub authorization: Account<'info, NotaryAuthorization>,

    #[account(
        seeds = [SEED_NETWORK_ACCOUNT, network.network_id.to_le_bytes().as_ref()],
        bump = network.bump
    )]
    pub network: Account<'info, Network>,

    pub system_program: Program<'info, System>,
}
