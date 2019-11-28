package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.pcc.bto.ToBase58Check;
import co.rsk.peg.utils.BridgeEventLogger;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BridgeSupportTest {

    @Test
    public void activations_is_set() {
        Block block = mock(Block.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP124)).thenReturn(true);

        BridgeSupport bridgeSupport = new BridgeSupport(
                mock(BridgeConstants.class),
                provider,
                mock(BridgeEventLogger.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                activations
        );

        Assert.assertTrue(bridgeSupport.getActivations().isActive(ConsensusRule.RSKIP124));
    }

    @Test(expected = NullPointerException.class)
    public void voteFeePerKbChange_nullFeeThrows() {
        Block block = mock(Block.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(null));
        when(tx.getSender())
                .thenReturn(new RskAddress(ByteUtil.leftPadBytes(new byte[]{0x43}, 20)));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        bridgeSupport.voteFeePerKbChange(tx, null);
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_unsuccessfulVote_unauthorized() {
        Block block = mock(Block.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(false);

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(-10));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_unsuccessfulVote_negativeFeePerKb() {
        Block block = mock(Block.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);
        when(authorizer.isAuthorized(tx.getSender()))
                .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
                .thenReturn(2);

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.NEGATIVE_SATOSHI), is(-1));
        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.ZERO), is(-1));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_unsuccessfulVote_excessiveFeePerKb() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        Block block = mock(Block.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);
        when(authorizer.isAuthorized(tx.getSender()))
                .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
                .thenReturn(2);
        when(constants.getMaxFeePerKb())
                .thenReturn(Coin.valueOf(MAX_FEE_PER_KB));

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.valueOf(MAX_FEE_PER_KB)), is(1));
        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.valueOf(MAX_FEE_PER_KB + 1)), is(-2));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_successfulVote() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        Block block = mock(Block.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);
        when(authorizer.isAuthorized(tx.getSender()))
                .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
                .thenReturn(2);
        when(constants.getMaxFeePerKb())
                .thenReturn(Coin.valueOf(MAX_FEE_PER_KB));

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(1));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_successfulVoteWithFeeChange() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        Block block = mock(Block.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);
        when(authorizer.isAuthorized(tx.getSender()))
                .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
                .thenReturn(1);
        when(constants.getMaxFeePerKb())
                .thenReturn(Coin.valueOf(MAX_FEE_PER_KB));

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(1));
        verify(provider).setFeePerKb(Coin.CENT);
    }

    @Test
    public void can_regenerate_p2wsh() {
        // THIS RAWTX WAS CREATED USING A MULTISIG WITH WITNESS WHICH DOESN'T HAVE THE EXACT SAME FORMAT. TALK TO SERGIO ABOUT IT
        byte[] rawTx = Hex.decode("02000000000101081411fd059c53153e92b0609bd575d386f1edec755a364983f81ee463e76f5e00000000232200209eeca52568753e58fc9c8ab5fb5135266f52cc32b2dbc8d6107d71d65b638fb0ffffffff0200e1f5050000000017a91481abb24df590fa2bb82b5a0da723dde7c967cdb5874873d7170000000017a914258d0b8a53446e7bdc4a94dbc8522ee64612d59987040047304402207acaab41b7ec78b3ff283be57a40fa49002bdee24094def2093241fe5f30032d02202eece64ab526779cd40f674db9e1b1bbacd5677b672a74fd87aad01e97f5d49801473044022023864ae868af21245bba3a5fc89adcb8bfb2c723b58880e427369d613cf3d0ba022060a6bfba2a473b3606604193e9eebc34dd2032a045cd3de093b9f887c4307b59016952210258953c6139e74677af8910ee1c8b81655432df80de34140eda89eb717d82513821027794c767d1168bfd13bbb747387d0b2fededcd3d0e9329bdd57e709ee38caed921021435a1f41033ac3b7f4bc38751b1a13f3cae2ba253e90b109e54205e8ecab28e53ae00000000");
        BtcTransaction tx = new BtcTransaction(BridgeMainNetConstants.getInstance().getBtcParams(), rawTx);

        Script script = new Script(tx.getWitness(0).getPush(3));
        String redeemScript = Hex.toHexString(script.getProgram());
        assertEquals("52210258953c6139e74677af8910ee1c8b81655432df80de34140eda89eb717d82513821027794c767d1168bfd13bbb747387d0b2fededcd3d0e9329bdd57e709ee38caed921021435a1f41033ac3b7f4bc38751b1a13f3cae2ba253e90b109e54205e8ecab28e53ae",
                redeemScript);
        byte[] hash160;
        hash160 = HashUtil.ripemd160(Sha256Hash.hash(script.getProgram()));                     // 2N47zGunGA6ZbPE6CpDQEBmh2uhazrNsFNA
        Address addr = new Address(BridgeRegTestConstants.getInstance().getBtcParams(), 196, hash160);
        assertEquals("2NGTsn7bWrHZaLcjpcxxyTr1DN7ysb6t577", addr.toBase58());
    }

    @Test
    public void can_regenerate_p2sh() {
        byte[] rawTx = Hex.decode("020000000179bb0822e38291ddd1c772c6aaa0113cea731af1c4d20042128d66d96a2b261700000000fc0047304402204ed5f0b4dfea4a544c9d5db1d787471ae56e635294cac46309d2bdfeb9f4c37902205458260d880613e3505650cefa5a9b1f94942ec72a6f2bafec2d7d4c862cd6aa01473044022005d87813c9e80ece6b87a5a6cffef14e2480c7d89d9309dbc08f9e7ba290a8ed02207df99423e92f6ef391f6ac6193ff9217c403ec44a2c5d898062725dcfe1fd1ab014c6952210258953c6139e74677af8910ee1c8b81655432df80de34140eda89eb717d82513821027794c767d1168bfd13bbb747387d0b2fededcd3d0e9329bdd57e709ee38caed921021435a1f41033ac3b7f4bc38751b1a13f3cae2ba253e90b109e54205e8ecab28e53aeffffffff022c67d717000000001976a91457df3606bd14903388d537879cd90fd58897056d88ac00e1f5050000000017a91481abb24df590fa2bb82b5a0da723dde7c967cdb58700000000");
        BtcTransaction tx = new BtcTransaction(BridgeMainNetConstants.getInstance().getBtcParams(), rawTx);

        Script script = tx.getInput(0).getScriptSig();
        String redeemScript = Hex.toHexString(script.getChunks().get(3).data);
        assertEquals("52210258953c6139e74677af8910ee1c8b81655432df80de34140eda89eb717d82513821027794c767d1168bfd13bbb747387d0b2fededcd3d0e9329bdd57e709ee38caed921021435a1f41033ac3b7f4bc38751b1a13f3cae2ba253e90b109e54205e8ecab28e53ae",
                redeemScript);
        byte[] hash160;
        hash160 = HashUtil.ripemd160(Sha256Hash.hash(script.getChunks().get(3).data));
        Address addr = new Address(BridgeRegTestConstants.getInstance().getBtcParams(), 196, hash160);
        assertEquals("2N47zGunGA6ZbPE6CpDQEBmh2uhazrNsFNA", addr.toBase58());
    }

    @Test
    public void can_regenerate_p2wpkh() {
        byte[] rawTx = Hex.decode("02000000000101fb7fa362006a70c08cbebbca6078cece058af1022ce489b5ef499da06f5381b90000000017160014242f68f1536faff34e908a75d024b6b321970538ffffffff02e0d3f505000000001976a9147d61bb191ab422ce0d9946bb381d596b29c5216388ac00e1f5050000000017a91481abb24df590fa2bb82b5a0da723dde7c967cdb587024730440220612ffa1d16c8072743ee3088ac8c721a861e0b8eeb4a79f651235a8c8af3824f0220758bdde5bb66df0fe0049ae55e5d2e5cc44059f972312980c3455bdbf959b60001210363934b85670b40f678e8250632646dcd634ce5e4910f86e7b5e35982224e841500000000");
        BtcTransaction tx = new BtcTransaction(BridgeMainNetConstants.getInstance().getBtcParams(), rawTx);

        byte[] pubkey = tx.getWitness(0).getPush(1);
        assertEquals("0363934b85670b40f678e8250632646dcd634ce5e4910f86e7b5e35982224e8415", Hex.toHexString(pubkey));
        byte[] hash160;
        hash160 = HashUtil.ripemd160(Sha256Hash.hash(pubkey));
        Address addr = new Address(BridgeRegTestConstants.getInstance().getBtcParams(), 196, hash160);
        assertEquals("2N51d9KgpWLSGxSPzto4CNWhvgtZdFn472S", addr.toBase58());
    }

    @Test
    public void can_regenerate_p2pk() {
        byte[] rawTx = Hex.decode("0100000001c280594cc4f30fcf407b3dbaee27f155d29e9516b7291045f67b412faec28068000000004948304502210096bf18f3fce1f3ac800f98c12e655240cbdb7127e046a3c1bd72e16d3e16c3fd022030e897e2eea143eca4e87b28ba104625eb316f1a487a72c5aecc93b824c8c74f01ffffffff017b240200000000001976a9143b08b0003ef9b4f37f8d18a54f9db8ec67f8cbf088ac00000000");
        BtcTransaction tx = new BtcTransaction(BridgeMainNetConstants.getInstance().getBtcParams(), rawTx);

        Script script = tx.getInput(0).getScriptSig();
        byte[] pubkey = script.getChunks().get(0).data;
        assertEquals("030e7061b9fb18571cf2441b2a7ee2419933ddaa423bc178672cd11e87911616d1", Hex.toHexString(pubkey));
        byte[] hash160;
        hash160 = HashUtil.ripemd160(Sha256Hash.hash(pubkey));
        Address addr = new Address(BridgeRegTestConstants.getInstance().getBtcParams(), 196, hash160);
        assertEquals("1CjRf1RMrTwyGoBHDbqzXERhVFkPyowt8i", addr.toBase58());
    }

}
