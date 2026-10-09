package edu.univalle.yugioh.logic;

import edu.univalle.yugioh.listener.BattleListener;
import edu.univalle.yugioh.model.Card;
import edu.univalle.yugioh.model.Position;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reglas y estado del duelo. NO depende de Swing: se comunica con la interfaz
 * únicamente a través de {@link BattleListener}.
 *
 * <p>Reglas:
 * <ul>
 *   <li>Ambas en ATAQUE: gana el mayor ATK.</li>
 *   <li>Ambas en DEFENSA: gana el mayor DEF.</li>
 *   <li>Una ataca y otra defiende: el atacante gana solo si su ATK es mayor que el DEF
 *       del defensor; en caso contrario gana el defensor.</li>
 *   <li>Empate exacto (misma posición y mismo valor): desempate aleatorio.</li>
 *   <li>El primero en ganar 2 rondas gana el duelo.</li>
 * </ul>
 */
public class Duel {

    public static final String PLAYER = "Jugador";
    public static final String AI = "IA";
    public static final int ROUNDS_TO_WIN = 2;
    public static final int CARDS_PER_SIDE = 3;

    private final List<Card> playerHand;
    private final List<Card> aiHand;
    private final Set<Card> playerUsed = new HashSet<>();
    private final Set<Card> aiUsed = new HashSet<>();
    private final List<BattleListener> listeners = new CopyOnWriteArrayList<>();
    private final Random random = new Random();

    private int playerScore;
    private int aiScore;
    private int round;
    private boolean started;
    private boolean finished;
    private boolean playerStarts;

    // Datos de la última ronda (se fijan ANTES de notificar a los listeners)
    private Card lastAiCard;
    private String lastDetail = "";

    public Duel(List<Card> playerCards, List<Card> aiCards) {
        if (playerCards.size() != CARDS_PER_SIDE || aiCards.size() != CARDS_PER_SIDE) {
            throw new IllegalArgumentException("Cada bando necesita exactamente " + CARDS_PER_SIDE + " cartas.");
        }
        this.playerHand = new ArrayList<>(playerCards);
        this.aiHand = new ArrayList<>(aiCards);
    }

    public void addListener(BattleListener listener) { listeners.add(listener); }
    public void removeListener(BattleListener listener) { listeners.remove(listener); }

    /** Inicia el duelo y define aleatoriamente quién elige primero en la ronda 1. */
    public void start() {
        if (started) {
            throw new IllegalStateException("El duelo ya inició.");
        }
        started = true;
        playerStarts = random.nextBoolean();
    }

    /**
     * Juega una ronda: la IA elige carta y posición al azar entre las disponibles,
     * se resuelve el enfrentamiento y se notifica a los listeners.
     */
    public void playRound(Card playerCard, Position playerPosition) {
        if (!started || finished) {
            throw new IllegalStateException("El duelo no está en curso.");
        }
        if (!playerHand.contains(playerCard) || playerUsed.contains(playerCard)) {
            throw new IllegalArgumentException("Carta no disponible.");
        }

        Card aiCard = pickRandomAvailable(aiHand, aiUsed);
        Position aiPosition = random.nextBoolean() ? Position.ATAQUE : Position.DEFENSA;

        playerUsed.add(playerCard);
        aiUsed.add(aiCard);
        round++;

        boolean playerWins = resolve(playerCard, playerPosition, aiCard, aiPosition);
        if (playerWins) { playerScore++; } else { aiScore++; }
        lastAiCard = aiCard;
        finished = playerScore >= ROUNDS_TO_WIN || aiScore >= ROUNDS_TO_WIN;
        String winner = playerWins ? PLAYER : AI;

        String playerDesc = playerCard.getName() + " [" + playerPosition + "]";
        String aiDesc = aiCard.getName() + " [" + aiPosition + "]";
        for (BattleListener l : listeners) { l.onTurn(playerDesc, aiDesc, winner); }
        for (BattleListener l : listeners) { l.onScoreChanged(playerScore, aiScore); }

        playerStarts = !playerStarts; // el turno inicial se alterna cada ronda
        if (finished) {
            String duelWinner = playerScore > aiScore ? PLAYER : AI;
            for (BattleListener l : listeners) { l.onDuelEnded(duelWinner); }
        }
    }

    /** Compara las cartas según las reglas. Devuelve true si gana el jugador y guarda el detalle. */
    private boolean resolve(Card pc, Position pp, Card ac, Position ap) {
        int pPower;
        int aPower;
        String rule;
        if (pp == Position.ATAQUE && ap == Position.ATAQUE) {
            pPower = pc.getAtk(); aPower = ac.getAtk(); rule = "ATK vs ATK";
        } else if (pp == Position.DEFENSA && ap == Position.DEFENSA) {
            pPower = pc.getDef(); aPower = ac.getDef(); rule = "DEF vs DEF";
        } else if (pp == Position.ATAQUE) { // jugador ataca, IA defiende
            pPower = pc.getAtk(); aPower = ac.getDef(); rule = "ATK del Jugador vs DEF de la IA";
            lastDetail = rule + ": " + pPower + " vs " + aPower;
            return pPower > aPower; // el atacante necesita ATK estrictamente mayor
        } else { // IA ataca, jugador defiende
            pPower = pc.getDef(); aPower = ac.getAtk(); rule = "ATK de la IA vs DEF del Jugador";
            lastDetail = rule + ": " + aPower + " vs " + pPower;
            return pPower >= aPower; // si el ATK atacante no supera el DEF, gana el defensor
        }

        if (pPower == aPower) {
            lastDetail = rule + ": " + pPower + " vs " + aPower + " -> empate, desempate al azar";
            return random.nextBoolean();
        }
        lastDetail = rule + ": " + pPower + " vs " + aPower;
        return pPower > aPower;
    }

    private Card pickRandomAvailable(List<Card> hand, Set<Card> used) {
        List<Card> available = new ArrayList<>();
        for (Card c : hand) {
            if (!used.contains(c)) { available.add(c); }
        }
        return available.get(random.nextInt(available.size()));
    }

    // ------------------------------------------------------------------ consultas

    public boolean isStarted() { return started; }
    public boolean isFinished() { return finished; }
    /** true si el Jugador elige primero en la ronda actual (se alterna cada ronda). */
    public boolean isPlayerStarter() { return playerStarts; }
    public int getRound() { return round; }
    public int getPlayerScore() { return playerScore; }
    public int getAiScore() { return aiScore; }
    public List<Card> getPlayerHand() { return Collections.unmodifiableList(playerHand); }
    public List<Card> getAiHand() { return Collections.unmodifiableList(aiHand); }
    /** Carta que jugó la IA en la última ronda. */
    public Card getLastAiCard() { return lastAiCard; }
    /** Explicación de cómo se resolvió la última ronda. */
    public String getLastDetail() { return lastDetail; }
}
