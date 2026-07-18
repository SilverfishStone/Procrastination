package com.workgame.controller;

import com.workgame.model.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Game controller – enforces all rules and mutates GameState.
 * The UI calls methods here; this class fires callbacks back to the UI
 * when it needs a player decision (target selection, etc.).
 */
public class GameController {

    private final GameState state;

    // Callbacks the UI must provide for interactive choices
    private Consumer<TargetRequest> targetRequestCallback;
    private Runnable                updateUICallback;
    private Consumer<String>        logCallback;
    /** Fired when a card is successfully drawn (not alert); carries the drawn card for animation. */
    private Consumer<Card>          drawCallback;
    /**
     * Fired when the current player plays a card to their own zone.
     * Carries: (card, destinationPlayer=currentPlayer).
     * The controller has already mutated state; UI animates then calls updateUI itself.
     */
    private java.util.function.BiConsumer<Card, Player> playCardCallback;
    /**
     * Fired when a weapon card is sent to a target player's zone.
     * Carries: (card, targetPlayer).
     */
    private java.util.function.BiConsumer<Card, Player> weaponSentCallback;
    /**
     * Fired when an alert card is drawn.  The UI shows the animation, then calls
     * the provided Runnable to let the controller finish resolving it.
     */
    private java.util.function.BiConsumer<Card, Runnable> alertCallback;
    /**
     * Fired when a harmful weapon hits a target who has an Excused card.
     * Carries (weapon, target, Runnable resolveAnyway).
     * If the target plays Excused, the weapon is blocked; otherwise resolveAnyway.run() is called.
     */
    private java.util.function.BiConsumer<Card, Runnable> defendCallback;
    /**
     * Fired when a mass discard is about to happen (Newbie, Performance Review, etc.).
     * Carries: (cardsToDiscard, onAnimationDone).
     * The UI animates each card flying to the discard pile, then calls onAnimationDone.
     */
    private java.util.function.BiConsumer<List<Card>, Runnable> bulkDiscardCallback;

    private com.workgame.controller.CpuPlayer cpuPlayer;
    public void setCpuPlayer(com.workgame.controller.CpuPlayer cp) { this.cpuPlayer = cp; }

    public GameController(GameState state) {
        this.state = state;
    }

    // ── Callback wiring ──────────────────────────────────────────────────────

    public void setTargetRequestCallback(Consumer<TargetRequest> cb) { this.targetRequestCallback = cb; }
    public void setUpdateUICallback(Runnable cb)                     { this.updateUICallback = cb; }
    public void setLogCallback(Consumer<String> cb)                   { this.logCallback = cb; }
    public void setDrawCallback(Consumer<Card> cb)                    { this.drawCallback = cb; }
    public void setPlayCardCallback(java.util.function.BiConsumer<Card, Player> cb) { this.playCardCallback = cb; }
    public void setWeaponSentCallback(java.util.function.BiConsumer<Card, Player> cb) { this.weaponSentCallback = cb; }
    public void setAlertCallback(java.util.function.BiConsumer<Card, Runnable> cb) { this.alertCallback = cb; }
    public void setBulkDiscardCallback(java.util.function.BiConsumer<List<Card>, Runnable> cb) { this.bulkDiscardCallback = cb; }
    public void setDefendCallback(java.util.function.BiConsumer<Card, Runnable> cb) { this.defendCallback = cb; }

    // ── Setup ────────────────────────────────────────────────────────────────

    /** Deal initial hands (5 action cards each) and set phase to PLAYER_TURN. */
    public void startGame() {
        for (Player p : state.getPlayers()) {
            for (int i = 0; i < Player.MAX_HAND_SIZE; i++) {
                Card c = state.drawCard(true);
                if (c != null) p.addToHand(c);
            }
        }
        state.setPhase(GameState.Phase.PLAYER_TURN);
        log("Game started! " + state.getCurrentPlayer().getName() + "'s turn.");
        updateUI();
    }

    // ── Turn actions ─────────────────────────────────────────────────────────

    /** Player draws a card from the draw pile. */
    public void drawCard() {
        Player p = state.getCurrentPlayer();
        if (state.isCardDrawnThisTurn()) { log("You already drew this turn."); return; }

        Card card = state.drawCard(false);
        if (card == null) { log("Draw pile is empty!"); return; }

        state.setCardDrawnThisTurn(true);

        // Alert cards must be played immediately
        if (card.getType() == CardType.ALERT) {
            log(p.getName() + " drew an Alert card: " + card.getName() + "!");
            state.getStats().recordDraw(card.getName());
            state.getStats().recordPlay(p.getName(), card.getName(), false, false, true);
            if (alertCallback != null) {
                // UI animates first, then calls the runnable to finish resolution
                alertCallback.accept(card, () -> {
                    resolveAlertCard(card, p);
                    state.setActionTakenThisTurn(true);
                    endTurn();
                });
            } else {
                resolveAlertCard(card, p);
                state.setActionTakenThisTurn(true);
                endTurn();
            }
            return;
        }

        p.addToHand(card);
        state.getStats().recordDraw(card.getName());
        log(p.getName() + " drew: " + card.getName());
        if (drawCallback != null) drawCallback.accept(card);
        else updateUI();
    }

    /** Player plays a card from their hand to their play area. */
    public void playCardFromHand(Card card) {
        Player p = state.getCurrentPlayer();
        if (!p.getHand().contains(card)) { log("Card not in hand."); return; }
        if (state.isActionTakenThisTurn()) { log("Already took an action this turn."); return; }
        if (!state.isCardDrawnThisTurn()) { log("Draw a card first."); return; }

        switch (card.getType()) {
            case PLAY -> playNormalCard(p, card);
            case HELPER -> {
                log("Helper cards can be played at any time — use the 'Use Helper' button.");
                return;
            }
            case ALERT -> {
                log("Alert cards are played automatically when drawn.");
                return;
            }
            case WEAPON_IMMEDIATE, WEAPON_PLAY, WEAPON_ROLLING -> {
                log("Select a target player to send this weapon to.");
                requestTarget(TargetRequest.Type.SEND_WEAPON, card, null, null);
                return;
            }
            default -> { log("Unknown card type."); return; }
        }
    }

    private void playNormalCard(Player p, Card card) {
        if (!p.canPlayCard()) {
            log("Play area full (max 3 cards). Discard a card first.");
            return;
        }
        p.removeFromHand(card);
        p.playCard(card);

        // Apply immediate hours
        applyImmediateEffect(card, p);

        // Sharing is Caring: ask for target
        if (card.getName().equals("Sharing is Caring")) {
            requestTarget(TargetRequest.Type.SHARING_TARGET, card, p, null);
        }

        state.setActionTakenThisTurn(true);
        state.getStats().recordPlay(p.getName(), card.getName(), false, false, false);
        log(p.getName() + " played: " + card.getName());
        if (playCardCallback != null) playCardCallback.accept(card, p);
        else { updateUI(); checkEndTurn(); }
    }

    /** Player discards a card from their hand. */
    public void discardFromHand(Card card) {
        Player p = state.getCurrentPlayer();
        if (!p.getHand().contains(card)) { log("Card not in hand."); return; }
        if (state.isActionTakenThisTurn()) { log("Already took an action this turn."); return; }
        if (!state.isCardDrawnThisTurn()) { log("Draw a card first."); return; }

        p.removeFromHand(card);
        applyDiscardHourEffect(card, p, true);
        performDiscardAction(card, p);
        state.discard(card);
        state.setActionTakenThisTurn(true);
        state.getStats().recordDiscard(p.getName());
        log(p.getName() + " discarded from hand: " + card.getName());
        updateUI();
        checkEndTurn();
    }

    /**
     * Applies the immediate hour gain/loss for discarding a card from hand.
     * The "starting value" is the card's immediateHours — what it would cost/gain
     * if discarded the moment it was drawn.
     *
     * Helper cards themselves have no hour value and are always exempt.
     * Cards discarded via Newbie are exempt because Newbie uses bulkDiscard(),
     * which never calls this method — the exemption is structural, not a flag here.
     */
    private void applyDiscardHourEffect(Card card, Player p, boolean fromHand) {
        // Helper cards have no immediate hour effect when discarded
        if (card.getType() == CardType.HELPER) return;

        if (fromHand) {
            int imm = card.getImmediateHours();
            if (imm > 0) {
                int gained = state.takeFromHourPool(imm);
                p.addHours(gained);
                state.getStats().recordHoursGained(p.getName(), gained);
                if (gained != 0) log(p.getName() + " gained +" + gained + "h discarding " + card.getName() + " from hand.");
            } else if (imm < 0) {
                int lost = Math.abs(imm);
                int removed = p.removeHours(lost);
                state.returnToHourPool(removed);
                state.getStats().recordHoursLost(p.getName(), removed);
                if (removed != 0) log(p.getName() + " lost -" + removed + "h discarding " + card.getName() + " from hand.");
            }
        }
        // fromHand=false (played card) is handled inside removePlayedCard
    }

    /** Player discards a card they have played in front of them. */
    public void discardPlayedCard(Card card) {
        Player p = state.getCurrentPlayer();
        if (!p.getPlayedCards().contains(card)) { log("Card not in your play area."); return; }
        if (state.isActionTakenThisTurn()) { log("Already took an action this turn."); return; }

        removePlayedCard(p, card, false);
        state.setActionTakenThisTurn(true);
        log(p.getName() + " discarded played card: " + card.getName());
        updateUI();
        checkEndTurn();
    }

    /** Player skips their turn. */
    public void skipTurn() {
        Player p = state.getCurrentPlayer();
        if (!state.isCardDrawnThisTurn()) { log("Draw a card before skipping."); return; }
        log(p.getName() + " skipped their turn.");
        state.setActionTakenThisTurn(true);
        tickPlayedCards(p);
        endTurn();
    }

    /**
     * Use a Helper card.
     * Counts as a turn ONLY when applied to the current player's own cards (Nepotism/Extension on own card).
     * Blocking a weapon (Excused) or helping another player does NOT count as a turn.
     */
    public void useHelperCard(Card card, Player targetPlayer, Card targetCard) {
        Player p = state.getCurrentPlayer();
        if (!p.getHand().contains(card)) { log("Helper card not in hand."); return; }
        if (card.getType() != CardType.HELPER) { log("Not a helper card."); return; }

        // Helpers that need a target card — ask the UI to pick one first
        if (targetCard == null && needsTargetCard(card)) {
            requestTarget(TargetRequest.Type.HELPER_TARGET, card, p, null);
            return;
        }

        boolean countsAsTurn = helperCountsAsTurn(card, p, targetPlayer, targetCard);
        if (countsAsTurn && state.isActionTakenThisTurn()) {
            log("Already took an action this turn."); return;
        }

        p.removeFromHand(card);
        resolveHelperCard(card, p, targetPlayer, targetCard);
        state.discard(card);

        if (countsAsTurn) state.setActionTakenThisTurn(true);
        state.getStats().recordPlay(p.getName(), card.getName(), false, true, false);
        log(p.getName() + " used helper: " + card.getName());
        if (!card.getName().equals("Newbie")) { updateUI(); checkEndTurn(); }
    }

    /** Called by the UI once the player has picked the target card for a helper. */
    public void applyHelperToCard(Card helper, Player targetPlayer, Card targetCard) {
        Player p = state.getCurrentPlayer();
        if (!p.getHand().contains(helper)) { log("Helper no longer in hand."); resolveTarget(); return; }

        boolean countsAsTurn = helperCountsAsTurn(helper, p, targetPlayer, targetCard);
        if (countsAsTurn && state.isActionTakenThisTurn()) {
            log("Already took an action this turn."); resolveTarget(); return;
        }

        p.removeFromHand(helper);
        resolveHelperCard(helper, p, targetPlayer, targetCard);
        state.discard(helper);

        if (countsAsTurn) state.setActionTakenThisTurn(true);
        log(p.getName() + " used " + helper.getName() + " on " + targetCard.getName());
        resolveTarget();
    }

    /**
     * A helper costs a turn only when applied to the current player's OWN played cards.
     * Blocking weapons (Excused), Newbie (special case), and helping others are free.
     */
    private boolean helperCountsAsTurn(Card helper, Player actor, Player targetPlayer, Card targetCard) {
        return switch (helper.getName()) {
            case "Excused"   -> false; // blocking a weapon is always free
            case "Newbie"    -> true;  // discarding your whole hand is your turn
            case "Extension",
                 "Nepotism"  -> targetCard != null && targetCard.getOwner() == actor; // only if own card
            default          -> false;
        };
    }

    private boolean needsTargetCard(Card card) {
        return switch (card.getName()) {
            case "Nepotism", "Extension", "Excused" -> true;
            default -> false;
        };
    }

    // ── Round tick ───────────────────────────────────────────────────────────

    private void tickPlayedCards(Player p) {
        boolean weaponBlocking = p.isBlockedByWeapon();
        List<Card> toExpire = new ArrayList<>();

        for (Card c : new ArrayList<>(p.getPlayedCards())) {
            c.incrementRoundsPlayed();

            boolean isWeapon = c.isWeapon();

            if (!weaponBlocking || isWeapon) {
                int hoursDelta = computeHoursDelta(c);
                applyHoursDelta(c, p, hoursDelta);
            }

            // Sharing is Caring propagation
            if (state.getSharingLinks().containsValue(p)) {
                // Find which player has the Sharing card linked to p
                for (Map.Entry<Card, Player> entry : state.getSharingLinks().entrySet()) {
                    if (entry.getValue() == p) {
                        Player sharer = entry.getKey().getOwner();
                        if (sharer != null && !weaponBlocking) {
                            int gained = computeHoursDelta(c);
                            if (gained > 0) {
                                int from = state.takeFromHourPool(gained);
                                sharer.addHours(from);
                            }
                        }
                    }
                }
            }

            if (c.shouldExpireThisRound()) toExpire.add(c);
        }

        for (Card c : toExpire) expireCard(p, c);
    }

    private int computeHoursDelta(Card c) {
        // Unpredictable: alternates each round
        if (c.getName().equals("Unpredictable")) {
            return (c.getRoundsPlayed() % 2 == 0) ? 1 : -1;
        }
        return c.getHoursPerRound();
    }

    private void applyHoursDelta(Card c, Player p, int delta) {
        if (delta > 0) {
            if (c.getHoursOnCard() > 0 && c.getImmediateHours() < 0) {
                int reduction = Math.min(delta, c.getHoursOnCard());
                c.removeHoursOnCard(reduction);
                state.returnToHourPool(reduction);
                int remainder = delta - reduction;
                if (remainder > 0) {
                    int gained = state.takeFromHourPool(remainder);
                    p.addHours(gained);
                    c.addHoursOnCard(gained);
                    state.getStats().recordHoursGained(p.getName(), gained);
                    log(p.getName() + " gained +" + gained + "h from " + c.getName() + " (debt reduced by " + reduction + "h).");
                } else {
                    log(p.getName() + "'s " + c.getName() + " reduced debt by " + reduction + "h.");
                }
            } else {
                int gained = state.takeFromHourPool(delta);
                p.addHours(gained);
                c.addHoursOnCard(gained);
                state.getStats().recordHoursGained(p.getName(), gained);
                log(p.getName() + " gained +" + gained + "h from " + c.getName() + ".");
            }
        } else if (delta < 0) {
            int debt = Math.abs(delta);
            int removed = p.removeHours(debt);
            c.addHoursOnCard(removed);
            state.getStats().recordHoursLost(p.getName(), removed);
            log(p.getName() + " lost -" + removed + "h from " + c.getName() + ".");
        }
    }

    // ── Card resolution helpers ───────────────────────────────────────────────

    private void applyImmediateEffect(Card card, Player p) {
        int imm = card.getImmediateHours();
        if (imm > 0) {
            int gained = state.takeFromHourPool(imm);
            p.addHours(gained);
            card.addHoursOnCard(gained);
            state.getStats().recordHoursGained(p.getName(), gained);
            log(p.getName() + " gained +" + gained + "h immediately from " + card.getName() + ".");
        } else if (imm < 0) {
            int debt = Math.abs(imm);
            if (p.getHourTokens() >= debt) {
                p.removeHours(debt);
                card.setHoursOnCard(debt);
                state.getStats().recordHoursLost(p.getName(), debt);
                log(p.getName() + " lost -" + debt + "h immediately from " + card.getName() + ".");
            } else {
                int playerHours = p.getHourTokens();
                p.removeHours(playerHours);
                int remainder = debt - playerHours;
                card.setHoursOnCard(debt);
                state.getStats().recordHoursLost(p.getName(), playerHours);
                log(p.getName() + " lost -" + playerHours + "h immediately from " + card.getName() +
                        " (Debt of " + remainder + "h must be mentally tracked — insufficient tokens.)");
            }
        }
    }

    /** Actions performed when a card is discarded (the text on the card). */
    private void performDiscardAction(Card card, Player actor) {
        switch (card.getName()) {
            case "Professional" -> {
                if (card.getRoundsPlayed() >= 8) {
                    int gained = state.takeFromHourPool(10);
                    actor.addHours(gained);
                    log(actor.getName() + " collected +10 hours from Professional!");
                }
            }
            case "Risky" -> {
                if (card.getRoundsPlayed() >= 8) {
                    int bonus = state.takeFromHourPool(card.getFinalHours());
                    actor.addHours(bonus);
                    log(actor.getName() + " collected bonus hours from Risky!");
                }
                // Hours on card (positive) kept by player already tracked in hourTokens
            }
            default -> {} // most cards have no special discard action beyond their played effect
        }
    }

    /** Expire a played card: player loses all hours on it. Parasite transfers to its sender. */
    private void expireCard(Player p, Card card) {
        int onCard = card.getHoursOnCard();
        state.getStats().recordExpiry();

        if (card.getImmediateHours() < 0) {
            int debt = onCard;
            p.removeHours(debt);
            state.getStats().recordHoursLost(p.getName(), debt);
            transferOrPool(card, debt, p);
            log(p.getName() + "'s " + card.getName() + " expired! Lost -" + debt + "h.");
        } else {
            p.removeHours(onCard);
            state.getStats().recordHoursLost(p.getName(), onCard);
            transferOrPool(card, onCard, p);
            log(p.getName() + "'s " + card.getName() + " expired! Lost -" + onCard + "h.");
        }

        state.getSharingLinks().remove(card);
        state.getSharingLinks().entrySet().removeIf(e -> {
            if (e.getValue() == p) {
                log("Sharing is Caring link broken because " + p.getName() + "'s card expired.");
                return true;
            }
            return false;
        });

        p.removePlayedCard(card);
        state.discard(card);

        if (card.getType() == CardType.WEAPON_ROLLING) {
            Player next = state.getNextPlayer(p);
            receiveWeaponCard(card, next);
        }
    }

    /**
     * For Parasite: transfer hours to the sender.
     * For all other cards: return hours to the pool.
     */
    private void transferOrPool(Card card, int hours, Player victim) {
        if (hours <= 0) return;
        Player beneficiary = card.getName().equals("Parasite") ? card.getSender() : null;
        if (beneficiary != null && beneficiary != victim) {
            beneficiary.addHours(hours);
            state.getStats().recordHoursGained(beneficiary.getName(), hours);
            state.getStats().recordHoursLost(victim.getName(), hours);
            state.getStats().recordTransfer(hours);
            log(beneficiary.getName() + " received +" + hours + "h from Parasite transfer (from " + victim.getName() + ").");
        } else {
            state.returnToHourPool(hours);
        }
    }

    /** Resolve a weapon card being sent to a target. */
    public void resolveWeaponOnTarget(Card weapon, Player sender, Player target) {
        sender.removeFromHand(weapon);

        // For harmful weapons targeting a human player who has Excused: offer defend
        if (weapon.getType() == CardType.WEAPON_IMMEDIATE
                && isHarmfulWeapon(weapon)
                && target.isHuman()
                && targetHasExcused(target)
                && defendCallback != null) {
            // Push a defend pending ON TOP of the SEND_WEAPON pending already on the stack.
            // resolveTarget() below pops the SEND_WEAPON one; defend will push/pop its own.
            state.popPendingResolution(); // pop SEND_WEAPON
            state.pushPendingResolution(); // push DEFEND
            defendCallback.accept(weapon, () -> {
                resolveImmediateWeapon(weapon, sender, target);
                state.setActionTakenThisTurn(true);
                log(sender.getName() + " played " + weapon.getName() + " on " + target.getName());
                resolveTarget(); // pops DEFEND
                if (weaponSentCallback != null) weaponSentCallback.accept(weapon, target);
            });
            return;
        }

        switch (weapon.getType()) {
            case WEAPON_IMMEDIATE -> resolveImmediateWeapon(weapon, sender, target);
            case WEAPON_PLAY, WEAPON_ROLLING -> {
                weapon.setSender(sender); // store for Parasite transfer
                receiveWeaponCard(weapon, target);
            }
        }

        state.setActionTakenThisTurn(true);
        state.getStats().recordPlay(sender.getName(), weapon.getName(), true, false, false);
        log(sender.getName() + " played " + weapon.getName() + " on " + target.getName());
        // Pop the SEND_WEAPON pending — sub-requests (Tardy/Deadline/Quit) have pushed their own
        state.popPendingResolution();
        if (weaponSentCallback != null) weaponSentCallback.accept(weapon, target);
        else { updateUI(); checkEndTurn(); }
    }

    /** Called by UI if target successfully blocked the weapon with Excused. */
    public void weaponBlocked(Card weapon, Player target, Card excused) {
        target.removeFromHand(excused);
        state.discard(excused);
        state.discard(weapon);
        log(target.getName() + " blocked " + weapon.getName() + " with Excused!");
        resolveTarget(); // pops DEFEND pending
        if (weaponSentCallback != null) weaponSentCallback.accept(weapon, target);
    }

    private boolean isHarmfulWeapon(Card w) {
        return switch (w.getName()) {
            case "Deadline", "Quit", "Scammer" -> true;
            default -> false;
        };
    }

    private boolean targetHasExcused(Player target) {
        return target.getHand().stream().anyMatch(c -> c.getName().equals("Excused"));
    }

    private void resolveImmediateWeapon(Card weapon, Player sender, Player target) {
        switch (weapon.getName()) {
            case "Tardy" -> {
                if (!target.getPlayedCards().isEmpty()) {
                    requestTarget(TargetRequest.Type.TARDY_TARGET, weapon, sender, target);
                } else {
                    log(target.getName() + " has no cards in play to delay.");
                }
            }
            case "Deadline" -> {
                if (!target.getPlayedCards().isEmpty()) {
                    requestTarget(TargetRequest.Type.DEADLINE_TARGET, weapon, sender, target);
                } else {
                    log(target.getName() + " has no cards in play to expire.");
                }
            }
            case "Scammer" -> {
                int stolen = target.removeHours(1);
                sender.addHours(stolen);
                state.getStats().recordHoursLost(target.getName(), stolen);
                state.getStats().recordHoursGained(sender.getName(), stolen);
                state.getStats().recordTransfer(stolen);
                log(sender.getName() + " stole +" + stolen + "h from " + target.getName() + ".");
            }
            case "Quit" -> {
                // Sender chooses which of the target's played cards to discard.
                // If the target has no played cards, a random hand card is discarded.
                if (!target.getPlayedCards().isEmpty()) {
                    // Ask the SENDER to pick which of the target's played cards to destroy.
                    requestTarget(TargetRequest.Type.QUIT_TARGET, weapon, sender, target);
                } else if (!target.getHand().isEmpty()) {
                    // No played cards visible — discard a random hand card
                    int idx = new Random().nextInt(target.getHand().size());
                    Card tc = target.getHand().remove(idx);
                    applyDiscardHourEffect(tc, target, true);
                    state.discard(tc);
                    log(target.getName() + " was forced to discard " + tc.getName()
                            + " from hand. Must draw to 5 next turn.");
                } else {
                    log(target.getName() + " has no cards to discard.");
                }
            }
            case "Foreign Exchange" -> {
                if (!sender.getHand().isEmpty() && !target.getHand().isEmpty()) {
                    int si = new Random().nextInt(sender.getHand().size());
                    int ti = new Random().nextInt(target.getHand().size());
                    Card sc = sender.getHand().remove(si);
                    Card tc = target.getHand().remove(ti);
                    sender.addToHand(tc);
                    target.addToHand(sc);
                    log(sender.getName() + " and " + target.getName() + " traded random cards.");
                }
            }
        }
    }

    private void receiveWeaponCard(Card weapon, Player target) {
        // Target must discard one played card if play area is full
        if (!target.canPlayCard()) {
            target.getPlayedCards().stream()
                    .filter(c -> !c.isWeapon())
                    .findFirst()
                    .ifPresent(c -> removePlayedCard(target, c, false));
        }
        // Remember sender before reassigning owner (sender set by caller via resolveWeaponOnTarget)
        weapon.setOwner(target);
        weapon.setHoursOnCard(0);
        weapon.setExpired(false);
        applyImmediateEffect(weapon, target);
        target.getPlayedCards().add(weapon);
    }

    private void resolveAlertCard(Card card, Player p) {
        switch (card.getName()) {
            case "Fired!" -> {
                log("FIRED! All played cards of " + p.getName() + " expire!");
                for (Card c : new ArrayList<>(p.getPlayedCards())) expireCard(p, c);
            }
            case "Performance Review" -> {
                log("PERFORMANCE REVIEW! " + p.getName() + " discards hand and redraws.");
                List<Card> toDrop = new ArrayList<>(p.getHand());
                p.getHand().clear();
                // Apply discard hour effects and special actions on each card
                for (Card c : toDrop) {
                    applyDiscardHourEffect(c, p, true);
                    performDiscardAction(c, p);
                }
                bulkDiscard(toDrop, () -> {
                    safeRefillHand(p);
                    updateUI();
                });
            }
            case "Amnesia" -> {
                log("AMNESIA! All cards reset to original values!");
                for (Player pl : state.getPlayers()) {
                    for (Card c : pl.getPlayedCards()) {
                        c.setHoursOnCard(0);
                        c.setExpired(false);
                        c.resetRoundsPlayed();
                    }
                }
            }
            case "Recession" -> {
                log("RECESSION! All played cards expire for everyone!");
                for (Player pl : state.getPlayers()) {
                    for (Card c : new ArrayList<>(pl.getPlayedCards())) expireCard(pl, c);
                }
            }
        }
        state.discard(card);
        updateUI();
    }

    private void resolveHelperCard(Card card, Player actor, Player targetPlayer, Card targetCard) {
        switch (card.getName()) {
            case "Excused" -> {
                if (targetCard != null && targetCard.isWeapon()) {
                    if (targetPlayer != null) targetPlayer.removePlayedCard(targetCard);
                    else {
                        // search all players
                        for (Player pl : state.getPlayers())
                            if (pl.getPlayedCards().contains(targetCard)) { pl.removePlayedCard(targetCard); break; }
                    }
                    state.discard(targetCard);
                    log("Excused blocked " + targetCard.getName());
                } else {
                    log("Excused needs a weapon card target.");
                }
            }
            case "Extension" -> {
                if (targetCard != null) {
                    // Store +5 extension as negative roundsPlayed offset
                    // We subtract from roundsPlayed so the card "ages" 5 rounds slower
                    // Clamped so it can't go below 0
                    int reduction = Math.min(5, targetCard.getRoundsPlayed());
                    for (int i = 0; i < reduction; i++) {
                        // Card doesn't have decrementRoundsPlayed — use resetRoundsPlayed and replay
                    }
                    // Proper approach: store extension bonus on card
                    targetCard.addExtensionBonus(5);
                    targetCard.attachHelper(card);
                    log("Extension: " + targetCard.getName() + " expires 5 rounds later.");
                } else {
                    log("Extension needs a target card.");
                }
            }
            case "Nepotism" -> {
                if (targetCard != null) {
                    targetCard.setNepotismProtected(true);
                    targetCard.attachHelper(card);
                    log("Nepotism protects " + targetCard.getName() + " from expiring.");
                } else {
                    log("Nepotism needs a target card.");
                }
            }
            case "Newbie" -> {
                List<Card> toDrop = new ArrayList<>(actor.getHand());
                actor.getHand().clear();
                bulkDiscard(toDrop, () -> {
                    safeRefillHand(actor);
                    log(actor.getName() + " used Newbie — dead hand, drew fresh cards.");
                    updateUI();
                    checkEndTurn();
                });
            }
        }
    }

    /** Properly remove a played card from the play area. */
    private void removePlayedCard(Player p, Card card, boolean expired) {
        // Discard any helpers attached to this card
        for (Card h : card.getAttachedHelpers()) state.discard(h);
        card.clearAttachedHelpers();

        if (expired) {
            expireCard(p, card);
        } else {
            int onCard = card.getHoursOnCard();
            if (onCard > 0) {
                if (card.getImmediateHours() < 0) {
                    // Debt card: pay remaining balance
                    int paid = p.removeHours(onCard);
                    transferOrPool(card, paid, p);
                    state.getStats().recordHoursLost(p.getName(), paid);
                    log(p.getName() + " paid remaining debt of -" + paid + "h discarding " + card.getName() + ".");
                } else {
                    // Positive card: player KEEPS accumulated hours — they were earned each round.
                    // Hours are already in the player's bank. The card just leaves.
                    log(p.getName() + " discarded " + card.getName() + ", keeping +" + onCard + "h earned.");
                }
            }

            // Special bonus actions on timely discard (Professional, Risky)
            performDiscardAction(card, p);

            // Sharing is Caring cleanup
            state.getSharingLinks().remove(card);
            p.removePlayedCard(card);
            state.discard(card);
        }
    }

    // ── Bulk discard & safe redraw ────────────────────────────────────────────

    /**
     * Moves cards to the discard pile and fires the UI callback to animate them.
     * If no callback is set, discards immediately and calls onDone synchronously.
     */
    private void bulkDiscard(List<Card> cards, Runnable onDone) {
        for (Card c : cards) state.discard(c);
        if (bulkDiscardCallback != null) {
            bulkDiscardCallback.accept(new ArrayList<>(cards), onDone);
        } else {
            onDone.run();
        }
    }

    /**
     * Draw cards to fill a player's hand to MAX_HAND_SIZE, skipping alert cards.
     */
    private void safeRefillHand(Player p) {
        ensureMinHand(p);
    }

    /**
     * Ensure a player has at least MAX_HAND_SIZE cards, topping up silently.
     * Skips alert cards (reshuffled to bottom).
     */
    private void ensureMinHand(Player p) {
        int attempts = 0;
        while (p.handSize() < Player.MAX_HAND_SIZE && attempts < 50) {
            attempts++;
            Card nc = state.drawCard(false);
            if (nc == null) break;
            if (nc.getType() == CardType.ALERT) {
                state.getDrawPile().addLast(nc);
                continue;
            }
            p.addToHand(nc);
        }
    }

    // ── Turn end ─────────────────────────────────────────────────────────────

    private void checkEndTurn() {
        if (state.isActionTakenThisTurn() && !state.hasPendingResolutions()) endTurn();
    }

    private void endTurn() {
        Player p = state.getCurrentPlayer();
        tickPlayedCards(p);
        ensureMinHand(p); // always top up to 5 before moving on

        if (state.isGameOver()) {
            state.setPhase(GameState.Phase.GAME_OVER);
            state.getStats().finalize(state.getPlayers(), state.getGlobalRound());
            com.workgame.model.StatsHistory.add(state.getStats());
            log("Game Over!");
        } else {
            state.nextPlayer();
            log("It's now " + state.getCurrentPlayer().getName() + "'s turn.");
        }
        updateUI();
    }

    // ── Target requests ──────────────────────────────────────────────────────

    private void requestTarget(TargetRequest.Type type, Card card, Player actor, Player excludePlayer) {
        state.pushPendingResolution();
        if (targetRequestCallback != null) {
            targetRequestCallback.accept(new TargetRequest(type, card, actor, excludePlayer, state.getPlayers()));
        }
    }

    /** Pop a pending resolution and check if the turn can now end. */
    private void resolveTarget() {
        state.popPendingResolution();
        if (!state.hasPendingResolutions() && state.isActionTakenThisTurn()) {
            checkEndTurn();
        }
        updateUI();
    }

    /** Called by the UI once the sender has chosen which of the target's played cards to destroy. */
    public void resolveQuitTarget(Player target, Card chosenCard) {
        removePlayedCard(target, chosenCard, false);
        log(target.getName() + " was forced to discard " + chosenCard.getName() + ".");
        resolveTarget();
    }

    /** Called by UI after sender picks which card to delay with Tardy. */
    public void resolveTardy(Player target, Card targetCard) {
        targetCard.addExtensionBonus(1);
        log("Tardy: " + targetCard.getName() + " on " + target.getName() + " delayed by 1 round.");
        resolveTarget();
    }

    /** Called by UI after sender picks which card to expire with Deadline. */
    public void resolveDeadline(Player target, Card targetCard) {
        expireCard(target, targetCard);
        log("Deadline expired " + targetCard.getName() + " for " + target.getName() + ".");
        resolveTarget();
    }

    /** Called by the UI after the play-to-zone animation completes. */
    public void finishPlayCard() {
        updateUI();
        checkEndTurn();
    }

    /** Called by the UI after the weapon-sent animation completes. */
    public void finishWeaponSent() {
        updateUI();
        checkEndTurn();
    }

    public void setSharingTarget(Card card, Player target) {
        state.getSharingLinks().put(card, target);
        log("Sharing is Caring linked to " + target.getName());
        resolveTarget();
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private void log(String msg) {
        state.setLastMessage(msg);
        if (logCallback != null) logCallback.accept(msg);
    }

    private void updateUI() {
        if (updateUICallback != null) updateUICallback.run();
    }

    public GameState getState() { return state; }

    // ── Target request DTO ───────────────────────────────────────────────────

    public record TargetRequest(
            Type type, Card card, Player actor, Player exclude, List<Player> allPlayers
    ) {
        public enum Type {
            SEND_WEAPON,      // pick which player to send a weapon to
            SHARING_TARGET,   // pick which player to link Sharing is Caring to
            QUIT_TARGET,      // sender picks which of target's played cards to discard
            HELPER_TARGET,    // pick which played card to attach a helper to
            TARDY_TARGET,     // sender picks which of target's played cards to delay
            DEADLINE_TARGET   // sender picks which of target's played cards to expire
        }
    }
}