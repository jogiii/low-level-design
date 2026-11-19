package lld.atm;

/** A card is in, waiting for the PIN. The customer can also cancel. */
final class CardInsertedState implements AtmState {
    static final CardInsertedState INSTANCE = new CardInsertedState();

    private CardInsertedState() {
    }

    @Override
    public String name() {
        return "CARD_INSERTED";
    }

    @Override
    public void enterPin(Atm atm, String pin) {
        try {
            AuthToken token = atm.bank().authenticate(atm.card(), pin);
            atm.startSession(token);
            atm.moveTo(AuthenticatedState.INSTANCE);
        } catch (AtmException e) {
            if (e.reason() == AtmException.Reason.CARD_BLOCKED) {
                atm.captureCard(); // too many wrong PINs: keep the card, back to IDLE
            }
            // INVALID_PIN: stay in this state so the customer can try again.
            throw e;
        }
    }

    @Override
    public void ejectCard(Atm atm) {
        atm.returnCard();
    }
}
