package com.workgame.controller;

import com.workgame.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI decision engine.  Called once per CPU turn; decides and executes a full turn.
 *
 * Easy:   Draw, then make a random legal action.
 * Medium: Draw, then apply heuristic (play good cards, discard bad, target weak opponents).
 * Hard:   Draw, then apply greedy strategy (target the leader, protect own cards, combo plays).
 */
public class CpuPlayer {

    private final GameController controller;
    private final GameState      state;
    private final Random         rng = new Random();

    public CpuPlayer(GameController controller, GameState state) {
        this.controller = controller;
        this.state      = state;
    }

    /** Execute one full CPU turn. */
    public void takeTurn() {
        Player me = state.getCurrentPlayer();
        System.out.println("[CPU] takeTurn: " + me.getName()
                + " hand=" + me.handSize()
                + " played=" + me.getPlayedCards().size()
                + " hours=" + me.getHourTokens());
        controller.drawCard();
        javafx.application.Platform.runLater(() -> decideAction(me));
    }

    public void continueAction() {
        Player me = state.getCurrentPlayer();
        if (me == null || me.isHuman()) {
            System.out.println("[CPU] continueAction: skipped (human or null)");
            return;
        }
        if (state.isActionTakenThisTurn()) {
            System.out.println("[CPU] continueAction: skipped (already acted)");
            return;
        }
        System.out.println("[CPU] continueAction: " + me.getName());
        javafx.application.Platform.runLater(() -> decideAction(me));
    }

    private void decideAction(Player me) {
        System.out.println("[CPU] decideAction: " + me.getName()
                + " type=" + me.getPlayerType()
                + " hand=" + me.handSize()
                + " acted=" + state.isActionTakenThisTurn());
        switch (me.getPlayerType()) {
            case CPU_EASY   -> easyAction(me);
            case CPU_MEDIUM -> mediumAction(me);
            case CPU_HARD   -> hardAction(me);
            default         -> {}
        }
    }

    // ── EASY ─────────────────────────────────────────────────────────────────

    private void easyAction(Player me) {
        List<Card> hand = me.getHand();
        if (hand.isEmpty()) { controller.skipTurn(); return; }

        Card pick = hand.get(rng.nextInt(hand.size()));

        switch (pick.getType()) {
            case PLAY -> {
                if (me.canPlayCard()) controller.playCardFromHand(pick);
                else controller.discardFromHand(randomCard(hand));
            }
            case HELPER -> controller.useHelperCard(pick, null, null);
            case WEAPON_IMMEDIATE, WEAPON_PLAY, WEAPON_ROLLING -> {
                Player target = randomOpponent(me);
                if (target != null) controller.resolveWeaponOnTarget(pick, me, target);
                else controller.discardFromHand(pick);
            }
            default -> controller.discardFromHand(pick);
        }
    }

    // ── MEDIUM ───────────────────────────────────────────────────────────────

    private void mediumAction(Player me) {
        List<Card> hand = me.getHand();
        if (hand.isEmpty()) { controller.skipTurn(); return; }

        // Priority 1: play a positive play card if we have room
        if (me.canPlayCard()) {
            Card best = hand.stream()
                    .filter(c -> c.getType() == CardType.PLAY && c.getImmediateHours() >= 0)
                    .findFirst().orElse(null);
            if (best != null) { controller.playCardFromHand(best); return; }
        }

        // Priority 2: use a helper on our best played card
        Card helper = hand.stream().filter(c -> c.getType() == CardType.HELPER
                && !c.getName().equals("Newbie")).findFirst().orElse(null);
        if (helper != null && !me.getPlayedCards().isEmpty()) {
            Card target = me.getPlayedCards().stream()
                    .max(Comparator.comparingInt(Card::getHoursOnCard)).orElse(null);
            if (target != null && needsTargetCard(helper)) {
                controller.applyHelperToCard(helper, me, target);
                return;
            }
            controller.useHelperCard(helper, null, null);
            return;
        }

        // Priority 3: send a weapon at the richest opponent
        Card weapon = hand.stream()
                .filter(c -> c.isWeapon()).findFirst().orElse(null);
        if (weapon != null) {
            Player target = richestOpponent(me);
            if (target != null) { controller.resolveWeaponOnTarget(weapon, me, target); return; }
        }

        // Priority 4: discard a negative card from hand
        Card worst = hand.stream()
                .filter(c -> c.getImmediateHours() < 0)
                .min(Comparator.comparingInt(Card::getImmediateHours)).orElse(null);
        if (worst != null) { controller.discardFromHand(worst); return; }

        // Fallback: discard a random card
        controller.discardFromHand(randomCard(hand));
    }

    // ── HARD ─────────────────────────────────────────────────────────────────

    private void hardAction(Player me) {
        List<Card> hand = me.getHand();
        if (hand.isEmpty()) { controller.skipTurn(); return; }

        // Priority 1: play Professional or high-value play card
        if (me.canPlayCard()) {
            Card pro = hand.stream()
                    .filter(c -> c.getType() == CardType.PLAY
                            && (c.getName().equals("Professional") || c.getFinalHours() > 0
                            || c.getHoursPerRound() > 0))
                    .findFirst().orElse(null);
            if (pro != null) { controller.playCardFromHand(pro); return; }
        }

        // Priority 2: protect our highest-value played card with Nepotism/Extension
        Card protector = hand.stream()
                .filter(c -> c.getType() == CardType.HELPER
                        && (c.getName().equals("Nepotism") || c.getName().equals("Extension")))
                .findFirst().orElse(null);
        if (protector != null && !me.getPlayedCards().isEmpty()) {
            Card best = me.getPlayedCards().stream()
                    .filter(c -> !c.isNepotismProtected())
                    .max(Comparator.comparingInt(c -> c.getHoursPerRound() + c.getFinalHours()))
                    .orElse(null);
            if (best != null) { controller.applyHelperToCard(protector, me, best); return; }
        }

        // Priority 3: Deadline or Quit on the leading opponent
        Player leader = richestOpponent(me);
        if (leader != null) {
            Card deadline = hand.stream()
                    .filter(c -> c.getName().equals("Deadline") || c.getName().equals("Quit"))
                    .findFirst().orElse(null);
            if (deadline != null) {
                controller.resolveWeaponOnTarget(deadline, me, leader);
                // Auto-pick their best played card for Deadline/Quit via first in list
                return;
            }
        }

        // Priority 4: any weapon at richest opponent
        Card weapon = hand.stream().filter(Card::isWeapon).findFirst().orElse(null);
        if (weapon != null && leader != null) {
            controller.resolveWeaponOnTarget(weapon, me, leader);
            return;
        }

        // Priority 5: play any positive play card
        if (me.canPlayCard()) {
            Card play = hand.stream()
                    .filter(c -> c.getType() == CardType.PLAY).findFirst().orElse(null);
            if (play != null) { controller.playCardFromHand(play); return; }
        }

        // Fallback: discard worst card
        Card worst = hand.stream()
                .min(Comparator.comparingInt(c -> c.getImmediateHours() + c.getHoursPerRound()))
                .orElse(randomCard(hand));
        controller.discardFromHand(worst);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Card randomCard(List<Card> hand) {
        return hand.get(rng.nextInt(hand.size()));
    }

    private Player randomOpponent(Player me) {
        List<Player> opponents = state.getPlayers().stream()
                .filter(p -> p != me).toList();
        return opponents.isEmpty() ? null : opponents.get(rng.nextInt(opponents.size()));
    }

    private Player richestOpponent(Player me) {
        return state.getPlayers().stream()
                .filter(p -> p != me)
                .max(Comparator.comparingInt(Player::getHourTokens))
                .orElse(null);
    }

    private boolean needsTargetCard(Card card) {
        return card.getName().equals("Nepotism")
                || card.getName().equals("Extension")
                || card.getName().equals("Excused");
    }

    // ── Auto-resolve callbacks for CPU ────────────────────────────────────────

    /**
     * Called when a free helper resolved mid-turn and the CPU still needs to act.
     * Implementation is above alongside takeTurn.
     */
    public void handleTargetRequest(GameController.TargetRequest req) {
        Player me = state.getCurrentPlayer();
        switch (req.type()) {
            case TARDY_TARGET -> {
                Player target = req.exclude();
                if (target != null && !target.getPlayedCards().isEmpty())
                    controller.resolveTardy(target, target.getPlayedCards().get(0));
            }
            case DEADLINE_TARGET -> {
                Player target = req.exclude();
                if (target != null && !target.getPlayedCards().isEmpty()) {
                    // Pick the card with the most hours (most damaging to expire)
                    Card worst = target.getPlayedCards().stream()
                            .max(Comparator.comparingInt(Card::getHoursOnCard))
                            .orElse(target.getPlayedCards().get(0));
                    controller.resolveDeadline(target, worst);
                }
            }
            case QUIT_TARGET -> {
                Player target = req.exclude();
                if (target != null && !target.getPlayedCards().isEmpty())
                    controller.resolveQuitTarget(target, target.getPlayedCards().get(0));
            }
            case SHARING_TARGET -> {
                // Pick opponent with most hours to share with
                Player richest = richestOpponent(me);
                if (richest != null) controller.setSharingTarget(req.card(), richest);
            }
            case HELPER_TARGET -> {
                // Apply to our own best played card
                if (!me.getPlayedCards().isEmpty())
                    controller.applyHelperToCard(req.card(), me, me.getPlayedCards().get(0));
            }
            default -> {} // SEND_WEAPON handled before resolveWeaponOnTarget is called
        }
    }
}