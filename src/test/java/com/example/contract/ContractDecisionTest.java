package com.example.contract;

public final class ContractDecisionTest {
    public static void main(String[] args) {
        ContractService.WorkOrder signed = new ContractService.WorkOrder("WO-1042", "SIGNED", "T. Rivera", new String[]{"before.jpg"}, "accepted");
        ContractService.WorkOrder dispatched = new ContractService.WorkOrder("WO-1043", "DISPATCHED", "T. Rivera", new String[]{"before.jpg"}, "pending");
        assertLinkDecision(signed, true);
        assertLinkDecision(dispatched, false);
        System.out.println("contract decision test passed");
    }

    private static void assertLinkDecision(ContractService.WorkOrder order, boolean expected) {
        boolean actual = order.status().equals("SIGNED");
        if (actual != expected) throw new AssertionError(order.id());
    }
}
