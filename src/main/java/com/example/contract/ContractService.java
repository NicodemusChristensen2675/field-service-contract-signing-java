package com.example.contract;

import java.io.IOException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContractService {
    private final InfraiClient client;
    private final String bucket;

    public ContractService(InfraiClient client, String bucket) { this.client = client; this.bucket = bucket; }

    public String signAndLink(WorkOrder order, String certificatePem, String privateKeyPem)
            throws IOException, InterruptedException {
        if (!order.status().equals("SIGNED")) throw new IllegalStateException("contract is not signed");
        String generated = client.post("/pdf/generate", "{\"markdown\":\"Contract " + order.id() + "\",\"page_size\":\"A4\",\"orientation\":\"portrait\",\"store\":false}", order.id() + "-generate");
        String pdf = decodeGeneratedPdf(generated);
        String signed = client.post("/pdf/sign", "{\"pdf\":" + jsonValue(pdf) + ",\"cert_pem\":" + jsonValue(certificatePem) + ",\"key_pem\":" + jsonValue(privateKeyPem) + ",\"reason\":\"field service approval\",\"location\":\"operations\"}", order.id() + "-sign");
        String signedPdf = decodeBase64Payload(signed, "pdf.sign");
        String objectKey = "contracts/" + order.id() + "/signed.pdf";
        client.put("/storage/object/put/" + bucket + "/" + objectKey, "{\"data_base64\":" + jsonValue(signedPdf) + ",\"content_type\":\"application/pdf\",\"idempotency_key\":\"" + order.id() + "-object\"}", order.id() + "-object");
        String link = client.post("/storage/object/presign/" + bucket + "/" + objectKey, "{\"op\":\"get\",\"expires_seconds\":900,\"response_disposition\":\"attachment\"}", order.id() + "-link");
        return link;
    }

    static String jsonValue(String value) {
        if (value == null) return "null";
        StringBuilder json = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                default -> {
                    if (c < 0x20) json.append(String.format("\\u%04x", (int) c));
                    else json.append(c);
                }
            }
        }
        return json.append('"').toString();
    }

    private String decodeGeneratedPdf(String response) throws IOException, InterruptedException {
        return decodeBase64Payload(response, "pdf.generate");
    }

    String decodeBase64Payload(String response, String operation) throws IOException, InterruptedException {
        Matcher matcher = Pattern.compile("\"(?:url|signed_pdf|signed_pdf_base64|pdf_base64|data_base64)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*+)\"").matcher(response);
        if (!matcher.find()) throw new IllegalStateException(operation + " response did not contain a PDF");
        String value = unescape(matcher.group(1));
        if (value.startsWith("data:application/pdf;base64,")) {
            value = value.substring("data:application/pdf;base64,".length());
        } else if (value.startsWith("https://")) {
            java.net.http.HttpResponse<byte[]> download = java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create(value))
                            .timeout(java.time.Duration.ofSeconds(30)).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if (download.statusCode() != 200) throw new IOException("PDF download returned " + download.statusCode());
            value = Base64.getEncoder().encodeToString(download.body());
        }
        return decodeBase64(value, operation);
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\') { result.append(c); continue; }
            char escape = value.charAt(++i);
            switch (escape) {
                case '"', '\\', '/' -> result.append(escape);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    result.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> throw new IllegalArgumentException("Invalid JSON escape");
            }
        }
        return result.toString();
    }

    private String decodeBase64(String encoded, String operation) {
        try {
            Base64.getDecoder().decode(encoded);
            return encoded;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(operation + " response contained invalid base64 PDF", e);
        }
    }

    public record WorkOrder(String id, String status, String technician, String[] photoKeys, String followUp) {}
}
