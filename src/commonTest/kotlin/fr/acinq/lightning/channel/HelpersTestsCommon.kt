package fr.acinq.lightning.channel

import fr.acinq.bitcoin.*
import fr.acinq.bitcoin.Bitcoin.computeP2PkhAddress
import fr.acinq.bitcoin.Bitcoin.computeP2ShOfP2WpkhAddress
import fr.acinq.bitcoin.Bitcoin.computeP2WpkhAddress
import fr.acinq.lightning.Lightning.randomKey
import fr.acinq.lightning.channel.Helpers.Closing.checkClosingDustAmounts
import fr.acinq.lightning.tests.utils.LightningTestSuite
import fr.acinq.lightning.transactions.Transactions
import fr.acinq.lightning.utils.sat
import fr.acinq.secp256k1.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HelpersTestsCommon : LightningTestSuite() {

    @Test
    fun `compute address from pubkey script`() {
        val pub = PrivateKey(Hex.decode("0101010101010101010101010101010101010101010101010101010101010101")).publicKey()

        fun address(script: List<ScriptElt>, chainHash: ByteVector32) =
            Helpers.Closing.btcAddressFromScriptPubKey(ByteVector(Script.write(script)), chainHash)


        listOf(Block.LivenetGenesisBlock.hash, Block.TestnetGenesisBlock.hash, Block.RegtestGenesisBlock.hash).forEach {
            assertEquals(address(Script.pay2pkh(pub), it), computeP2PkhAddress(pub, it))
            assertEquals(address(Script.pay2wpkh(pub), it), computeP2WpkhAddress(pub, it))
            assertEquals(address(Script.pay2sh(Script.pay2wpkh(pub)), it), computeP2ShOfP2WpkhAddress(pub, it))
            // all these chain hashes are invalid
            assertEquals(address(Script.pay2pkh(pub), it.reversed()), null)
            assertEquals(address(Script.pay2wpkh(pub), it.reversed()), null)
            assertEquals(address(Script.pay2sh(Script.pay2wpkh(pub)), it.reversed()), null)
        }

        listOf(
            Triple("0014d0b19277b0f76c9512f26d77573fd31a8fd15fc7", Block.TestnetGenesisBlock.hash, "tb1q6zceyaas7akf2yhjd4m4w07nr28azh78gw79kk"),
            Triple("00203287047df2aa7aade3f394790a9c9d6f9235943f48a012e8a9f2c3300ca4f2d1", Block.TestnetGenesisBlock.hash, "tb1qx2rsgl0j4fa2mclnj3us48yad7frt9plfzsp969f7tpnqr9y7tgsyprxej"),
            Triple("76a914b17deefe2feab87fef7221cf806bb8ca61f00fa188ac", Block.TestnetGenesisBlock.hash, "mwhSm2SHhRhd19KZyaQLgJyAtCLnkbzWbf"),
            Triple("a914d3cf9d04f4ecc36df8207b300e46bc6775fc84c087", Block.TestnetGenesisBlock.hash, "2NCZBGzKadAnLv1ijAqhrKavMuqvxqu18yY"),
            Triple("00145cb882efd643b7d63ae133e4d5e88e10bd5a20d7", Block.LivenetGenesisBlock.hash, "bc1qtjug9m7kgwmavwhpx0jdt6ywzz745gxhxwyn8u"),
            Triple("00208c2865c87ffd33fc5d698c7df9cf2d0fb39d93103c637a06dea32c848ebc3e1d", Block.LivenetGenesisBlock.hash, "bc1q3s5xtjrll5elchtf337lnnedp7eemycs833h5pk75vkgfr4u8cws3ytg02"),
            Triple("76a914536ffa992491508dca0354e52f32a3a7a679a53a88ac", Block.LivenetGenesisBlock.hash, "18cBEMRxXHqzWWCxZNtU91F5sbUNKhL5PX"),
            Triple("a91481b9ac6a59b53927da7277b5ad5460d781b365d987", Block.LivenetGenesisBlock.hash, "3DWwX7NYjnav66qygrm4mBCpiByjammaWy"),
        ).forEach {
            assertEquals(
                Helpers.Closing.btcAddressFromScriptPubKey(
                    scriptPubKey = ByteVector(Hex.decode(it.first)),
                    chainHash = it.second
                ),
                it.third
            )
        }
    }

    @Test
    fun `check closing tx amounts above dust`() {
        val p2pkhBelowDust = listOf(TxOut(545.sat, Script.pay2pkh(randomKey().publicKey())))
        val p2shBelowDust = listOf(TxOut(539.sat, Script.pay2sh(Hex.decode("0000000000000000000000000000000000000000"))))
        val p2wpkhBelowDust = listOf(TxOut(293.sat, Script.pay2wpkh(randomKey().publicKey())))
        val p2wshBelowDust = listOf(TxOut(329.sat, Script.pay2wsh(Hex.decode("0000000000000000000000000000000000000000"))))
        val allOutputsAboveDust = listOf(
            TxOut(546.sat, Script.pay2pkh(randomKey().publicKey())),
            TxOut(540.sat, Script.pay2sh(Hex.decode("0000000000000000000000000000000000000000"))),
            TxOut(294.sat, Script.pay2wpkh(randomKey().publicKey())),
            TxOut(330.sat, Script.pay2wsh(Hex.decode("0000000000000000000000000000000000000000"))),
        )

        fun toClosingTx(txOut: List<TxOut>): Transactions.TransactionWithInputInfo.ClosingTx {
            val input = Transactions.InputInfo(OutPoint(ByteVector32.Zeroes, 0), TxOut(1000.sat, listOf()), listOf())
            return Transactions.TransactionWithInputInfo.ClosingTx(input, Transaction(2, listOf(), txOut, 0), null)
        }

        assertTrue(checkClosingDustAmounts(toClosingTx(allOutputsAboveDust)))
        assertFalse(checkClosingDustAmounts(toClosingTx(p2pkhBelowDust)))
        assertFalse(checkClosingDustAmounts(toClosingTx(p2shBelowDust)))
        assertFalse(checkClosingDustAmounts(toClosingTx(p2wpkhBelowDust)))
        assertFalse(checkClosingDustAmounts(toClosingTx(p2wshBelowDust)))
    }

}