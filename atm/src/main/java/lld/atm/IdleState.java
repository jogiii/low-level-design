package lld.atm;

import java.util.Objects;

/** No customer. The only thing you can do is insert a card. */
final class IdleState implements AtmState {
    static final IdleState INSTANCE = new IdleState();

    private IdleState() {
    }

    @Override
    public String name() {
        return "IDLE";
    }

    @Override
    public void insertCard(Atm atm, Card card) {
        Objects.requireNonNull(card, "card");
        // Throws for unknown, blocked or expired cards; the machine stays IDLE
        // and the card is pushed straight back out.
        atm.bank().verifyCard(card);
        atm.acceptCard(card);
        atm.moveTo(CardInsertedState.INSTANCE);
    }
}
