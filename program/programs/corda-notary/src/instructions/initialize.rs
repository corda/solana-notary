use crate::Administration;
use crate::SEED_ADMIN;
use anchor_lang::prelude::*;

#[derive(Accounts)]
pub struct Initialize<'info> {
    #[account(mut)]
    pub admin: Signer<'info>,

    #[account(
        init,
        payer = admin,
        space = Administration::DISCRIMINATOR.len() + Administration::INIT_SPACE,
        // No other seeds are needed since the admin is a unique account
        seeds = [SEED_ADMIN],
        // The bump is used to ensure the PDA will generate a valid program address
        bump
    )]
    pub administration: Account<'info, Administration>,

    pub system_program: Program<'info, System>,
}
