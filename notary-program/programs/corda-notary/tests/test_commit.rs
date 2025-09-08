pub mod test_utils;

use anchor_client::solana_sdk::{signature::Keypair, signer::Signer};
use corda_notary::{NotaryError, StateRefGroup};
use litesvm::LiteSVM;
use solana_sdk::transaction::TransactionError;
use std::cell::RefCell;
use std::rc::Rc;
use test_utils::notary_client::NotaryClient;

use crate::test_utils::admin_client::AdminClient;
use crate::test_utils::test_helpers::{
    deploy_notary_program, generate_random_corda_tx_id, get_authorization_pda_for_address, input_index, inputs_group,
    ref_index, references_group,
};
use corda_notary::CURRENT_ACCOUNT_SCHEMA_VERSION;
use rstest::*;
use solana_sdk::instruction::InstructionError;

#[fixture]
fn notary_client() -> NotaryClient {
    let mut svm = LiteSVM::new();
    deploy_notary_program(&mut svm);
    let svm_rc = Rc::new(RefCell::new(svm));
    let admin_client = AdminClient::new(svm_rc.clone());

    admin_client.initialize_notary_program().expect("Failed to initialize notary program");
    let network_id: u16 = 0;
    admin_client.create_network(network_id).expect("Failed to create network");

    let notary_key_pair = Keypair::new();

    admin_client.authorize_notary(&notary_key_pair.pubkey(), network_id).expect("Failed to authorize notary");

    // insecure cloning should be okay for testing purposes
    let notary_client = NotaryClient::new_with_keypair(svm_rc, notary_key_pair.insecure_clone(), network_id);

    assert!(
        notary_client.is_authorized_notary(&notary_client.get_client_pubkey()).unwrap(),
        "Notary should be authorized"
    );
    notary_client
}

#[rstest]
fn commit_issuance(notary_client: NotaryClient) {
    let tx_id = generate_random_corda_tx_id();

    let was_pre_commited = notary_client.tx_id_is_committed(tx_id);
    assert!(!was_pre_commited.unwrap(), "Transaction should not be committed before");

    notary_client.commit(tx_id, vec![]).unwrap();

    let was_commited = notary_client.tx_id_is_committed(tx_id);
    assert!(was_commited.unwrap(), "Transaction should be committed successfully");
}

#[rstest]
fn commit_creates_inputs_if_not_present(notary_client: NotaryClient) {
    let tx_id = generate_random_corda_tx_id();

    let input_id = generate_random_corda_tx_id();

    assert_tx!(notary_client, tx_id, false, "Transaction should not be committed before");
    assert_tx!(notary_client, input_id, false, "Input should not be known  before");

    notary_client.commit(tx_id, vec![inputs_group(input_id, vec![1, 5])]).unwrap();

    assert_tx!(notary_client, tx_id, true, "Transaction should be committed successfully");
    assert_tx!(notary_client, input_id, true, "Input should be known  after");
}

#[rstest]
fn cannot_double_spend_input(notary_client: NotaryClient) {
    let tx_id = generate_random_corda_tx_id();

    let input_id = generate_random_corda_tx_id();

    assert_tx!(notary_client, tx_id, false, "Transaction should not be committed before");
    assert_tx!(notary_client, input_id, false, "Input should not be known  before");

    notary_client.commit(tx_id, vec![inputs_group(input_id, vec![1, 5])]).unwrap();

    assert_tx!(notary_client, tx_id, true, "Transaction should be committed successfully");
    assert_tx!(notary_client, input_id, true, "Input should be known  after");

    let new_tx_id = generate_random_corda_tx_id();

    let result = notary_client.commit(new_tx_id, vec![inputs_group(input_id, vec![1, 3])]);

    assert_is_solana_error!(result, "Double spend should fail", NotaryError::Conflict);
    assert_tx!(notary_client, new_tx_id, false, "Double spend attempt should not create output");
}

#[rstest]
fn can_spend_other_output(notary_client: NotaryClient) {
    let tx_id = generate_random_corda_tx_id();

    let input_id = generate_random_corda_tx_id();

    assert_tx!(notary_client, tx_id, false, "Transaction should not be committed before");
    assert_tx!(notary_client, input_id, false, "Input should not be known  before");

    notary_client.commit(tx_id, vec![inputs_group(input_id, vec![1, 5])]).unwrap();

    assert_tx!(notary_client, tx_id, true, "Transaction should be committed successfully");
    assert_tx!(notary_client, input_id, true, "Input should be known  after");

    let new_tx_id = generate_random_corda_tx_id();

    notary_client.commit(new_tx_id, vec![inputs_group(input_id, vec![2, 3])]).unwrap();

    let tx_state = notary_client.get_tx_account(tx_id).unwrap().unwrap();
    let tx_state_unspent_bitset = tx_state.unspent_bitset;
    assert_eq!(format!("{tx_state_unspent_bitset:b}"), "11111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111");
    assert_eq!(CURRENT_ACCOUNT_SCHEMA_VERSION, tx_state.version);

    let input_state = notary_client.get_tx_account(input_id).unwrap().unwrap();
    let input_state_unspent_bitset = input_state.unspent_bitset;
    assert_eq!(format!("{input_state_unspent_bitset:b}"), "11111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111010001");
    assert_eq!(CURRENT_ACCOUNT_SCHEMA_VERSION, input_state.version);

    assert_tx!(notary_client, new_tx_id, true, "Output transaction should exist");
}

#[rstest]
fn can_spend_inputs_from_multiple_txs(notary_client: NotaryClient) {
    let tx_id = generate_random_corda_tx_id();

    let input_id1 = generate_random_corda_tx_id();
    let input_id2 = generate_random_corda_tx_id();

    assert_tx!(notary_client, tx_id, false, "Transaction should not be committed before");
    assert_tx!(notary_client, input_id1, false, "Input should not be known  before");
    assert_tx!(notary_client, input_id2, false, "Input should not be known  before");

    let inputs = vec![inputs_group(input_id1, vec![1, 5]), inputs_group(input_id2, vec![2])];

    notary_client.commit(tx_id, inputs).unwrap();

    assert_tx!(notary_client, tx_id, true, "Transaction should be committed successfully");
    assert_tx!(notary_client, input_id1, true, "Input1 should be known after");
    assert_tx!(notary_client, input_id2, true, "Input2 should be known after");
}

#[rstest]
fn can_spend_output_from_previous_output(notary_client: NotaryClient) {
    let tx_id = generate_random_corda_tx_id();

    let input_id = generate_random_corda_tx_id();

    assert_tx!(notary_client, tx_id, false, "Transaction should not be committed before");
    assert_tx!(notary_client, input_id, false, "Input should not be known  before");

    notary_client.commit(tx_id, vec![inputs_group(input_id, vec![1, 5])]).unwrap();

    assert_tx!(notary_client, tx_id, true, "Transaction should be committed successfully");
    assert_tx!(notary_client, input_id, true, "Input should be known  after");

    let new_tx_id = generate_random_corda_tx_id();

    notary_client.commit(new_tx_id, vec![inputs_group(tx_id, vec![2, 3])]).unwrap();

    assert_tx!(notary_client, new_tx_id, true, "Output transaction should exist");
}

#[rstest]
fn commit_fails_when_input_accounts_are_missing(notary_client: NotaryClient) {
    let tx_id = generate_random_corda_tx_id();

    let input_id = generate_random_corda_tx_id();
    let authorized_notary_pda = get_authorization_pda_for_address(&notary_client.get_client_pubkey());

    let accounts = notary_client.create_accounts_for_commit(authorized_notary_pda, Some(tx_id), vec![]);

    let result = notary_client.commit_with_custom_accounts(tx_id, vec![inputs_group(input_id, vec![0])], accounts);

    assert_is_solana_error!(result, "Missing input account should fail", NotaryError::InvalidAccounts);
    assert_tx!(notary_client, tx_id, false, "Transaction should not be committed after fauilure");
    assert_tx!(notary_client, input_id, false, "Input should not be present after fauilure");
}

#[rstest]
fn commit_fails_with_duplicate_input_accounts(notary_client: NotaryClient) {
    let tx_id = generate_random_corda_tx_id();

    let input_id = generate_random_corda_tx_id();
    let authorized_notary_pda = get_authorization_pda_for_address(&notary_client.get_client_pubkey());

    let accounts =
        notary_client.create_accounts_for_commit(authorized_notary_pda, Some(tx_id), vec![input_id, input_id]);

    let result = notary_client.commit_with_custom_accounts(tx_id, vec![inputs_group(input_id, vec![0])], accounts);

    assert_is_solana_error!(result, "Duplicate input account should lead to failure", NotaryError::InvalidAccounts);
    assert_tx!(notary_client, tx_id, false, "Transaction should not be committed after fauilure");
    assert_tx!(notary_client, input_id, false, "Input should not be present after fauilure");
}

#[rstest]
fn commit_fails_when_output_account_is_missing(notary_client: NotaryClient) {
    let tx_id = generate_random_corda_tx_id();

    let input_id = generate_random_corda_tx_id();
    let authorized_notary_pda = get_authorization_pda_for_address(&notary_client.get_client_pubkey());

    let accounts = notary_client.create_accounts_for_commit(authorized_notary_pda, None, vec![input_id]);

    let result = notary_client.commit_with_custom_accounts(tx_id, vec![inputs_group(input_id, vec![0])], accounts);

    assert_is_solana_error!(result, "Missing output account should lead to failure", NotaryError::InvalidAccounts);
    assert_tx!(notary_client, tx_id, false, "Transaction should not be committed after fauilure");
    assert_tx!(notary_client, input_id, false, "Input should not be present after fauilure");
}

#[rstest]
fn can_commit_without_consuming_reference_state(notary_client: NotaryClient) {
    let tx_id = generate_random_corda_tx_id();

    let input_id = generate_random_corda_tx_id();
    let reference_id = generate_random_corda_tx_id();

    assert_tx!(notary_client, tx_id, false, "Transaction should not be committed before");
    assert_tx!(notary_client, input_id, false, "Input should not be known  before");
    assert_tx!(notary_client, reference_id, false, "Reference state should not be known before");

    let inputs1 = vec![
        inputs_group(input_id, vec![1, 5]), // input
        references_group(reference_id, vec![1]),
    ]; // reference state

    notary_client.commit(tx_id, inputs1).unwrap();

    assert_tx!(notary_client, tx_id, true, "Transaction should be committed successfully");
    assert_tx!(notary_client, input_id, true, "Input should be known  after");
    assert_tx!(notary_client, reference_id, true, "Reference state should be known here");

    let new_tx_id = generate_random_corda_tx_id();

    let inputs2 = vec![
        inputs_group(tx_id, vec![2, 3]), // input from previous tx
        references_group(reference_id, vec![1]),
    ]; // same reference state

    notary_client.commit(new_tx_id, inputs2).unwrap();

    assert_tx!(notary_client, new_tx_id, true, "Output transaction should exist");
}

#[rstest]
fn cannot_commit_with_consumed_reference_state(notary_client: NotaryClient) {
    let tx_id = generate_random_corda_tx_id();
    let reference_id = generate_random_corda_tx_id();

    assert_tx!(notary_client, tx_id, false, "Transaction should not be committed before");
    assert_tx!(notary_client, reference_id, false, "Reference state should not be known before");

    let references = vec![inputs_group(reference_id, vec![1])]; // set up reference state as input

    //consume reference state
    notary_client.commit(tx_id, references).unwrap(); // consume reference state 1

    assert_tx!(notary_client, tx_id, true, "Transaction should be committed successfully");
    assert_tx!(notary_client, reference_id, true, "Reference state should be known here");

    let new_tx_id = generate_random_corda_tx_id();

    let test_input = vec![
        inputs_group(tx_id, vec![2, 3]),         // use output from previous tx ans input
        references_group(reference_id, vec![1]), // try to reuse previous reference state
    ];

    let result = notary_client.commit(new_tx_id, test_input);

    assert_is_solana_error!(result, "Consumed reference state should lead to failure", NotaryError::Conflict);
    assert_tx!(notary_client, new_tx_id, false, "Output transaction should not exist");
}

#[rstest]
fn can_use_inputs_and_reference_states_from_same_existing_tx(notary_client: NotaryClient) {
    let tx_id = generate_random_corda_tx_id();

    let input_id = generate_random_corda_tx_id();

    assert_tx!(notary_client, tx_id, false, "Transaction should not be committed before");
    assert_tx!(notary_client, input_id, false, "Input should not be known  before");

    let inputs1 = vec![inputs_group(input_id, vec![1, 5])];

    notary_client.commit(tx_id, inputs1).unwrap();

    assert_tx!(notary_client, tx_id, true, "Transaction should be committed successfully");
    assert_tx!(notary_client, input_id, true, "Input should be known  after");

    let new_tx_id = generate_random_corda_tx_id();
    let test_input = vec![StateRefGroup::new(tx_id, vec![input_index(2), input_index(3), ref_index(1)])]; // inputs and refs are different indices of same tx

    notary_client.commit(new_tx_id, test_input).unwrap();

    assert_tx!(notary_client, new_tx_id, true, "Output transaction should exist");
}

#[rstest]
fn can_use_inputs_and_reference_states_from_same_unkown_tx(notary_client: NotaryClient) {
    let tx_id = generate_random_corda_tx_id();
    let new_tx_id = generate_random_corda_tx_id();

    assert_tx!(notary_client, tx_id, false, "Input should not be known before");
    assert_tx!(notary_client, new_tx_id, false, "Transaction should not be committed before");

    let test_input = vec![StateRefGroup::new(tx_id, vec![input_index(2), input_index(3), ref_index(1)])]; // inputs and refs are different indices of same tx

    notary_client.commit(new_tx_id, test_input).unwrap();

    assert_tx!(notary_client, tx_id, true, "Transaction should be committed successfully");
    assert_tx!(notary_client, new_tx_id, true, "Output transaction should exist");
}

#[rstest]
fn cannot_use_same_state_as_input_and_reference_state_from_exising_tx(notary_client: NotaryClient) {
    let tx_id = generate_random_corda_tx_id();
    let input_id = generate_random_corda_tx_id();

    assert_tx!(notary_client, tx_id, false, "Transaction should not be committed before");
    assert_tx!(notary_client, input_id, false, "Input should not be known  before");

    let inputs1 = vec![inputs_group(input_id, vec![1, 5])];

    notary_client.commit(tx_id, inputs1).unwrap();

    assert_tx!(notary_client, tx_id, true, "Transaction should be committed successfully");
    assert_tx!(notary_client, input_id, true, "Input should be known  after");

    let new_tx_id = generate_random_corda_tx_id();
    let test_input = vec![StateRefGroup::new(tx_id, vec![input_index(1), ref_index(1)])]; // inputs and refs are same index of same tx

    let result = notary_client.commit(new_tx_id, test_input);

    assert_is_solana_error!(result, "Cannot use same state as input and reference!", NotaryError::Conflict);
    assert_tx!(notary_client, new_tx_id, false, "Output transaction should not exist");
}

#[rstest]
fn resubmitting_the_same_tx_twice_fails(notary_client: NotaryClient) {
    let tx_id = generate_random_corda_tx_id();
    let input_id = generate_random_corda_tx_id();

    assert_tx!(notary_client, tx_id, false, "Transaction should not be committed before");
    assert_tx!(notary_client, input_id, false, "Input should not be known  before");

    let inputs1 = vec![inputs_group(input_id, vec![1, 5])];

    notary_client.commit(tx_id, inputs1.clone()).unwrap();

    assert_tx!(notary_client, tx_id, true, "Transaction should be committed successfully");
    assert_tx!(notary_client, input_id, true, "Input should be known  after");

    let result = notary_client.commit(tx_id, inputs1.clone());

    assert!(result.is_err());
    assert_eq!(result.err().unwrap().err, TransactionError::AlreadyProcessed);

    let inputs2 = vec![inputs_group(input_id, vec![1, 4])];
    let result1 = notary_client.commit(tx_id, inputs2);

    assert_is_solana_error!(
        result1,
        "Should catch resubmission of tx with different inputs",
        NotaryError::Resubmission
    );
}
