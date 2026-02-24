use corda_notary::types::FlaggedU8;
use rstest::rstest;

#[rstest]
#[case(0, 0)]
#[case(1, 1)]
#[case(127, 127)]
#[case(128, 0)]
#[case(255, 127)]
fn flagged_u8_new(#[case] value: u8, #[case] expected_value: u8) {
    for flag in vec![true, false] {
        let fu8 = FlaggedU8::new(value, flag);
        assert_eq!(fu8.value(), expected_value);
        assert_eq!(fu8.flag(), flag);
    }
}
