package edu.univalle.yugioh.api;

/**
 * Error al consultar la API de YGOProDeck. El mensaje es apto para mostrarse al usuario
 * (por ejemplo "Error de red" o "No se pudo cargar la carta").
 */
public class YgoApiException extends Exception {

    private static final long serialVersionUID = 1L;

    public YgoApiException(String message) {
        super(message);
    }

    public YgoApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
