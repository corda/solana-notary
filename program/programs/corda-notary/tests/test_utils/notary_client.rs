use anchor_client::solana_sdk::instruction::Instruction;
use anchor_client::solana_sdk::native_token::LAMPORTS_PER_SOL;
use anchor_client::solana_sdk::signature::Keypair;
use anchor_client::solana_sdk::signer::Signer;
use anchor_lang::prelude::AccountMeta;
use anchor_lang::prelude::Pubkey;
use anchor_lang::InstructionData;
use corda_notary::StateRefGroup;
use corda_notary::{CordaTxAccount, NotaryAuthorization, TxId};
use litesvm::types::TransactionResult;
use litesvm::LiteSVM;
use std::cell::RefCell;
use std::rc::Rc;

use crate::test_utils::test_helpers::build_and_send_transaction;
use crate::test_utils::test_helpers::get_authorization_pda_for_address;
use crate::test_utils::test_helpers::get_pda;

pub struct NotaryClient {
    pub svm: Rc<RefCell<LiteSVM>>,
    pub payer: Keypair,
    pub network_id: u16,
}

impl NotaryClient {
    pub fn new_with_keypair(svm: Rc<RefCell<LiteSVM>>, payer: Keypair, network_id: u16) -> Self {
        svm.borrow_mut()
            .airdrop(&payer.pubkey(), LAMPORTS_PER_SOL)
            .unwrap();

        Self {
            svm,
            payer,
            network_id,
        }
    }

    pub fn new(svm: Rc<RefCell<LiteSVM>>, network_id: u16) -> Self {
        let keypair = Keypair::new();
        Self::new_with_keypair(svm, keypair, network_id)
    }

    pub fn get_client_pubkey(&self) -> Pubkey {
        self.payer.pubkey()
    }

    pub fn is_authorized_notary(
        &self,
        address: &Pubkey,
    ) -> Result<bool, anchor_client::ClientError> {
        let authorization_pda = get_authorization_pda_for_address(address);
        get_pda::<NotaryAuthorization>(self.svm.clone(), &authorization_pda)
            .map(|opt| opt.is_some() && opt.unwrap().notary == *address)
    }

    pub fn get_corda_tx_account(&self, tx_id: TxId, network_id: u16) -> Pubkey {
        let (output_account, _) = Pubkey::find_program_address(
            &[
                "corda_tx".as_bytes(),
                &tx_id.as_ref(),
                network_id.to_le_bytes().as_ref(),
            ],
            &corda_notary::ID,
        );
        output_account
    }

    pub fn commit(
        &self,
        tx_id: TxId,
        inputs_and_references: Vec<StateRefGroup>,
    ) -> TransactionResult {
        let authorized_notary_pda = get_authorization_pda_for_address(&self.get_client_pubkey());
        self.commit_with_custom_authorization_pda(
            tx_id,
            inputs_and_references,
            authorized_notary_pda,
        )
    }

    pub fn create_accounts_for_commit(
        &self,
        authorization_pda: Pubkey,
        output: Option<TxId>,
        inputs_and_references: Vec<TxId>,
    ) -> Vec<AccountMeta> {
        let mut accounts = vec![
            AccountMeta::new_readonly(self.payer.pubkey(), true),
            AccountMeta::new(authorization_pda, false),
            AccountMeta::new_readonly(anchor_client::solana_sdk::system_program::ID, false),
        ];

        if output.is_some() {
            accounts.push(AccountMeta::new(
                self.get_corda_tx_account(output.unwrap(), self.network_id),
                false,
            ))
        }

        let mut remaining_accounts = inputs_and_references
            .iter()
            .map(|txhash| {
                AccountMeta::new(self.get_corda_tx_account(*txhash, self.network_id), false)
            })
            .collect::<Vec<_>>();

        accounts.append(&mut remaining_accounts);
        accounts
    }

    pub fn commit_with_custom_authorization_pda(
        &self,
        tx_id: TxId,
        inputs_and_references: Vec<StateRefGroup>,
        authorization_pda: Pubkey,
    ) -> TransactionResult {
        let input_ids = inputs_and_references
            .iter()
            .map(|group| group.txhash)
            .collect::<Vec<_>>();

        let accounts = self.create_accounts_for_commit(authorization_pda, Some(tx_id), input_ids);

        self.commit_with_custom_accounts(tx_id, inputs_and_references, accounts)
    }

    pub fn commit_with_custom_accounts(
        &self,
        tx_id: TxId,
        inputs_and_references: Vec<StateRefGroup>,
        accounts: Vec<AccountMeta>,
    ) -> TransactionResult {
        let ix = Instruction {
            program_id: corda_notary::ID,
            accounts: accounts,
            data: corda_notary::instruction::Commit {
                tx_id,
                inputs_and_references,
            }
            .data(),
        };

        build_and_send_transaction(self.svm.clone(), self.payer.insecure_clone(), ix)
    }

    pub fn tx_id_is_committed(&self, tx_id: TxId) -> Result<bool, anchor_client::ClientError> {
        self.get_tx_account(tx_id).map(|opt| opt.is_some())
    }

    pub fn get_tx_account(
        &self,
        tx_id: TxId,
    ) -> Result<Option<CordaTxAccount>, anchor_client::ClientError> {
        let output_account_pubkey = self.get_corda_tx_account(tx_id, self.network_id);
        get_pda::<CordaTxAccount>(self.svm.clone(), &output_account_pubkey)
    }
}
