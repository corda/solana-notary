pub use crate::instructions::*;
use crate::states::*;
use crate::ACCOUNT_SCHEMA_VERSION;
use crate::{types::*, NotaryError};
use anchor_lang::{
    prelude::*,
    solana_program::{program::invoke_signed, system_instruction},
    Bumps, Discriminator,
};
use std::collections::HashSet;
use std::u128;

// size of account discriminator
const CORDA_TX_DISCRIMINATOR_SIZE: usize = CordaTxAccount::DISCRIMINATOR.len();

// layout of remaining account:
// output account, plus a list of input and reference accounts. The StateRefGroup knows
// which indices are inputs and which are reference states.
pub fn do_commit<'a, 'b, 'c, 'info>(
    ctx: &Context<'a, 'b, 'c, 'info, Commit<'info>>,
    inputs_and_references: Vec<StateRefGroup>,
    tx_id: &TxId,
) -> Result<()> {
    if ctx.remaining_accounts.len() != 1 /* output */ + inputs_and_references.len() {
        msg!(
            "InvalidAccounts: expected {} remaining accounts",
            inputs_and_references.len() + 1
        );
        return err!(NotaryError::InvalidAccounts);
    }

    // ban resubmission - check if we have seen the account before
    let (output_account, output_bump) = get_corda_tx_account(ctx, tx_id, 0)?;

    // The account is already populated - fail as resubmission is not allowed
    if !output_account.data_is_empty() {
        return err!(NotaryError::Resubmission);
    }

    // Create output account
    create_corda_tx_account(output_account, ctx, tx_id, output_bump)?;

    // Anchor doesn't like the same account be updated more than once, so we make sure there aren't any duplicates.
    // This check can be removed once we move away from Anchor to further save on the CUs.
    let mut seen_txhashes = HashSet::with_capacity(inputs_and_references.len());
    let mut has_conflicts = false;

    const INPUT_OFFSET: usize = 1;

    for (group_index, state_ref_group) in inputs_and_references.iter().enumerate() {
        if !seen_txhashes.insert(state_ref_group.txhash) {
            msg!("InvalidAccounts: duplicate txhash @ {}", group_index);
            return err!(NotaryError::InvalidAccounts);
        }

        let corda_tx_account_info = get_and_create_corda_tx_account(
            ctx,
            &state_ref_group.txhash,
            INPUT_OFFSET + group_index,
        )?;

        let mut corda_tx_account =
            CordaTxAccount::try_deserialize(&mut &corda_tx_account_info.data.borrow()[..])?;

        for (group_indices_index, &flagged_state_ref_index) in
            state_ref_group.flagged_indices.iter().enumerate()
        {
            let state_ref_index = flagged_state_ref_index.value();
            let is_reference = flagged_state_ref_index.flag();
            if state_ref_index >= 128 {
                msg!("InvalidStateRef: {}.{}", group_index, group_indices_index);
                return err!(NotaryError::InvalidStateRef);
            }
            let mask = 1u128 << state_ref_index;
            if corda_tx_account.unspent_bitset & mask == 0 {
                msg!("Conflict: {}.{}", group_index, group_indices_index);
                // All conflicts need to be reported, so only return the error at the end.
                has_conflicts = true;
            }
            if !is_reference {
                corda_tx_account.unspent_bitset &= !mask; // Spend the input state by clearing the flag
            }
        }

        corda_tx_account.try_serialize(&mut *corda_tx_account_info.data.borrow_mut())?;
    }

    if has_conflicts {
        err!(NotaryError::Conflict)
    } else {
        Ok(())
    }
}

fn get_and_create_corda_tx_account<'a, 'b, 'c, 'info>(
    ctx: &Context<'a, 'b, 'c, 'info, Commit<'info>>,
    txhash: &TxId,
    index: usize,
) -> Result<&'c AccountInfo<'info>> {
    let (account_info, bump) = get_corda_tx_account(ctx, txhash, index)?;

    if account_info.data_is_empty() {
        create_corda_tx_account(&account_info, ctx, txhash, bump)?;
    } else if account_info.owner != ctx.program_id {
        msg!(
            "InvalidAccountOwner: {} account owner ({}) is not the program",
            index,
            account_info.owner
        );
        return err!(NotaryError::InvalidAccountOwner);
    }

    Ok(account_info)
}

fn get_corda_tx_account<'a, 'b, 'c, 'info>(
    ctx: &Context<'a, 'b, 'c, 'info, Commit<'info>>,
    txhash: &TxId,
    index: usize,
) -> Result<(&'c AccountInfo<'info>, u8)> {
    let (expected_address, bump) = Pubkey::find_program_address(
        &[
            b"corda_tx",
            txhash.as_ref(),
            ctx.accounts.authorization.network_id.to_le_bytes().as_ref(),
        ],
        ctx.program_id,
    );

    let account_info = &ctx.remaining_accounts[index];

    if *account_info.key != expected_address {
        msg!(
            "InvalidAccounts: {} expected {}, actual {}",
            index,
            expected_address,
            *account_info.key
        );
        return err!(NotaryError::InvalidAccounts);
    }

    Ok((account_info, bump))
}

fn create_corda_tx_account<'a, 'b, 'c, 'info>(
    account_info: &AccountInfo<'info>,
    ctx: &Context<'a, 'b, 'c, 'info, Commit<'info>>,
    tx_id: &TxId,
    bump: u8,
) -> Result<()> {
    let space = CORDA_TX_DISCRIMINATOR_SIZE + CordaTxAccount::INIT_SPACE;
    let lamports_required = Rent::get()?.minimum_balance(space);

    invoke_signed(
        &system_instruction::create_account(
            ctx.accounts.notary.key,
            account_info.key,
            lamports_required,
            space as u64, // account span
            &crate::ID,   // owner program
        ),
        &[
            ctx.accounts.notary.to_account_info(),
            account_info.clone(),
            ctx.accounts.system_program.to_account_info(),
        ],
        &[&[
            b"corda_tx",
            tx_id.as_ref(),
            ctx.accounts.authorization.network_id.to_le_bytes().as_ref(),
            &[bump],
        ]],
    )?;

    let account_data = CordaTxAccount {
        version: ACCOUNT_SCHEMA_VERSION,
        unspent_bitset: u128::MAX,
    };

    let mut writer = account_info.data.borrow_mut();

    account_data.try_serialize(&mut &mut writer[..])?;

    Ok(())
}
