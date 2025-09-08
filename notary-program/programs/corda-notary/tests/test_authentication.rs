pub mod test_utils;

use anchor_client::solana_sdk::signer::Signer;
use corda_notary::{Administration, StateRefGroup};
use litesvm::LiteSVM;
use std::cell::RefCell;
use std::rc::Rc;
use test_utils::admin_client::AdminClient;
use test_utils::notary_client::NotaryClient;

use crate::test_utils::test_helpers::{
    deploy_notary_program, generate_random_corda_tx_id, get_admin_pda, get_authorization_pda_for_address, get_pda,
};
use rstest::*;

#[fixture]
fn svm() -> Rc<RefCell<LiteSVM>> {
    let mut svm = LiteSVM::new();
    deploy_notary_program(&mut svm);
    Rc::new(RefCell::new(svm))
}

#[rstest]
fn set_admin_on_initialize(svm: Rc<RefCell<LiteSVM>>) {
    let admin_client = AdminClient::new(svm.clone());
    // Initialize the notary program, setting the admin PDA
    admin_client.initialize_notary_program().expect("Failed to initialize notary program");

    let admin_pda = get_admin_pda();

    let result = get_pda::<Administration>(svm.clone(), &admin_pda).expect("Failed to check if admin PDA exists");
    assert_eq!(admin_client.payer.pubkey(), result.unwrap().admin, "Admin PDA should have the correct admin key");
}

#[rstest]
fn reinitalizing_should_fail(svm: Rc<RefCell<LiteSVM>>) {
    let admin_client = AdminClient::new(svm.clone());

    admin_client.initialize_notary_program().expect("Failed to initialize notary program");
    let network_id: u16 = 0;
    admin_client.create_network(network_id).expect("Failed to create network");

    let non_authorized_admin_client = AdminClient::new(svm.clone());
    non_authorized_admin_client.initialize_notary_program().expect_err("Reinitialization should fail");
}

#[rstest]
fn admin_should_be_able_to_authorize_notary(svm: Rc<RefCell<LiteSVM>>) {
    let admin_client = AdminClient::new(svm.clone());

    admin_client.initialize_notary_program().expect("Failed to initialize notary program");
    let network_id: u16 = 0;
    admin_client.create_network(network_id).expect("Failed to create network");

    let notary_client = NotaryClient::new(svm.clone(), network_id);

    admin_client.authorize_notary(&notary_client.payer.pubkey(), network_id).expect("Failed to authorize notary");

    assert!(notary_client.is_authorized_notary(&notary_client.payer.pubkey()).unwrap(), "Notary should be authorized");
}

#[rstest]
fn non_admin_should_not_be_able_to_authorize_notary(svm: Rc<RefCell<LiteSVM>>) {
    let admin_client = AdminClient::new(svm.clone());

    admin_client.initialize_notary_program().expect("Failed to initialize notary program");
    let network_id: u16 = 0;
    admin_client.create_network(network_id).expect("Failed to create network");

    let non_authorized_admin_client = AdminClient::new(svm.clone());

    let error =
        non_authorized_admin_client.authorize_notary(&non_authorized_admin_client.payer.pubkey(), 0).unwrap_err();

    // get the logs
    let error_message = error.meta.pretty_logs();
    assert!(error_message.contains("Unauthorized"), "Expected Unauthorized error, got: {}", error_message);
}

#[rstest]
fn admin_should_be_able_to_revoke_notary(svm: Rc<RefCell<LiteSVM>>) {
    let admin_client = AdminClient::new(svm.clone());

    admin_client.initialize_notary_program().expect("Failed to initialize notary program");
    let network_id: u16 = 0;
    admin_client.create_network(network_id).expect("Failed to create network");
    let notary_client = NotaryClient::new(svm, network_id);

    let notary_pubkey = notary_client.get_client_pubkey();
    admin_client.authorize_notary(&notary_pubkey, network_id).expect("Failed to authorize notary");
    assert!(notary_client.is_authorized_notary(&notary_pubkey).unwrap(), "Notary should be authorized");
    admin_client.revoke_notary(&notary_pubkey).expect("Failed to revoke notary");
    assert!(
        !notary_client.is_authorized_notary(&notary_pubkey).unwrap(),
        "Notary should not be authorized after revocation"
    );

    let tx_id = generate_random_corda_tx_id();
    notary_client.commit(tx_id, vec![]).expect_err("Revoked notary should not be able to commit");
}

#[rstest]
fn authorized_notary_should_be_able_to_commit(svm: Rc<RefCell<LiteSVM>>) {
    let admin_client = AdminClient::new(svm.clone());

    admin_client.initialize_notary_program().expect("Failed to initialize notary program");
    let network_id: u16 = 0;
    admin_client.create_network(network_id).expect("Failed to create network");

    // insecure cloning should be okay for testing purposes
    let notary_client = NotaryClient::new(svm, network_id);
    let notary_pubkey = notary_client.get_client_pubkey();

    admin_client.authorize_notary(&notary_pubkey, network_id).expect("Failed to authorize notary");

    assert!(
        notary_client.is_authorized_notary(&notary_client.get_client_pubkey()).unwrap(),
        "Notary should be authorized"
    );

    let tx_id = generate_random_corda_tx_id();

    notary_client.commit(tx_id, vec![]).unwrap();

    let was_commited = notary_client.tx_id_is_committed(tx_id);
    assert!(was_commited.unwrap(), "Transaction should be committed successfully");
}

#[rstest]
fn non_authorized_notary_should_not_be_able_to_commit(svm: Rc<RefCell<LiteSVM>>) {
    let admin_client = AdminClient::new(svm.clone());
    admin_client.initialize_notary_program().expect("Failed to initialize notary program");
    let network_id: u16 = 0;
    admin_client.create_network(network_id).expect("Failed to create network");

    let notary_client = NotaryClient::new(svm.clone(), network_id);

    let notary_pubkey = notary_client.get_client_pubkey();

    admin_client.authorize_notary(&notary_pubkey, 0).expect("Failed to authorize notary");

    let notary_pubkey = notary_client.get_client_pubkey();

    // insecure cloning should be okay for testing purposes
    let non_authorized_notary_client = NotaryClient::new(svm, network_id);

    let tx_id = generate_random_corda_tx_id();

    let authorized_notary_pda = get_authorization_pda_for_address(&notary_pubkey);

    let inputs: Vec<StateRefGroup> = vec![];

    let error = non_authorized_notary_client
        .commit_with_custom_authorization_pda(tx_id, inputs, authorized_notary_pda)
        .unwrap_err();

    // get the message from the error
    let error_message = error.meta.pretty_logs();
    // in this case the seeds constraint is violated, so we have a ConstraintsSeed error
    assert!(error_message.contains("ConstraintSeeds"), "Expected Unauthorized error, got: {}", error_message);
}
