package edu.univalle.yugioh.ui;

import edu.univalle.yugioh.api.YgoApiClient;
import edu.univalle.yugioh.api.YgoApiException;
import edu.univalle.yugioh.listener.BattleListener;
import edu.univalle.yugioh.logic.Duel;
import edu.univalle.yugioh.model.Card;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Ventana principal. Implementa {@link BattleListener} para reaccionar a los eventos de
 * {@link Duel}. Toda la red (API + imágenes) se hace en un SwingWorker para no bloquear el EDT.
 */
public class MainFrame extends JFrame implements BattleListener {

    // Paleta del tema (compartida con CardPanel)
    static final Color BG_DARK = new Color(10, 14, 26);
    static final Color BG_PANEL = new Color(18, 26, 46);
    static final Color BLUE = new Color(70, 140, 230);
    static final Color BLUE_DARK = new Color(30, 70, 140);
    static final Color GOLD = new Color(212, 175, 55);
    static final Color GOLD_DARK = new Color(140, 110, 30);
    static final Color TEXT = new Color(225, 230, 240);
    static final Color TEXT_DIM = new Color(150, 160, 180);
    static final Color OK = new Color(110, 200, 120);
    static final Color ERR = new Color(240, 100, 100);

    private static final Map<String, BufferedImage> IMAGES = new HashMap<>();

    /** Carga (y cachea) una imagen de /assets/ui. Devuelve null si no existe. */
    static BufferedImage loadImage(String name) {
        return IMAGES.computeIfAbsent(name, n -> {
            try {
                return ImageIO.read(MainFrame.class.getResource("/assets/ui/" + n));
            } catch (Exception e) {
                return null;
            }
        });
    }

    private static final int TOTAL_CARDS = Duel.CARDS_PER_SIDE * 2;

    private final YgoApiClient api = new YgoApiClient();
    private final JButton btnStart = new ArenaButton("Iniciar duelo");
    private final JButton btnReload = new ArenaButton("Cargar nuevas cartas");
    private final JButton btnHowTo = new ArenaButton("Cómo jugar");
    private final JButton btnRematch = new ArenaButton("Revancha");
    private final ScoreBadge badgePlayer = new ScoreBadge(Duel.PLAYER, false);
    private final ScoreBadge badgeAi = new ScoreBadge(Duel.AI, true);
    private final JLabel lblStatus = new JLabel(" ", SwingConstants.CENTER);
    private final JProgressBar progress = new JProgressBar();
    private final JTextArea log = new JTextArea(5, 40);
    private final JPanel aiRow = cardRow();
    private final JPanel playerRow = cardRow();
    private final JLabel lblResult = new JLabel(" ", SwingConstants.CENTER);
    private CardPanel lastPlayerPanel;
    private String arena = "field-basic.png";
    private final List<CardPanel> playerPanels = new ArrayList<>();
    private final List<CardPanel> aiPanels = new ArrayList<>();

    private Duel duel;
    private ViewState state = ViewState.IDLE;

    /** Estados de la pantalla; de ellos depende qué botones están habilitados. */
    private enum ViewState { IDLE, LOADING, READY, IN_PROGRESS, FINISHED, ERROR }

    private void setState(ViewState newState) {
        state = newState;
        btnStart.setEnabled(state == ViewState.READY);
        btnRematch.setEnabled(state == ViewState.FINISHED);
        btnReload.setEnabled(state == ViewState.READY || state == ViewState.FINISHED || state == ViewState.ERROR);
        btnStart.setToolTipText(state == ViewState.READY ? "Comienza el duelo"
                : "Disponible cuando las cartas están cargadas y el duelo no ha empezado");
        btnRematch.setToolTipText(state == ViewState.FINISHED ? "Repite el duelo con las mismas cartas"
                : "Disponible cuando termina un duelo");
        btnReload.setToolTipText(btnReload.isEnabled() ? "Descarga 6 cartas nuevas"
                : "No disponible mientras se cargan cartas o se juega un duelo");
    }

    public MainFrame() {
        super("Yu-Gi-Oh! Duel Arena");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        buildUi();
        wireListeners();
        pack();
        fitToScreen();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
        updateScore(0, 0);
        loadCards(); // al abrir, ambos jugadores reciben sus 3 cartas
    }

    /** Evita que la ventana sea más grande que el área útil de la pantalla (portátiles / escalado de Windows). */
    private void fitToScreen() {
        java.awt.Rectangle usable = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(getWidth(), usable.width), Math.min(getHeight(), usable.height));
    }

    // ------------------------------------------------------------------ construcción de la UI

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(8, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, new Color(6, 9, 18), 0, getHeight(), new Color(16, 28, 56)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        root.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        setContentPane(root);

        JLabel title = new JLabel("YU-GI-OH! DUEL ARENA", SwingConstants.CENTER);
        title.setFont(new Font(Font.SERIF, Font.BOLD, 30));
        title.setForeground(GOLD);
        lblStatus.setForeground(TEXT_DIM);
        progress.setIndeterminate(true);
        progress.setVisible(false);
        progress.setBorderPainted(false);
        progress.setBackground(BG_PANEL);
        progress.setForeground(BLUE);
        progress.setPreferredSize(new Dimension(100, 6));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);
        buttons.add(btnStart);
        buttons.add(btnRematch);
        buttons.add(btnReload);
        buttons.add(btnHowTo);
        setState(ViewState.IDLE); // loadCards() pasa a LOADING; con LOADING aquí la carga nunca arrancaba

        JPanel titleAndButtons = new JPanel(new GridLayout(0, 1, 0, 4));
        titleAndButtons.setOpaque(false);
        titleAndButtons.add(title);
        titleAndButtons.add(buttons);
        JPanel head = new JPanel(new BorderLayout(12, 0)); // marcador | título y botones | marcador
        head.setOpaque(false);
        head.add(badgePlayer, BorderLayout.WEST);
        head.add(titleAndButtons, BorderLayout.CENTER);
        head.add(badgeAi, BorderLayout.EAST);

        lblResult.setFont(new Font(Font.SERIF, Font.BOLD, 15));
        lblResult.setForeground(TEXT_DIM);
        JPanel messages = new JPanel(new GridLayout(0, 1, 0, 2));
        messages.setOpaque(false);
        messages.add(lblResult);
        messages.add(lblStatus);
        JPanel foot = new JPanel(new BorderLayout());
        foot.setOpaque(false);
        foot.add(messages, BorderLayout.CENTER);
        foot.add(progress, BorderLayout.SOUTH); // oculta = no ocupa espacio
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(head, BorderLayout.NORTH);
        top.add(foot, BorderLayout.SOUTH);
        root.add(top, BorderLayout.NORTH);

        styleRow(aiRow, "IA", BLUE);
        styleRow(playerRow, "Tus cartas", GOLD);
        JPanel center = new JPanel(new GridLayout(1, 2, 14, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                BufferedImage field = loadImage(arena);
                if (field == null) {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                double k = Math.max(getWidth() / (double) field.getWidth(), getHeight() / (double) field.getHeight());
                int w = (int) (field.getWidth() * k), h = (int) (field.getHeight() * k);
                g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 18, 18));
                g2.drawImage(field, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
                g2.setColor(new Color(6, 10, 22, 175)); // oscurece el campo para que no compita con las cartas
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        center.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        center.add(aiRow);
        center.add(playerRow);
        root.add(center, BorderLayout.CENTER);

        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        log.setBackground(new Color(8, 12, 22));
        log.setForeground(TEXT);
        log.setCaretColor(TEXT);
        log.setMargin(new java.awt.Insets(4, 6, 4, 6));
        JScrollPane scroll = new JScrollPane(log);
        scroll.getViewport().setBackground(new Color(8, 12, 22));
        scroll.setOpaque(false);
        scroll.setBorder(titled("Registro de batalla", GOLD));
        root.add(scroll, BorderLayout.SOUTH);
    }

    /** Fila de cartas con fondo translucente para dejar ver el campo de batalla. */
    private static JPanel cardRow() {
        return new JPanel(new GridLayout(1, Duel.CARDS_PER_SIDE, 8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(10, 16, 34, 170));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.dispose();
            }
        };
    }

    private static void styleRow(JPanel row, String title, Color accent) {
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                titled(title, accent), BorderFactory.createEmptyBorder(4, 6, 6, 6)));
    }

    private static TitledBorder titled(String text, Color color) {
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(color.darker(), 2, true), " " + text + " ");
        tb.setTitleColor(color);
        tb.setTitleFont(new Font(Font.SERIF, Font.BOLD, 14));
        return tb;
    }

    /** Contador de puntos con los assets de Life Point (contador + dígitos del sprite). */
    static class ScoreBadge extends JPanel {
        private static final double SCALE = 0.42;
        private static final int CAPTION_H = 18;
        private final String caption;
        private final boolean enemy;
        private int score;

        ScoreBadge(String caption, boolean enemy) {
            this.caption = caption;
            this.enemy = enemy;
            setOpaque(false);
            BufferedImage img = loadImage(enemy ? "life-point-counter-enemy.png" : "life-point-counter.png");
            int w = img != null ? (int) (img.getWidth() * SCALE) : 140;
            int h = img != null ? (int) (img.getHeight() * SCALE) : 90;
            setPreferredSize(new Dimension(w, h + CAPTION_H));
        }

        void setScore(int score) {
            this.score = score;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(new Font(Font.SERIF, Font.BOLD, 13));
            g2.setColor(enemy ? BLUE : GOLD);
            int tw = g2.getFontMetrics().stringWidth(caption);
            g2.drawString(caption, (getWidth() - tw) / 2, 13);
            g2.translate(0, CAPTION_H);
            BufferedImage img = loadImage(enemy ? "life-point-counter-enemy.png" : "life-point-counter.png");
            BufferedImage digits = loadImage("life-point-numbers.png");
            if (img == null || digits == null) {
                g2.setColor(TEXT);
                g2.setFont(new Font(Font.SERIF, Font.BOLD, 28));
                g2.drawString("LP " + score, 10, 50);
                g2.dispose();
                return;
            }
            g2.drawImage(img, 0, 0, getWidth(), getHeight() - CAPTION_H, null);
            // sprite: fila 1 = 1-5, fila 2 = 6,7,8,9,0 (celdas de 28x55)
            int d = Math.abs(score) % 10;
            int idx = d == 0 ? 9 : d - 1;
            int cw = digits.getWidth() / 5, ch = digits.getHeight() / 2;
            int sx = (idx % 5) * cw, sy = (idx / 5) * ch;
            int dw = (int) (cw * SCALE * 1.5), dh = (int) (ch * SCALE * 1.5);
            int cx = (int) ((enemy ? 185 : 195) * SCALE);
            int cy = (int) ((enemy ? 47 : 137) * SCALE);
            g2.drawImage(digits, cx - dw / 2, cy - dh / 2, cx + dw / 2, cy + dh / 2, sx, sy, sx + cw, sy + ch, null);
            g2.dispose();
        }
    }

    /** Botón con estilo dorado/azul (también usado por CardPanel). */
    static class ArenaButton extends JButton {
        private boolean hover;

        ArenaButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean on = isEnabled();
            Color top = !on ? new Color(45, 50, 62) : hover ? BLUE : new Color(50, 105, 190);
            Color bottom = !on ? new Color(30, 34, 44) : BLUE_DARK;
            g2.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.setColor(on ? GOLD : new Color(80, 80, 90));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 14, 14);
            g2.dispose();
            setForeground(on ? Color.WHITE : new Color(130, 135, 150));
            super.paintComponent(g);
        }
    }

    /** Registra los ActionListener de los botones "Iniciar duelo" y "Cargar nuevas cartas". */
    private void wireListeners() {
        btnStart.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startDuel();
            }
        });
        btnHowTo.addActionListener(e -> showHowToPlay());
        btnRematch.addActionListener(e -> rematch());
        btnReload.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadCards();
            }
        });
    }

    // ------------------------------------------------------------------ carga de cartas (en segundo plano)

    private void loadCards() {
        if (state == ViewState.LOADING || state == ViewState.IN_PROGRESS) {
            return; // no se recargan cartas durante una carga ni en pleno duelo
        }
        setState(ViewState.LOADING);
        duel = null;
        playerPanels.clear();
        aiPanels.clear();
        playerRow.removeAll();
        aiRow.removeAll();
        playerRow.repaint();
        aiRow.repaint();
        updateScore(0, 0);
        progress.setVisible(true);
        showResult(" ", TEXT_DIM);
        setStatus("Cargando cartas 0/" + TOTAL_CARDS + "...", TEXT_DIM);
        appendLog("Cargando cartas desde YGOProDeck...");
        new LoadCardsWorker().execute();
    }

    /** Descarga las 6 cartas (datos + imagen) sin bloquear la interfaz. */
    private class LoadCardsWorker extends SwingWorker<List<Card>, Integer> {
        @Override
        protected List<Card> doInBackground() throws YgoApiException {
            List<Card> cards = new ArrayList<>();
            for (int i = 0; i < TOTAL_CARDS; i++) {
                cards.add(api.fetchCardWithImage());
                publish(cards.size());
            }
            return cards;
        }

        @Override
        protected void process(List<Integer> chunks) { // se ejecuta en el EDT
            setStatus("Cargando cartas " + chunks.get(chunks.size() - 1) + "/" + TOTAL_CARDS + "...",
                    TEXT_DIM);
        }

        @Override
        protected void done() { // se ejecuta en el EDT
            progress.setVisible(false);
            try {
                onCardsLoaded(get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                setState(ViewState.ERROR);
            } catch (ExecutionException e) {
                setState(ViewState.ERROR);
                showError(e.getCause());
            }
        }
    }

    private void onCardsLoaded(List<Card> cards) {
        String[] arenas = {"field-basic.png", "field-extra.png", "field-pendulum.png"};
        arena = arenas[new java.util.Random().nextInt(arenas.length)]; // campo distinto en cada carga
        setupDuel(cards.subList(0, Duel.CARDS_PER_SIDE), cards.subList(Duel.CARDS_PER_SIDE, TOTAL_CARDS));
        setState(ViewState.READY);
        setStatus("Cartas listas. Pulsa \"Iniciar duelo\".", OK);
        appendLog("Las " + TOTAL_CARDS + " cartas se cargaron correctamente.");
    }

    /** Crea los paneles y un Duel nuevo con las cartas dadas (sirve para carga inicial y revancha). */
    private void setupDuel(List<Card> playerCards, List<Card> aiCards) {
        ActionListener chooseListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JButton source = (JButton) e.getSource();
                for (CardPanel p : playerPanels) {
                    if (SwingUtilities.isDescendingFrom(source, p)) {
                        onCardChosen(p);
                        return;
                    }
                }
            }
        };
        for (Card c : playerCards) {
            CardPanel p = CardPanel.forPlayer(c);
            p.setPlayable(false); // se habilitan al iniciar el duelo
            p.addChooseListener(chooseListener);
            playerPanels.add(p);
            playerRow.add(p);
        }
        for (Card c : aiCards) {
            CardPanel p = CardPanel.forAi(c);
            aiPanels.add(p);
            aiRow.add(p);
        }
        playerRow.revalidate();
        aiRow.revalidate();

        duel = new Duel(playerCards, aiCards);
        duel.addListener(this);
    }

    /** Revancha: mismas 6 cartas, marcador en 0 y duelo nuevo (sin consultar la API). */
    private void rematch() {
        if (state != ViewState.FINISHED || duel == null) {
            return; // la revancha solo existe cuando terminó un duelo
        }
        List<Card> playerCards = new ArrayList<>(duel.getPlayerHand());
        List<Card> aiCards = new ArrayList<>(duel.getAiHand());
        playerPanels.clear();
        aiPanels.clear();
        playerRow.removeAll();
        aiRow.removeAll();
        updateScore(0, 0);
        appendLog("");
        appendLog("=== REVANCHA: mismas cartas, marcador reiniciado ===");
        showResult(" ", TEXT_DIM);
        setupDuel(playerCards, aiCards);
        playerRow.repaint();
        aiRow.repaint();
        startDuel();
    }

    private void showHowToPlay() {
        String html = "<html><body style='width:380px;color:#E1E6F0;font-family:SansSerif;font-size:12px'>"
                + "<h2 style='color:#D4AF37'>Cómo jugar</h2>"
                + "<p>Cada bando recibe " + Duel.CARDS_PER_SIDE + " cartas Monster. Gana el duelo quien llegue primero a "
                + Duel.ROUNDS_TO_WIN + " rondas.</p>"
                + "<ol><li>Pulsa <b>Iniciar duelo</b>.</li>"
                + "<li>En cada ronda elige una carta, su posición (<b>Atacar</b> o <b>Defender</b>) y pulsa <b>Elegir carta</b>.</li>"
                + "<li>La IA elige carta y posición al azar; su carta se revela al resolver la ronda.</li>"
                + "<li>Cada carta se usa una sola vez.</li></ol>"
                + "<p style='color:#D4AF37'><b>Reglas</b></p>"
                + "<ul><li>Ambas en ATAQUE: gana el mayor ATK.</li>"
                + "<li>Ambas en DEFENSA: gana el mayor DEF.</li>"
                + "<li>Una ataca y otra defiende: el atacante gana solo si su ATK es mayor que el DEF del defensor; si no, gana el defensor.</li>"
                + "<li>Empate exacto: desempate al azar.</li></ul>"
                + "<p><b>Revancha</b> repite el duelo con las mismas cartas; <b>Cargar nuevas cartas</b> descarga otras.</p>"
                + "</body></html>";
        JDialog dialog = new JDialog(this, "Cómo jugar", true);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(BG_PANEL);
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD_DARK, 2),
                BorderFactory.createEmptyBorder(12, 18, 14, 18)));
        content.add(new JLabel(html), BorderLayout.CENTER);
        JButton close = new ArenaButton("Entendido");
        close.addActionListener(e -> dialog.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.setOpaque(false);
        south.add(close);
        content.add(south, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showError(Throwable error) {
        String msg = (error instanceof YgoApiException)
                ? error.getMessage()
                : "Error inesperado: " + error;
        setStatus("Error: " + msg + " Pulsa \"Cargar nuevas cartas\" para reintentar.", ERR);
        appendLog("ERROR: " + msg);
        JOptionPane.showMessageDialog(this, msg, "No se pudo cargar", JOptionPane.ERROR_MESSAGE);
    }

    // ------------------------------------------------------------------ flujo del duelo

    private void startDuel() {
        if (duel == null || duel.isStarted() || (state != ViewState.READY && state != ViewState.FINISHED)) {
            return;
        }
        duel.start();
        setState(ViewState.IN_PROGRESS);
        appendLog("");
        appendLog("=== DUELO INICIADO (gana quien llegue a " + Duel.ROUNDS_TO_WIN + " rondas) ===");
        announceRoundStart();
        setAllPlayable(true);
    }

    /** Se ejecuta al pulsar "Elegir carta" en una carta del jugador. */
    private void onCardChosen(CardPanel panel) {
        if (state != ViewState.IN_PROGRESS || duel == null || !duel.isStarted() || duel.isFinished()) {
            return;
        }
        setAllPlayable(false);
        lastPlayerPanel = panel;
        duel.playRound(panel.getCard(), panel.getSelectedPosition()); // notifica por BattleListener
        panel.markUsed();
        if (!duel.isFinished()) {
            announceRoundStart();
            setAllPlayable(true);
        }
    }

    private void announceRoundStart() {
        int next = duel.getRound() + 1;
        String starter = duel.isPlayerStarter() ? Duel.PLAYER : Duel.AI;
        appendLog("");
        appendLog("--- Ronda " + next + ": elige primero " + starter + " ---");
        setStatus("Ronda " + next + " de " + Duel.CARDS_PER_SIDE + ": elige una carta y su posición (primero a "
                + Duel.ROUNDS_TO_WIN + ").", TEXT);
    }

    private void setAllPlayable(boolean playable) {
        for (CardPanel p : playerPanels) {
            p.setPlayable(playable);
        }
    }

    // ------------------------------------------------------------------ BattleListener

    @Override
    public void onTurn(String playerCard, String aiCard, String winner) {
        final Card aiCardObj = duel.getLastAiCard();
        final String detail = duel.getLastDetail();
        runOnEdt(() -> {
            for (CardPanel p : aiPanels) {
                if (p.getCard() == aiCardObj) {
                    p.reveal(); // muestra la carta que jugó la IA
                    p.markResult(Duel.AI.equals(winner));
                }
            }
            if (lastPlayerPanel != null) {
                lastPlayerPanel.markResult(Duel.PLAYER.equals(winner));
            }
            boolean won = Duel.PLAYER.equals(winner);
            showResult("Ronda " + duel.getRound() + ": " + (won ? "¡Ganaste!" : "Ganó la IA") + "  ·  " + detail,
                    won ? OK : ERR);
            appendLog("Jugador jugó:  " + playerCard);
            appendLog("IA jugó:  " + aiCard);
            appendLog("Resultado:     " + detail);
            appendLog("Ganador de la ronda: " + winner);
        });
    }

    @Override
    public void onScoreChanged(int playerScore, int aiScore) {
        runOnEdt(() -> {
            updateScore(playerScore, aiScore);
            appendLog("Puntaje -> Jugador " + playerScore + " | IA " + aiScore);
        });
    }

    @Override
    public void onDuelEnded(String winner) {
        runOnEdt(() -> {
            appendLog("");
            appendLog("=== GANADOR DEL DUELO: " + winner.toUpperCase() + " ===");
            setState(ViewState.FINISHED);
            setStatus("Duelo terminado. Ganador: " + winner + ". Pulsa \"Revancha\" o \"Cargar nuevas cartas\".",
                    BLUE);
            setAllPlayable(false);
            for (CardPanel p : aiPanels) {
                p.revealUnplayed(); // muestra las cartas de la IA que no se jugaron
            }
            for (CardPanel p : playerPanels) {
                p.revealUnplayed();
            }
            // invokeLater: el diálogo no debe interrumpir el resto del procesamiento de la ronda
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    Duel.PLAYER.equals(winner) ? "¡Ganaste el duelo!" : "La IA ganó el duelo.",
                    "Fin del duelo", JOptionPane.INFORMATION_MESSAGE));
        });
    }

    // ------------------------------------------------------------------ utilidades

    private void updateScore(int player, int ai) {
        badgePlayer.setScore(player);
        badgeAi.setScore(ai);
    }

    private void showResult(String text, Color color) {
        lblResult.setForeground(color);
        lblResult.setText(text);
    }

    private void setStatus(String text, Color color) {
        lblStatus.setForeground(color);
        lblStatus.setText(text);
    }

    private void appendLog(String line) {
        log.append(line + "\n");
        log.setCaretPosition(log.getDocument().getLength()); // autoscroll al último mensaje
    }

    private static void runOnEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
}
