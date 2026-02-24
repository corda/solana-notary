use crate::SEED_ADMIN;
use crate::SEED_NETWORK_ACCOUNT;
use crate::{Administration, Network, NotaryError};
use anchor_lang::prelude::*;

#[derive(Accounts)]
pub struct CreateNetwork<'info> {
    #[account(mut)]
    pub admin: Signer<'info>,

    #[account(mut,
        seeds = [SEED_ADMIN],
        bump = administration.bump,
        constraint = admin.key() == administration.admin @ NotaryError::Unauthorized,
    )]
    pub administration: Account<'info, Administration>,

    #[account(
        init,
        payer = admin,
        space = Network::DISCRIMINATOR.len() + Network::INIT_SPACE,
        seeds = [SEED_NETWORK_ACCOUNT, administration.next_network_id.to_le_bytes().as_ref()],
        bump
    )]
    pub network: Account<'info, Network>,

    pub system_program: Program<'info, System>,
}
