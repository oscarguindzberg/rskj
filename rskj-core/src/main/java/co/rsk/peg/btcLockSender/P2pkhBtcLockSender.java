package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.script.Script;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeUtils;
import java.util.Optional;

public class P2pkhBtcLockSender extends BtcLockSender {

    public P2pkhBtcLockSender(BtcTransaction btcTx) throws BtcLockSenderParseException {
        super(btcTx);
        this.setType(BtcLockSender.TxType.P2PKH);
    }

    @Override
    protected void parse (BtcTransaction btcTx) throws BtcLockSenderParseException {
        if (btcTx == null) {
            throw new BtcLockSenderParseException();
        }

        Optional<Script> scriptSig = BridgeUtils.getFirstInputScriptSig(btcTx);
        if (!scriptSig.isPresent()) {
            throw new BtcLockSenderParseException();
        }

        byte[] data = scriptSig.get().getChunks().get(1).data;

        //Looking for btcAddress
        BtcECKey senderBtcKey = BtcECKey.fromPublicOnly(data);
        this.btcAddress = new Address(btcTx.getParams(), senderBtcKey.getPubKeyHash());

        //Looking for rskAddress
        org.ethereum.crypto.ECKey key = org.ethereum.crypto.ECKey.fromPublicOnly(data);
        this.rskAddress = new RskAddress(key.getAddress());

    }

    private final void setType(BtcLockSender.TxType trtype) {
        this.transactionType = trtype;
    }

}
