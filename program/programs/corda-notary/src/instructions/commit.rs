use crate::SEED_NOTARY_AUTHORIZATION;
use crate::{NotaryAuthorization, NotaryError};
use anchor_lang::prelude::*;

#[derive(Accounts)]
pub struct Commit<'info> {
    #[account()]
    pub notary: Signer<'info>,

    #[account(
        seeds = [SEED_NOTARY_AUTHORIZATION, notary.key().as_ref()],
        bump = authorization.bump,
        constraint = notary.key() == authorization.notary @ NotaryError::Unauthorized
    )]
    pub authorization: Account<'info, NotaryAuthorization>,

    pub system_program: Program<'info, System>,
}
