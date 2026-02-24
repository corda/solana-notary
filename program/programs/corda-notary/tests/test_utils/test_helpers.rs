use anchor_client::solana_sdk::signer::Signer;
use anchor_lang::prelude::Pubkey;
use anchor_lang::solana_program::instruction::Instruction;
use anchor_lang::solana_program::message::v0::Message;
use anchor_lang::solana_program::message::VersionedMessage;
use corda_notary::types::FlaggedU8;
use corda_notary::{StateRefGroup, TxId};
use litesvm::types::TransactionResult;
use litesvm::LiteSVM;
use rand::Rng;
use solana_sdk::signature::Keypair;
use solana_sdk::transaction::VersionedTransaction;
use std::cell::RefCell;
use std::rc::Rc;

pub fn generate_random_corda_tx_id() -> TxId {
    let mut rng = rand::rng();
    let random_bytes: [u8; 32] = rng.random();
    TxId(random_bytes)
}

pub fn deploy_notary_program(svm: &mut LiteSVM) {
    let bytes = include_bytes!("../../../../target/deploy/corda_notary.so");
    let program_id = corda_notary::ID;
    svm.add_program(program_id, bytes);
}

pub fn get_authorization_pda_for_address(address: &Pubkey) -> Pubkey {
    let (authorization_pda, _) = Pubkey::find_program_address(
        &[b"notary_authorization", address.as_ref()],
        &corda_notary::ID,
    );
    authorization_pda
}

pub fn get_corda_tx_output_account(tx_id: TxId) -> Pubkey {
    let (output_account, _) = Pubkey::find_program_address(&[&tx_id.as_ref()], &corda_notary::ID);
    output_account
}

pub fn get_admin_pda() -> Pubkey {
    let (admin_pda, _) = Pubkey::find_program_address(&[b"admin"], &corda_notary::ID);
    admin_pda
}

pub fn get_network_pda(network_id: u16) -> Pubkey {
    let (network_pda, _) = Pubkey::find_program_address(
        &[b"network_account", network_id.to_le_bytes().as_ref()],
        &corda_notary::ID,
    );
    network_pda
}

pub fn inputs_group(tx_id: TxId, indices: Vec<u8>) -> StateRefGroup {
    StateRefGroup::new(tx_id, indices.iter().map(|i| input_index(*i)).collect())
}

pub fn references_group(tx_id: TxId, indices: Vec<u8>) -> StateRefGroup {
    StateRefGroup::new(tx_id, indices.iter().map(|i| ref_index(*i)).collect())
}

pub fn input_index(index: u8) -> FlaggedU8 {
    FlaggedU8::new(index, false)
}

pub fn ref_index(index: u8) -> FlaggedU8 {
    FlaggedU8::new(index, true)
}

pub fn build_and_send_transaction(
    svm: Rc<RefCell<LiteSVM>>,
    payer: Keypair,
    instruction: Instruction,
) -> TransactionResult {
    let mut svm = svm.borrow_mut();

    let blockhash = svm.latest_blockhash();
    let tx = VersionedTransaction::try_new(
        VersionedMessage::V0(
            Message::try_compile(&payer.pubkey(), &[instruction], &[], blockhash).unwrap(),
        ),
        &[payer.insecure_clone()],
    )
    .unwrap();

    svm.send_transaction(tx)
}

pub fn get_pda<T>(
    svm: Rc<RefCell<LiteSVM>>,
    pda: &Pubkey,
) -> Result<Option<T>, anchor_client::ClientError>
where
    T: anchor_lang::AccountDeserialize,
{
    match svm.borrow().get_account(pda) {
        Some(account) => {
            if account.owner != corda_notary::ID {
                return Ok(None);
            }
            match T::try_deserialize(&mut account.data.as_slice()) {
                Ok(data) => Ok(Some(data)),
                Err(e) => Err(e.into()),
            }
        }
        None => Ok(None),
    }
}
