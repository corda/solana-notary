use anchor_lang::prelude::*;

#[account(discriminator = 1)]
#[derive(InitSpace, Debug)]
pub struct Administration {
    pub version: u8,
    pub bump: u8,
    pub admin: Pubkey,
    /// The next network ID to be used for a new network
    pub next_network_id: u16,
}

#[account(discriminator = 2)]
#[derive(InitSpace, Debug)]
pub struct CordaTxAccount {
    pub version: u8,
    pub unspent_bitset: u128,
}

#[account(discriminator = 3)]
#[derive(InitSpace, Debug)]
pub struct NotaryAuthorization {
    pub version: u8,
    pub bump: u8,
    /// The notary address that is authorized
    pub notary: Pubkey,
    /// The network this notary belongs to
    pub network_id: u16,
}

#[account(discriminator = 4)]
#[derive(InitSpace, Debug)]
pub struct Network {
    pub version: u8,
    pub bump: u8,
    pub network_id: u16,
}
