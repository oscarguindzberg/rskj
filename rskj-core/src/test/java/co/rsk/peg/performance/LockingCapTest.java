package co.rsk.peg.performance;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.util.MaxSizeHashMap;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class LockingCapTest extends BridgePerformanceTestCase {

    private static final ECKey authorizedLockingCapChanger = ECKey.fromPrivate(Hex.decode("da6a5451bfd74829307ec6d4a8c55174d4859169f162a8ed8fcba8f7636e77cc"));
    private static final ECKey unauthorizedLockingCapChanger = ECKey.fromPrivate(Hex.decode("f18ad1e830dd746ba350f4a43b3067e85634b5138a8515246441a453ec7460e9"));

    private ECKey sender;

    @BeforeClass
    public static void setupA() {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();
    }

    @Test
    public void getLockingCap() {
        sender = authorizedLockingCapChanger;
        ExecutionStats stats = new ExecutionStats("getLockingCap");
        executeTestCase((int executionIndex) -> Bridge.GET_LOCKING_CAP.encode(), "getLockingCap", 5000, stats);
        BridgePerformanceTest.addStats(stats);
    }

    @Test
    public void increaseLockingCap() {
        sender = authorizedLockingCapChanger;
        ExecutionStats stats = new ExecutionStats("increaseLockingCap");
        // Get initial locking cap and convert it to BTC
        Coin initialValue = BridgeRegTestConstants.getInstance().getInitialLockingCap().divide(Coin.COIN.getValue());
        executeTestCase(
                (int executionIndex) -> {
                    long value = Helper.randomCoin(initialValue, 1, 2).getValue();
                    return Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{ value });
                },
                "increaseLockingCap",
                5000,
                stats);
        BridgePerformanceTest.addStats(stats);
    }

    @Test
    public void increaseLockingCap_unauthorized() {
        sender = unauthorizedLockingCapChanger;
        ExecutionStats stats = new ExecutionStats("increaseLockingCap_unauthorized");
        // Get initial locking cap and convert it to BTC
        Coin initialValue = BridgeRegTestConstants.getInstance().getInitialLockingCap().divide(Coin.COIN.getValue());
        executeTestCase(
                (int executionIndex) -> {
                    long value = Helper.randomCoin(initialValue, 1, 2).getValue();
                    return Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{ value });
                },
                "increaseLockingCap",
                5000,
                stats);
        BridgePerformanceTest.addStats(stats);
    }

    private void executeTestCase(ABIEncoder abiEncoder, String name, int times, ExecutionStats stats) {
        executeAndAverage(
                name,
                times,
                abiEncoder,
                buildInitializer(),
                (int executionIndex) -> Helper.buildTx(sender),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private BridgeStorageProviderInitializer buildInitializer() {
        final int minBtcBlocks = 500;
        final int maxBtcBlocks = 1000;

        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            BtcBlockStore btcBlockStore = new RepositoryBtcBlockStoreWithCache(BridgeRegTestConstants.getInstance().getBtcParams(),
                    repository.startTracking(), new MaxSizeHashMap<>(RepositoryBtcBlockStoreWithCache.MAX_SIZE_MAP_STORED_BLOCKS, true),
                    PrecompiledContracts.BRIDGE_ADDR);
            Context btcContext = new Context(networkParameters);
            BtcBlockChain btcBlockChain;
            try {
                btcBlockChain = new BtcBlockChain(btcContext, btcBlockStore);
            } catch (BlockStoreException e) {
                throw new RuntimeException("Error initializing btc blockchain for tests");
            }

            int blocksToGenerate = Helper.randomInRange(minBtcBlocks, maxBtcBlocks);
            Helper.generateAndAddBlocks(btcBlockChain, blocksToGenerate);
        };
    }
}
