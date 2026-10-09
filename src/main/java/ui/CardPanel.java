package edu.univalle.yugioh.ui;

import edu.univalle.yugioh.model.Card;
import edu.univalle.yugioh.model.Position;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

/**
 * Muestra una carta: imagen, nombre, ATK y DEF.
 * Variante del jugador: incluye selector de posición y botón "Elegir carta".
 * Variante de la IA: aparece boca abajo ("?") hasta que se revela.
 */
public class CardPanel extends JPanel {

    private final Card card;
    private final boolean hidden;
    private final JLabel imageLabel = new JLabel();
    private final JLabel nameLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel statsLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel stateLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JRadioButton rbAttack = new JRadioButton("Atacar", true);
    private final JRadioButton rbDefense = new JRadioButton("Defender");
    private final JButton btnChoose = new MainFrame.ArenaButton("Elegir carta");
    private boolean used;
    private Color accent = MainFrame.GOLD;

    /** Carta del jugador (visible e interactiva). */
    public static CardPanel forPlayer(Card card) { return new CardPanel(card, false); }

    /** Carta de la IA (oculta hasta que se juega). */
    public static CardPanel forAi(Card card) { return new CardPanel(card, true); }

    private CardPanel(Card card, boolean hidden) {
        this.card = card;
        this.hidden = hidden;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        accent = hidden ? MainFrame.BLUE : MainFrame.GOLD;
        setBorder(BorderFactory.createEmptyBorder(9, 8, 8, 8));

        imageLabel.setAlignmentX(CENTER_ALIGNMENT);
        nameLabel.setAlignmentX(CENTER_ALIGNMENT);
        statsLabel.setAlignmentX(CENTER_ALIGNMENT);
        stateLabel.setAlignmentX(CENTER_ALIGNMENT);
        statsLabel.setFont(statsLabel.getFont().deriveFont(Font.BOLD));
        statsLabel.setForeground(MainFrame.GOLD);
        nameLabel.setForeground(MainFrame.TEXT);
        stateLabel.setForeground(MainFrame.TEXT_DIM);

        add(imageLabel);
        add(Box.createVerticalStrut(4));
        add(nameLabel);
        add(statsLabel);
        add(stateLabel);

        if (hidden) {
            imageLabel.setIcon(placeholder());
            statsLabel.setText("ATK ? / DEF ?");
            nameLabel.setText(html("???"));
        } else {
            showCard();
            ButtonGroup group = new ButtonGroup();
            group.add(rbAttack);
            group.add(rbDefense);
            JPanel radios = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            radios.setOpaque(false);
            for (JRadioButton rb : new JRadioButton[]{rbAttack, rbDefense}) {
                rb.addActionListener(e -> updateStats());
                rb.setOpaque(false);
                rb.setForeground(MainFrame.TEXT);
                rb.setFocusPainted(false);
                rb.setFont(rb.getFont().deriveFont(11f));
                rb.setMargin(new java.awt.Insets(0, 0, 0, 2));
            }
            radios.add(rbAttack);
            radios.add(rbDefense);
            btnChoose.setAlignmentX(CENTER_ALIGNMENT);
            add(radios);
            add(Box.createVerticalStrut(4));
            add(btnChoose);
        }
    }

    public Card getCard() { return card; }

    /** Posición elegida por el jugador. */
    public Position getSelectedPosition() {
        return rbDefense.isSelected() ? Position.DEFENSA : Position.ATAQUE;
    }

    /** Registra el ActionListener del botón "Elegir carta". */
    public void addChooseListener(ActionListener listener) {
        btnChoose.addActionListener(listener);
    }

    /** Habilita o deshabilita la elección (una carta usada nunca se habilita). */
    public void setPlayable(boolean playable) {
        boolean enabled = playable && !used && !hidden;
        btnChoose.setEnabled(enabled);
        rbAttack.setEnabled(enabled);
        rbDefense.setEnabled(enabled);
    }

    /** Marca la carta como usada (se ve atenuada y deja de ser elegible). */
    public void markUsed() {
        used = true;
        setPlayable(false);
        if (stateLabel.getText().trim().isEmpty()) {
            stateLabel.setText("Usada");
            accent = new Color(90, 95, 110);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, new Color(28, 40, 72), 0, h, new Color(12, 18, 36)));
        g2.fillRoundRect(2, 2, w - 5, h - 5, 16, 16);
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawRoundRect(2, 2, w - 5, h - 5, 16, 16);
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(6, 6, w - 13, h - 13, 12, 12);
        g2.dispose();
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (used) { // atenuar cartas ya usadas
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(new Color(5, 8, 16, 85));
            g2.fillRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 16, 16);
            g2.dispose();
        }
    }

    /** Revela la carta de la IA y la marca como usada. */
    public void reveal() {
        showCard();
        markUsed();
    }

    private void showCard() {
        imageLabel.setIcon(card.getImage() != null ? new ImageIcon(card.getImage()) : placeholder());
        nameLabel.setText(html(card.getName()));
        updateStats();
        setToolTipText(card.getName() + "  (ATK " + card.getAtk() + " / DEF " + card.getDef() + ")");
    }

    /** Resalta el valor que contaría según la posición elegida (ATK si ataca, DEF si defiende). */
    private void updateStats() {
        if (hidden) {
            statsLabel.setText("ATK " + card.getAtk() + " / DEF " + card.getDef());
            return;
        }
        boolean atk = !rbDefense.isSelected();
        String on = atk ? "#D4AF37" : "#4690E6";
        String off = "#7A8499";
        statsLabel.setText("<html><span style='color:" + (atk ? on : off) + "'>" + (atk ? "<b>ATK " + card.getAtk() + "</b>" : "ATK " + card.getAtk())
                + "</span> / <span style='color:" + (atk ? off : on) + "'>" + (atk ? "DEF " + card.getDef() : "<b>DEF " + card.getDef() + "</b>")
                + "</span></html>");
    }

    /** Resultado de la ronda para esta carta: borde verde (ganó) o rojo (perdió). */
    public void markResult(boolean won) {
        used = true;
        setPlayable(false);
        accent = won ? MainFrame.OK : MainFrame.ERR;
        stateLabel.setForeground(accent);
        stateLabel.setText(won ? "¡Ganó!" : "Perdió");
        repaint();
    }

    /** Al terminar el duelo: muestra la carta (si estaba oculta) y la marca como no jugada. */
    public void revealUnplayed() {
        if (used) {
            return;
        }
        if (hidden) {
            showCard();
        }
        used = true;
        setPlayable(false);
        accent = new Color(90, 95, 110);
        stateLabel.setText("Sin jugar");
        repaint();
    }

    private static String html(String text) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><div style='width:120px;text-align:center'>" + safe + "</div></html>";
    }

    /** Dorso de carta genérico para las cartas ocultas. */
    private static ImageIcon placeholder() {
        BufferedImage deck = MainFrame.loadImage("deck.png"); // dorso real de carta
        if (deck != null) {
            BufferedImage scaled = new BufferedImage(Card.IMAGE_WIDTH, Card.IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(deck, 0, 0, Card.IMAGE_WIDTH, Card.IMAGE_HEIGHT, null);
            g.dispose();
            return new ImageIcon(scaled);
        }
        BufferedImage img = new BufferedImage(Card.IMAGE_WIDTH, Card.IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(20, 40, 90));
        g.fillRoundRect(0, 0, Card.IMAGE_WIDTH - 1, Card.IMAGE_HEIGHT - 1, 12, 12);
        g.setColor(MainFrame.GOLD);
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(3, 3, Card.IMAGE_WIDTH - 7, Card.IMAGE_HEIGHT - 7, 10, 10);
        g.setFont(new Font(Font.SERIF, Font.BOLD, 56));
        g.drawString("?", Card.IMAGE_WIDTH / 2 - 16, Card.IMAGE_HEIGHT / 2 + 20);
        g.dispose();
        return new ImageIcon(img);
    }
}
