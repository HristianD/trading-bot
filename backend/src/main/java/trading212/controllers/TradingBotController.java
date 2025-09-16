package trading212.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import trading212.services.PortfolioService;
import trading212.services.PriceService;
import trading212.services.TradingBotService;

@RestController
@RequestMapping("/api")
public class TradingBotController {
    private final TradingBotService tradingBotService;
    private final PortfolioService portfolioService;
    private final PriceService priceService;

    public TradingBotController(TradingBotService tradingBotService, PortfolioService portfolioService, PriceService priceService) {
        this.tradingBotService = tradingBotService;
        this.portfolioService = portfolioService;
        this.priceService = priceService;
    }

    @PostMapping("/bot/start")
    public ResponseEntity<Map<String, Object>> startBot(@RequestParam String mode) {
        String normalizedMode = mode.toUpperCase();
        tradingBotService.startBot(normalizedMode);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Bot started in " + normalizedMode + " mode");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bot/stop")
    public ResponseEntity<Map<String, Object>> stopBot() {
        tradingBotService.stopBot();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Bot stopped");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bot/reset")
    public ResponseEntity<Map<String, Object>> resetBot() {
        tradingBotService.resetBot();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Bot reset successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bot/status")
    public ResponseEntity<Map<String, Object>> getBotStatus() {
        return ResponseEntity.ok(tradingBotService.getBotStatus());
    }

    @GetMapping("/account")
    public ResponseEntity<Map<String, Object>> getAccountInfo(@RequestParam String mode) {
        return ResponseEntity.ok(portfolioService.getAccountInfo(mode.toUpperCase()));
    }

    @GetMapping("/trades")
    public ResponseEntity<List<Map<String, Object>>> getTradeHistory(@RequestParam String mode) {
        return ResponseEntity.ok(portfolioService.getTradeHistory(mode.toUpperCase()));
    }

    @GetMapping("/prices")
    public ResponseEntity<List<Map<String, Object>>> getPriceHistory(@RequestParam String mode) {
        return ResponseEntity.ok(priceService.getPriceHistory(mode.toUpperCase()));
    }

    @GetMapping("/portfolio")
    public ResponseEntity<List<Map<String, Object>>> getPortfolio(@RequestParam String mode) {
        return ResponseEntity.ok(portfolioService.getPortfolio(mode.toUpperCase()));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        return ResponseEntity.ok(response);
    }
}
