package edu.univalle.yugioh.model;

import java.awt.Image;

/**
 * Modelo de una carta Monster de Yu-Gi-Oh!.
 * Los datos básicos (nombre, ATK, DEF, URL) son inmutables; la imagen se asigna
 * una sola vez después de descargarla en un hilo en segundo plano.
 */
public class Card {

    /** Ancho (px) al que se escala la imagen de la carta. */
    public static final int IMAGE_WIDTH = 100;
    /** Alto (px) al que se escala la imagen de la carta. */
    public static final int IMAGE_HEIGHT = 146;

    private final String name;
    private final int atk;
    private final int def;
    private final String imageUrl;
    private volatile Image image; // se asigna desde el hilo de descarga

    public Card(String name, int atk, int def, String imageUrl) {
        this.name = name;
        this.atk = atk;
        this.def = def;
        this.imageUrl = imageUrl;
    }

    public String getName() { return name; }
    public int getAtk() { return atk; }
    public int getDef() { return def; }
    public String getImageUrl() { return imageUrl; }
    public Image getImage() { return image; }
    public void setImage(Image image) { this.image = image; }

    /** Texto corto: "Nombre (ATK x / DEF y)". */
    public String describe() {
        return name + " (ATK " + atk + " / DEF " + def + ")";
    }

    @Override
    public String toString() {
        return describe();
    }
}
