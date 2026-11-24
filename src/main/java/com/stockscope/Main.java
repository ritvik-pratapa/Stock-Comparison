package com.stockscope;

/**
 * Entry point for StockScope.
 *
 * Right now this only prints a banner, which proves the project compiles
 * and runs. In later phases it becomes the interactive console menu.
 */
public class Main {

    public static void main(String[] args) {
        printBanner();
        System.out.println("Project setup complete.");
        System.out.println("Running on Java " + System.getProperty("java.version"));
    }

    private static void printBanner() {
        System.out.println("========================================");
        System.out.println("               STOCKSCOPE");
        System.out.println("       Financial Comparison Tool");
        System.out.println("========================================");
    }
}
