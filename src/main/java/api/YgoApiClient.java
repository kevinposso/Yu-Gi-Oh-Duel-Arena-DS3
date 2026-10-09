package edu.univalle.yugioh.api;

import edu.univalle.yugioh.model.Card;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

/**
 * Cliente de la API YGOProDeck.
 * <p>
 * IMPORTANTE: los métodos de esta clase son BLOQUEANTES (hacen red). Deben llamarse
 * desde un hilo en segundo plano (por ejemplo un SwingWorker), nunca desde el EDT.
 */
public class YgoApiClient {

    /** Endpoint de carta aleatoria (redirige a cardinfo.php?num=1&offset=0&sort=random). */
    private static final String RANDOM_CARD_URL = "https://db.ygoprodeck.com/api/v7/randomcard.php";
    /** Intentos máximos para obtener una carta Monster válida. */
    private static final int MAX_ATTEMPTS = 5;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL) // randomcard.php responde con una redirección
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Obtiene una carta Monster completa (datos + imagen ya descargada y escalada).
     *
     * @throws YgoApiException si hay error de red o no se obtiene una carta válida
     */
    public Card fetchCardWithImage() throws YgoApiException {
        Card card = fetchRandomMonster();
        loadImage(card);
        return card;
    }

    /**
     * Pide cartas aleatorias hasta obtener un Monster con ATK y DEF.
     * Si la carta no es Monster (Spell/Trap) o no tiene ATK/DEF (Link), se vuelve a solicitar.
     */
    public Card fetchRandomMonster() throws YgoApiException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Card card = parseMonster(requestRandomCard());
            if (card != null) {
                return card;
            }
        }
        throw new YgoApiException("No se pudo cargar la carta: no se obtuvo un Monster válido tras "
                + MAX_ATTEMPTS + " intentos.");
    }

    /** Descarga la imagen de la carta (una sola vez), la escala y la guarda en el modelo. */
    public void loadImage(Card card) throws YgoApiException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(card.getImageUrl()))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = http.send(request, BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new YgoApiException("No se pudo cargar la carta: la imagen respondió HTTP "
                        + response.statusCode() + ".");
            }
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (original == null) {
                throw new YgoApiException("No se pudo cargar la carta: imagen inválida.");
            }
            card.setImage(scale(original));
        } catch (IOException e) {
            throw new YgoApiException("Error de red al descargar la imagen: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YgoApiException("Error de red: operación interrumpida.", e);
        }
    }

    // ------------------------------------------------------------------ privados

    /** Hace la petición HTTP y devuelve el objeto JSON de la carta. */
    private JSONObject requestRandomCard() throws YgoApiException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(RANDOM_CARD_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new YgoApiException("Error de red: la API respondió HTTP " + response.statusCode() + ".");
            }
            JSONObject root = new JSONObject(response.body());
            if (root.has("data")) { // formato real: {"data":[{...carta...}],"meta":{...}}
                JSONArray data = root.getJSONArray("data");
                if (data.length() == 0) {
                    throw new YgoApiException("No se pudo cargar la carta: respuesta vacía.");
                }
                return data.getJSONObject(0);
            }
            return root; // por si la API devolviera la carta directamente
        } catch (IOException e) {
            throw new YgoApiException("Error de red: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YgoApiException("Error de red: operación interrumpida.", e);
        } catch (JSONException e) {
            throw new YgoApiException("No se pudo cargar la carta: JSON inválido.", e);
        }
    }

    /**
     * Convierte el JSON en {@link Card}. Devuelve null si no es un Monster utilizable
     * (no es Monster, no tiene ATK/DEF o no tiene imagen).
     */
    static Card parseMonster(JSONObject json) {
        if (!json.optString("type", "").contains("Monster")) {
            return null;
        }
        if (json.isNull("atk") || json.isNull("def")) { // p. ej. Link Monsters no tienen DEF
            return null;
        }
        JSONArray images = json.optJSONArray("card_images");
        if (images == null || images.length() == 0) {
            return null;
        }
        try {
            JSONObject firstImage = images.getJSONObject(0);
            String url = firstImage.optString("image_url_small", firstImage.optString("image_url", ""));
            if (url.isEmpty()) {
                return null;
            }
            return new Card(json.optString("name", "Desconocida"), json.getInt("atk"), json.getInt("def"), url);
        } catch (JSONException e) { // ATK/DEF no numéricos (p. ej. "?"): se descarta y se pide otra carta
            return null;
        }
    }

    /** Escala la imagen al tamaño de visualización (se hace en el hilo de fondo, no en el EDT). */
    private static Image scale(BufferedImage src) {
        BufferedImage out = new BufferedImage(Card.IMAGE_WIDTH, Card.IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, Card.IMAGE_WIDTH, Card.IMAGE_HEIGHT, null);
        g.dispose();
        return out;
    }
}
