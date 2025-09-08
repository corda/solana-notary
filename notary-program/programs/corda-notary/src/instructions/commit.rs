use crate::{NotaryAuthorization, NotaryError};
use anchor_lang::prelude::*;

#[derive(Accounts)]
pub struct Commit<'info> {
    #[account()]
    pub notary: Signer<'info>,

    #[account(
        seeds = [b"notary_authorization", notary.key().as_ref()],
        bump = authorization.bump,
        constraint = notary.key() == authorization.notary @ NotaryError::Unauthorized
    )]
    pub authorization: Account<'info, NotaryAuthorization>,

    pub system_program: Program<'info, System>,
}
