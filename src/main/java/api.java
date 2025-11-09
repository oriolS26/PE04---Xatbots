import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.google.gson.*;
import java.util.regex.*;
import java.util.*;

public class api {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-3.5-turbo";
    private static final double DEFAULT_TEMPERATURE = 0.7;

    private final List<Map<String, String>> conversation = new ArrayList<>();
    private String model = DEFAULT_MODEL;
    private double temperature = DEFAULT_TEMPERATURE;
    private String apiKey;

    public api() {
        apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("Error: No s'ha trobat la variable d'entorn OPENAI_API_KEY.");
            System.exit(1);
        }
        conversation.add(Map.of("role", "system", "content", "Ets un assistent útil i amable."));
    }

    public void start() {
        Scanner sc = new Scanner(System.in);
        System.out.println("--- Xat amb ChatGPT ---");
        System.out.println("Escriu /help per veure les opcions o /exit per sortir.");

        while (true) {
            System.out.print("Tu> ");
            String input = sc.nextLine().trim();

            if (input.isEmpty()) continue;
            if (input.equalsIgnoreCase("/exit")) break;

            if (input.equalsIgnoreCase("/help")) {
                printHelp();
                continue;
            }

            if (input.startsWith("/role ")) {
                String role = input.substring(6).trim();
                setSystemRole(role);
                System.out.println("[SISTEMA] Rol canviat: " + role);
                continue;
            }

            if (input.startsWith("/model ")) {
                model = input.substring(7).trim();
                System.out.println("[SISTEMA] Model canviat a: " + model);
                continue;
            }

            conversation.add(Map.of("role", "user", "content", input));
            System.out.println("[TU] " + input);

            try {
                String response = getChatGPTResponse();
                System.out.println("[ASSISTENT] " + response);
                conversation.add(Map.of("role", "assistant", "content", response));
            } catch (Exception e) {
                System.out.println("[ERROR] No s'ha pogut obtenir resposta: " + e.getMessage());
            }
        }
        sc.close();
        System.out.println("Sessió finalitzada.");
    }

    private String getChatGPTResponse() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

    // Prepara els missatges (historial de conversa)
    StringBuilder messagesJson = new StringBuilder("[");
    for (int i = 0; i < conversation.size(); i++) {
        Map<String, String> msg = conversation.get(i);
        messagesJson.append(String.format("{\"role\":\"%s\",\"content\":\"%s\"}",
                escapeJson(msg.get("role")), escapeJson(msg.get("content"))));
        if (i < conversation.size() - 1) messagesJson.append(",");
    }
    messagesJson.append("]");

    // Cos de la petició
    String body = String.format("{\"model\":\"%s\",\"temperature\":%s,\"messages\":%s}",
            model, temperature, messagesJson);

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
        throw new RuntimeException("Error HTTP " + response.statusCode() + ": " + response.body());
    }
    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
    JsonArray choices = json.getAsJsonArray("choices");
    if (choices == null || choices.size() == 0) {
        throw new RuntimeException("No s'han rebut opcions vàlides: " + response.body());
    }

    JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
    if (message == null || !message.has("content")) {
        throw new RuntimeException("Resposta invàlida: " + response.body());
    }

    return message.get("content").getAsString();
}
    private void setSystemRole(String text) {
        if (!conversation.isEmpty() && conversation.get(0).get("role").equals("system")) {
            conversation.set(0, Map.of("role", "system", "content", text));
        } else {
            conversation.add(0, Map.of("role", "system", "content", text));
        }
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private void printHelp() {
        System.out.println("""
Comandes disponibles:
  /help          - Mostra aquesta ajuda
  /exit          - Finalitza la sessió
  /role <text>   - Defineix el rol del sistema
  /model <nom>   - Canvia el model d'OpenAI utilitzat
Exemples:
  /role Ets un professor de matemàtiques.
  /model gpt-3.5-turbo
""");
    }

    public static void main(String[] args) {
        new api().start();
    }
}
