use anchor_lang::prelude::*;
use std::fmt;
use std::fmt::{Debug, Display, Formatter};
use std::ops::Deref;

/// u8 representing a 7-bit value and a boolean flag in the MSB.
#[derive(AnchorSerialize, AnchorDeserialize, Default, Clone, Copy, Eq, PartialEq, Hash)]
pub struct FlaggedU8(u8);

impl FlaggedU8 {
    const FLAG_MASK: u8 = 0b1000_0000;
    const VALUE_MASK: u8 = 0b0111_1111;

    /// Wrap the given u8 and bool flag into a single u8 [FlaggedU8].
    ///
    /// NOTE: The MSB of the value is overwritten by the flag.
    pub fn new(value: u8, flag: bool) -> Self {
        let masked_value = value & Self::VALUE_MASK;
        let byte = if flag {
            masked_value | Self::FLAG_MASK
        } else {
            masked_value
        };
        Self(byte)
    }

    pub fn value(&self) -> u8 {
        self.0 & Self::VALUE_MASK
    }

    pub fn flag(&self) -> bool {
        (self.0 & Self::FLAG_MASK) != 0
    }
}

impl Deref for FlaggedU8 {
    type Target = u8;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl<T> AsRef<T> for FlaggedU8
where
    T: ?Sized,
    <FlaggedU8 as Deref>::Target: AsRef<T>,
{
    fn as_ref(&self) -> &T {
        self.deref().as_ref()
    }
}

impl Debug for FlaggedU8 {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        write!(f, "FlaggedU8({}:{})", self.value(), self.flag())
    }
}

impl Display for FlaggedU8 {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        write!(f, "{}:{}", self.value(), self.flag())
    }
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, Eq, PartialEq, PartialOrd, Hash)]
pub struct TxId(pub [u8; 32]);

impl TxId {
    pub fn state_ref(self, index: u8) -> StateRef {
        StateRef::new(self, index)
    }
}

impl Deref for TxId {
    type Target = [u8; 32];

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl<T> AsRef<T> for TxId
where
    T: ?Sized,
    <TxId as Deref>::Target: AsRef<T>,
{
    fn as_ref(&self) -> &T {
        self.deref().as_ref()
    }
}

impl Debug for TxId {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        write!(f, "{}", hex::encode_upper(self.0))
    }
}

impl Display for TxId {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        write!(f, "{}", hex::encode_upper(self.0))
    }
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Debug, Eq, PartialEq, Hash)]
pub struct StateRef {
    pub txhash: TxId,
    pub index: u8,
}

impl Display for StateRef {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        write!(f, "{}({})", self.txhash, self.index)
    }
}

impl StateRef {
    pub fn new(txhash: TxId, index: u8) -> StateRef {
        Self { txhash, index }
    }
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Debug)]
pub struct StateRefGroup {
    pub txhash: TxId,
    // TODO Encode this using u8 length prefix
    pub flagged_indices: Vec<FlaggedU8>,
}

impl StateRefGroup {
    pub fn new(txhash: TxId, flagged_indices: Vec<FlaggedU8>) -> StateRefGroup {
        Self {
            txhash,
            flagged_indices,
        }
    }
}
