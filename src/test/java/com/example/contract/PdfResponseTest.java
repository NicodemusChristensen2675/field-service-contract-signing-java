package com.example.contract;

public final class PdfResponseTest {
    public static void main(String[] args) throws Exception {
        ContractService service = new ContractService(null, "unused");
        String encoded = "JVBERi0xLjQ=";
        for (String field : new String[]{"pdf_base64", "data_base64", "signed_pdf_base64", "signed_pdf"}) {
            if (!encoded.equals(service.decodeBase64Payload("{\"data\":{\"" + field + "\":\"" + encoded + "\"}}", "pdf.sign")))
                throw new AssertionError(field);
        }
        if (!encoded.equals(service.decodeBase64Payload("{\"data\":{\"url\":\"data:application\\/pdf;base64," + encoded + "\"}}", "pdf.generate")))
            throw new AssertionError("data URI");
        String pem = "-----BEGIN CERTIFICATE-----\r\n\"\\\t\u0000\n-----END CERTIFICATE-----";
        String json = ContractService.jsonValue(pem);
        if (!json.contains("\\u000a") || !json.contains("\\u000d") || !json.contains("\\\"") || !json.contains("\\\\"))
            throw new AssertionError("PEM escaping");
        for (char c : json.toCharArray()) if (c < 32) throw new AssertionError("Unescaped control character");
        if (args.length > 0) service.decodeBase64Payload(java.nio.file.Files.readString(java.nio.file.Path.of(args[0])), "pdf.generate");
        System.out.println("PDF response and PEM serialization checks passed");
    }
}
