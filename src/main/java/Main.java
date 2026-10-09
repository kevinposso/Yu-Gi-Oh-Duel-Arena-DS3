import edu.univalle.yugioh.ui.StartMenu;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Punto de entrada: abre el menú inicial en el hilo de eventos de Swing (EDT). */
public class Main {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // si falla se usa el Look & Feel por defecto
        }
        SwingUtilities.invokeLater(() -> new StartMenu().setVisible(true));
    }
}
