pub mod test_utils;

use anchor_client::solana_sdk::{signature::Keypair, signer::Signer};
use litesvm::LiteSVM;
use std::cell::RefCell;
use std::rc::Rc;
use test_utils::notary_client::NotaryClient;

use crate::test_utils::admin_client::AdminClient;
use crate::test_utils::test_helpers::{
    deploy_notary_program, generate_random_corda_tx_id, get_admin_pda,
    get_authorization_pda_for_address, get_network_pda, get_pda, inputs_group,
};
use corda_notary::{Administration, Network, NotaryAuthorization, ACCOUNT_SCHEMA_VERSION};
use rstest::*;

#[fixture]
fn svm() -> Rc<RefCell<LiteSVM>> {
    let mut svm = LiteSVM::new();
    deploy_notary_program(&mut svm);
    Rc::new(RefCell::new(svm))
}

fn initialize_and_create_admin_client(svm: Rc<RefCell<LiteSVM>>) -> AdminClient {
    let admin_client = AdminClient::new(svm.clone());

    admin_client
        .initialize_notary_program()
        .expect("Failed to initialize notary program");
    admin_client
}

#[rstest]
fn create_network_increments_the_next_network(svm: Rc<RefCell<LiteSVM>>) {
    let admin_client = initialize_and_create_admin_client(svm.clone());
    let network_id: u16 = 0;
    admin_client
        .create_network(network_id)
        .expect("Failed to create network 0");
    let network =
        get_pda::<Network>(admin_client.svm.clone(), &get_network_pda(network_id)).unwrap();
    assert_eq!(ACCOUNT_SCHEMA_VERSION, network.unwrap().version);
    let administration =
        get_pda::<Administration>(admin_client.svm.clone(), &get_admin_pda()).unwrap();
    assert_eq!(
        1,
        administration.unwrap().next_network_id,
        "Next network ID should be 1 after creating the first network"
    );
}

#[rstest]
fn notary_authorization_assigns_network_id_correctly(svm: Rc<RefCell<LiteSVM>>) {
    let admin_client = initialize_and_create_admin_client(svm.clone());
    admin_client
        .create_network(0)
        .expect("Failed to create network 0");
    admin_client
        .create_network(1)
        .expect("Failed to create network 1");
    let notary_network_0_keypair = Keypair::new();
    let notary_network_1_keypair = Keypair::new();
    admin_client
        .authorize_notary(&notary_network_0_keypair.pubkey(), 0)
        .expect("Failed to authorize notary for network 0");
    admin_client
        .authorize_notary(&notary_network_1_keypair.pubkey(), 1)
        .expect("Failed to authorize notary for network 1");
    let authorization_notary_network_0_address =
        get_authorization_pda_for_address(&notary_network_0_keypair.pubkey());
    let authorization_notary_network_0 = get_pda::<NotaryAuthorization>(
        admin_client.svm.clone(),
        &authorization_notary_network_0_address,
    )
    .unwrap()
    .unwrap();
    assert_eq!(0, authorization_notary_network_0.network_id);
    assert_eq!(
        ACCOUNT_SCHEMA_VERSION,
        authorization_notary_network_0.version
    );
    let authorization_notary_network_1_address =
        get_authorization_pda_for_address(&notary_network_1_keypair.pubkey());
    let authorization_notary_network_1 = get_pda::<NotaryAuthorization>(
        admin_client.svm.clone(),
        &authorization_notary_network_1_address,
    )
    .unwrap()
    .unwrap();
    assert_eq!(1, authorization_notary_network_1.network_id);
}

#[rstest]
fn state_processing_is_correct_across_networks(svm: Rc<RefCell<LiteSVM>>) {
    let admin_client = initialize_and_create_admin_client(svm.clone());
    let network_0_id: u16 = 0;
    admin_client
        .create_network(network_0_id)
        .expect("Failed to create network 0");
    let network_1_id: u16 = 1;
    admin_client
        .create_network(network_1_id)
        .expect("Failed to create network 1");

    //Create notary A for network 0

    let notary_a_network_0_key_pair = Keypair::new();

    admin_client
        .authorize_notary(&notary_a_network_0_key_pair.pubkey(), network_0_id)
        .expect("Failed to authorize notary A for network 0");

    let notary_a_network_0_client = NotaryClient::new_with_keypair(
        svm.clone(),
        notary_a_network_0_key_pair.insecure_clone(),
        network_0_id,
    );

    //Create notary B for network 0

    let notary_b_network_0_key_pair = Keypair::new();
    admin_client
        .authorize_notary(&notary_b_network_0_key_pair.pubkey(), network_0_id)
        .expect("Failed to authorize notary B for network 0");

    let notary_b_network_0_client = NotaryClient::new_with_keypair(
        svm.clone(),
        notary_b_network_0_key_pair.insecure_clone(),
        network_0_id,
    );

    //Create notary C for network 1

    let notary_c_network_1_key_pair = Keypair::new();
    admin_client
        .authorize_notary(&notary_c_network_1_key_pair.pubkey(), network_1_id)
        .expect("Failed to authorize notary C for network 1");

    let notary_c_network_1_client = NotaryClient::new_with_keypair(
        svm.clone(),
        notary_c_network_1_key_pair.insecure_clone(),
        network_1_id,
    );

    // State issued is network 0 is visible to notary A and B in network 0, but not to notary C in network 1

    let issue_tx_id = generate_random_corda_tx_id();

    let was_pre_commited_pov_notary_a = notary_a_network_0_client.tx_id_is_committed(issue_tx_id);
    assert!(
        !was_pre_commited_pov_notary_a.unwrap(),
        "Transaction should not be committed before from the perspective of notary A"
    );
    let was_pre_commited_pov_notary_b = notary_b_network_0_client.tx_id_is_committed(issue_tx_id);
    assert!(
        !was_pre_commited_pov_notary_b.unwrap(),
        "Transaction should not be committed before from the perspective of notary B"
    );
    let was_pre_commited_pov_notary_c = notary_c_network_1_client.tx_id_is_committed(issue_tx_id);
    assert!(
        !was_pre_commited_pov_notary_c.unwrap(),
        "Transaction should not be committed before from the perspective of notary C"
    );

    notary_a_network_0_client
        .commit(issue_tx_id, vec![])
        .unwrap();

    let was_commited_notary_a = notary_a_network_0_client.tx_id_is_committed(issue_tx_id);
    assert!(
        was_commited_notary_a.unwrap(),
        "Transaction should be committed successfully from the the perspective of notary A"
    );
    let was_commited_notary_b = notary_b_network_0_client.tx_id_is_committed(issue_tx_id);
    assert!(
        was_commited_notary_b.unwrap(),
        "Transaction should be committed successfully from the the perspective of notary B"
    );
    let was_commited_notary_c = notary_c_network_1_client.tx_id_is_committed(issue_tx_id);
    // Notary C should not see the transaction committed in network 0
    assert!(
        !was_commited_notary_c.unwrap(),
        "Transaction should be committed successfully from the the perspective of notary C"
    );

    // Input spent in network 0 is visible to notary A and B in network 0, but not to notary C in network 1

    let with_inputs_tx_id = generate_random_corda_tx_id();

    notary_a_network_0_client
        .commit(
            with_inputs_tx_id,
            vec![inputs_group(issue_tx_id, vec![1, 5])],
        )
        .unwrap();

    assert_tx!(
        notary_a_network_0_client,
        with_inputs_tx_id,
        true,
        "Transaction should be committed successfully from the perspective of notary A"
    );
    assert_tx!(
        notary_b_network_0_client,
        with_inputs_tx_id,
        true,
        "Transaction should be committed successfully from the perspective of notary B"
    );
    // Notary C should not see the transaction with inputs committed in network 0
    assert_tx!(
        notary_c_network_1_client,
        with_inputs_tx_id,
        false,
        "Transaction should not be visible from the perspective of notary C"
    );
}
