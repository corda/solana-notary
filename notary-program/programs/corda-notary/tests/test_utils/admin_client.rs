use anchor_client::solana_sdk::instruction::Instruction;
use anchor_client::solana_sdk::native_token::LAMPORTS_PER_SOL;
use anchor_client::solana_sdk::signature::Keypair;
use anchor_client::solana_sdk::signer::Signer;
use anchor_lang::prelude::Pubkey;
use anchor_lang::InstructionData;
use anchor_lang::ToAccountMetas;
use litesvm::types::TransactionResult;
use litesvm::LiteSVM;
use std::cell::RefCell;
use std::rc::Rc;

use crate::test_utils::test_helpers::build_and_send_transaction;
use crate::test_utils::test_helpers::get_admin_pda;
use crate::test_utils::test_helpers::get_authorization_pda_for_address;
use crate::test_utils::test_helpers::get_network_pda;

pub struct AdminClient {
    pub svm: Rc<RefCell<LiteSVM>>,
    pub payer: Keypair,
}

impl AdminClient {
    pub fn new_with_keypair(svm: Rc<RefCell<LiteSVM>>, payer: Keypair) -> Self {
        svm.borrow_mut().airdrop(&payer.pubkey(), LAMPORTS_PER_SOL).unwrap();

        Self {
            svm,
            payer,
        }
    }

    pub fn new(svm: Rc<RefCell<LiteSVM>>) -> Self {
        let keypair = Keypair::new();
        Self::new_with_keypair(svm, keypair)
    }

    pub fn initialize_notary_program(&self) -> TransactionResult {
        let ix = Instruction {
            program_id: corda_notary::ID,
            accounts: corda_notary::accounts::Initialize {
                admin: self.payer.pubkey(),
                administration: get_admin_pda(),
                system_program: anchor_client::solana_sdk::system_program::ID,
            }
            .to_account_metas(None),
            data: corda_notary::instruction::Initialize {}.data(),
        };

        build_and_send_transaction(self.svm.clone(), self.payer.insecure_clone(), ix)
    }

    pub fn create_network(&self, network_id: u16) -> TransactionResult {
        let ix = Instruction {
            program_id: corda_notary::ID,
            accounts: corda_notary::accounts::CreateNetwork {
                admin: self.payer.pubkey(),
                administration: get_admin_pda(),
                network: get_network_pda(network_id),
                system_program: anchor_client::solana_sdk::system_program::ID,
            }
            .to_account_metas(None),
            data: corda_notary::instruction::CreateNetwork {}.data(),
        };

        build_and_send_transaction(self.svm.clone(), self.payer.insecure_clone(), ix)
    }

    pub fn authorize_notary(&self, address_to_authorize: &Pubkey, network_id: u16) -> TransactionResult {
        let authorization_pda = get_authorization_pda_for_address(address_to_authorize);

        let ix = Instruction {
            program_id: corda_notary::ID,
            accounts: corda_notary::accounts::AuthorizeNotary {
                admin: self.payer.pubkey(),
                administration: get_admin_pda(),
                authorization: authorization_pda,
                system_program: anchor_client::solana_sdk::system_program::ID,
                network: get_network_pda(network_id),
            }
            .to_account_metas(None),
            data: corda_notary::instruction::AuthorizeNotary {
                address_to_authorize: *address_to_authorize,
            }
            .data(),
        };

        build_and_send_transaction(self.svm.clone(), self.payer.insecure_clone(), ix)
    }

    pub fn revoke_notary(&self, address_to_revoke: &Pubkey) -> TransactionResult {
        let authorization_pda = get_authorization_pda_for_address(address_to_revoke);

        let ix = Instruction {
            program_id: corda_notary::ID,
            accounts: corda_notary::accounts::RevokeNotary {
                admin: self.payer.pubkey(),
                administration: get_admin_pda(),
                authorization: authorization_pda,
            }
            .to_account_metas(None),
            data: corda_notary::instruction::RevokeNotary {
                address_to_revoke: *address_to_revoke,
            }
            .data(),
        };

        build_and_send_transaction(self.svm.clone(), self.payer.insecure_clone(), ix)
    }
}
