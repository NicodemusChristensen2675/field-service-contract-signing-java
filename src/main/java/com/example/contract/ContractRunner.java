package com.example.contract;

public final class ContractRunner {
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("INFRAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("INFRAI_API_KEY is required");
        InfraiClient client = new InfraiClient(apiKey);
        String bucket = "field-service-contracts";
        ContractService service = new ContractService(client, bucket);
        ContractService.WorkOrder order = new ContractService.WorkOrder("WO-1042", "SIGNED", "T. Rivera", new String[]{"photos/WO-1042-before.jpg"}, "customer accepted scope");
        System.out.println(service.signAndLink(order, System.getenv("CERT_PEM"), System.getenv("KEY_PEM")));
    }
}
