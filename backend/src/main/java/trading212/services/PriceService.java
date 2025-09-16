package trading212.services;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PriceService {
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${trading.bot.symbol:BTC}")
    private String symbol;
    
    @Value("${trading.bot.api-url}")
    private String api_url;

    PriceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BigDecimal fetchCurrentPrice() {
        BigDecimal curPrice = null;

        try {
            String response = restTemplate.getForObject(api_url, String.class);
            JsonNode root = objectMapper.readTree(response);
            String usdRate = root.path("data").path("rates").path("USD").asText();
            
            curPrice = new BigDecimal(usdRate);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return curPrice;
    }

    public BigDecimal calculateMA(int period, String mode) {
        String sql = "SELECT AVG(price) FROM (SELECT price FROM price_history WHERE symbol = ? AND mode = ? ORDER BY timestamp DESC LIMIT ?) AS recent_prices";
        
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, symbol, mode, period);
    }

    public void savePriceHistory(String symbol, BigDecimal price, String mode, Timestamp timestamp) {
        try {
            jdbcTemplate.update(
                "INSERT INTO price_history (symbol, price, mode, timestamp) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE price = VALUES(price), mode = VALUES(mode)",
                symbol, price, mode, timestamp
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void resetPriceHistory(String symbol) {
        jdbcTemplate.update(
            "DELETE FROM price_history WHERE symbol = ?",
            symbol
        );
    }

    public List<Map<String, Object>> getPriceHistory(String mode) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM price_history WHERE symbol = ? AND mode = ? ORDER BY timestamp DESC LIMIT 200",
            symbol, mode
        );
    }
}