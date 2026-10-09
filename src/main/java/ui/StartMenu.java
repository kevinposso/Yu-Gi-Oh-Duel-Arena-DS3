package edu.univalle.yugioh.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Menú inicial: imagen de portada con los botones "Iniciar" y "Salir". */
public class StartMenu extends JFrame {

    public StartMenu() {
        super("Yu-Gi-Oh! Duel Arena");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(buildContent(this::startGame, this::exitGame));
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private void startGame() {
        MainFrame game = new MainFrame(); // primero se crea y muestra el juego y luego se cierra el menú
        game.setVisible(true);
        dispose();
    }

    private void exitGame() {
        dispose();
        System.exit(0);
    }

    /** Panel del menú (separado para poder reutilizarlo y probarlo sin abrir la ventana). */
    static JPanel buildContent(Runnable onStart, Runnable onExit) {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                BufferedImage bg = MainFrame.loadImage("menu-background.jpg");
                if (bg == null) { // sin imagen: fondo oscuro con título
                    g2.setPaint(new GradientPaint(0, 0, new Color(6, 9, 18), 0, getHeight(), new Color(16, 28, 56)));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(MainFrame.GOLD);
                    g2.setFont(new Font(Font.SERIF, Font.BOLD, 40));
                    String t = "YU-GI-OH! DUEL ARENA";
                    g2.drawString(t, (getWidth() - g2.getFontMetrics().stringWidth(t)) / 2, getHeight() / 2);
                } else {
                    double k = Math.max(getWidth() / (double) bg.getWidth(), getHeight() / (double) bg.getHeight());
                    int w = (int) (bg.getWidth() * k), h = (int) (bg.getHeight() * k);
                    g2.drawImage(bg, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
                }
                // degradado inferior para que los botones se lean sobre la imagen
                g2.setPaint(new GradientPaint(0, getHeight() * 0.65f, new Color(0, 0, 0, 0),
                        0, getHeight(), new Color(0, 0, 0, 200)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        root.setPreferredSize(new Dimension(960, 640));

        JButton start = menuButton("Iniciar");
        JButton exit = menuButton("Salir");
        start.addActionListener(e -> onStart.run());
        exit.addActionListener(e -> onExit.run());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 28, 0));
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 0, 44, 0));
        buttons.add(start);
        buttons.add(exit);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private static JButton menuButton(String text) {
        JButton b = new MainFrame.ArenaButton(text);
        b.setFont(new Font(Font.SERIF, Font.BOLD, 20));
        b.setBorder(BorderFactory.createEmptyBorder(12, 48, 12, 48));
        return b;
    }
}
