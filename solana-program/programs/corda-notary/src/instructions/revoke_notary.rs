use crate::states::{Administration, NotaryAuthorization};
use crate::NotaryError;
use crate::SEED_ADMIN;
use crate::SEED_NOTARY_AUTHORIZATION;
use anchor_lang::prelude::*;

#[derive(Accounts)]
#[instruction(address_to_revoke: Pubkey)]
pub struct RevokeNotary<'info> {
    #[account(mut)]
    pub admin: Signer<'info>,

    #[account(
        seeds = [SEED_ADMIN],
        bump = administration.bump,
        constraint = admin.key() == administration.admin @ NotaryError::Unauthorized
    )]
    pub administration: Account<'info, Administration>,

    #[account(
        mut,
        seeds = [SEED_NOTARY_AUTHORIZATION, address_to_revoke.key().as_ref()],
        bump = authorization.bump,
        // Rent is returned to admin account
        close = admin
    )]
    pub authorization: Account<'info, NotaryAuthorization>,
}
