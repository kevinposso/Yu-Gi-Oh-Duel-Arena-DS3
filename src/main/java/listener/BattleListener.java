package edu.univalle.yugioh.listener;

/**
 * Observador de los eventos del duelo. Desacopla la lógica ({@code Duel}) de la interfaz:
 * Duel notifica y la UI decide cómo mostrarlo.
 */
public interface BattleListener {

    /**
     * Se terminó una ronda.
     *
     * @param playerCard descripción de la carta del jugador (nombre y posición)
     * @param aiCard     descripción de la carta de la IA (nombre y posición)
     * @param winner     "Jugador" o "IA"
     */
    void onTurn(String playerCard, String aiCard, String winner);

    /** El marcador cambió. */
    void onScoreChanged(int playerScore, int aiScore);

    /** El duelo terminó. @param winner "Jugador" o "IA" */
    void onDuelEnded(String winner);
}
