// using a macro as that gets inlined and thus fails at the call site.
#[macro_export]
macro_rules! assert_tx {
    ($notary_client:expr, $tx_id:expr, $expected:expr, $error_message:expr) => {
        let tx_exists = $notary_client.tx_id_is_committed($tx_id);
        assert!(tx_exists.unwrap() == $expected, $error_message);
    };
}

#[macro_export]
macro_rules! assert_is_solana_error {
    ($result:expr, $message:expr, $expected_error:expr) => {
        assert!($result.is_err(), $message);
        let err = $result.unwrap_err();
        assert_eq!(
            err.err,
            TransactionError::InstructionError(0, InstructionError::Custom($expected_error.into())),
            "Expected {}, got {}",
            $expected_error,
            err.meta.pretty_logs()
        );
    };
}
