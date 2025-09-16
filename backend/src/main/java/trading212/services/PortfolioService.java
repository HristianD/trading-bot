package trading212.services;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioService {
    private final JdbcTemplate jdbcTemplate;
    
    PortfolioService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BigDecimal getCurrentPosition(String symbol, String mode) {
        String sql = "SELECT COALESCE(quantity, 0) FROM portfolio WHERE account_id = 1 AND symbol = ? AND mode = ?";
        List<BigDecimal> results = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getBigDecimal(1), symbol, mode);
        
        return results.isEmpty() ? BigDecimal.ZERO : results.get(0);
    }
    
    public BigDecimal getAccountBalance(String mode) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE id = 1 AND mode = ?", BigDecimal.class, mode);
    }

    @Transactional
    public void upsertPortfolio(BigDecimal quantity, BigDecimal price, String symbol, String tradeType, String mode) {
        if ("BUY".equals(tradeType)) {
            BigDecimal totalValue = quantity.multiply(price);
            BigDecimal averagePrice = totalValue.divide(quantity, 8, RoundingMode.HALF_UP);
            
            String sql = "INSERT INTO portfolio (account_id, symbol, quantity, average_buy_price, mode) " +
                         "VALUES (1, ?, ?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE " +
                         "quantity = quantity + VALUES(quantity), " +
                         "average_buy_price = ((quantity * average_buy_price) + (VALUES(quantity) * VALUES(average_buy_price))) / (quantity + VALUES(quantity))";
            
            jdbcTemplate.update(sql, symbol, quantity, averagePrice, mode);
        } else {
            // SELL
            BigDecimal totalValue = quantity.multiply(price);
            
            // Update balance
            jdbcTemplate.update("UPDATE account SET balance = balance + ? WHERE id = 1 AND mode = ?", totalValue, mode);
            
            // Update portfolio
            jdbcTemplate.update(
                "UPDATE portfolio SET quantity = quantity - ? WHERE account_id = 1 AND symbol = ? AND mode = ?",
                quantity, symbol, mode
            );
            
            // Remove from portfolio if quantity is 0
            jdbcTemplate.update(
                "DELETE FROM portfolio WHERE account_id = 1 AND symbol = ? AND quantity <= 0.00001 AND mode = ?",
                symbol, mode
            );
        }
    }

    public void createPortfolio(BigDecimal quantity, BigDecimal price, String symbol, String mode) {
        jdbcTemplate.update(
            "INSERT INTO portfolio (account_id, symbol, quantity, average_buy_price, mode) VALUES (1, ?, ?, ?, ?)",
            symbol, quantity, price, mode
        );
    }

    public void resetPortfolio() {
        // Reset account balance
        jdbcTemplate.update("UPDATE account SET balance = initial_balance WHERE id = 1");
        
        // Clear portfolio
        jdbcTemplate.update("DELETE FROM portfolio WHERE account_id = 1");

        // Clear trades
        jdbcTemplate.update("DELETE FROM trades WHERE account_id = 1");
    }

    public void upsertBalance(BigDecimal totalValue, String mode) {
        jdbcTemplate.update("INSERT INTO account (id, balance, mode) VALUES (1, ?, ?) ON DUPLICATE KEY UPDATE balance = balance - VALUES(balance)", totalValue, mode);
    }

    public BigDecimal calculateProfitLoss(BigDecimal quantity, BigDecimal price, String symbol, String mode) {
        String avgPriceSql = "SELECT average_buy_price FROM portfolio WHERE account_id = 1 AND symbol = ? AND mode = ?";
        BigDecimal avgBuyPrice = jdbcTemplate.queryForObject(avgPriceSql, BigDecimal.class, symbol, mode);
        
        return quantity.multiply(price.subtract(avgBuyPrice));
    }
    
    public Map<String, Object> getAccountInfo(String mode) {
        // Fetch account info for the given mode
        String accountSql = "SELECT * FROM account WHERE id = 1 AND mode = ?";
        Map<String, Object> account = jdbcTemplate.queryForMap(accountSql, mode);

        // Calculate portfolio value for the same mode
        String portfolioSql = """
            SELECT COALESCE(SUM(p.quantity * ph.price), 0) AS portfolio_value
            FROM portfolio p
            JOIN LATERAL (
                SELECT price 
                FROM price_history 
                WHERE symbol = p.symbol AND mode = ?
                ORDER BY timestamp DESC 
                LIMIT 1
            ) ph ON true
            WHERE p.account_id = 1 AND p.mode = ?
        """;

        BigDecimal portfolioValue = jdbcTemplate.queryForObject(portfolioSql,BigDecimal.class, mode, mode);

        account.put("portfolio_value", portfolioValue);
        account.put("total_value", ((BigDecimal) account.get("balance")).add(portfolioValue));

        return account;
    }
    
    public List<Map<String, Object>> getTradeHistory(String mode) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM trades WHERE account_id = 1 AND mode = ? ORDER BY timestamp DESC LIMIT 100", mode
        );
    }
    
    public List<Map<String, Object>> getPortfolio(String mode) {
        String sql = """
            WITH latest_prices AS (
                SELECT ph1.symbol, ph1.price
                FROM price_history ph1
                JOIN (
                    SELECT symbol, MAX(timestamp) AS latest
                    FROM price_history
                    WHERE mode = ?
                    GROUP BY symbol
                ) ph2
                ON ph1.symbol = ph2.symbol 
                AND ph1.timestamp = ph2.latest
                AND ph1.mode = ?
            )
            SELECT 
                p.*,
                lp.price AS current_price,
                (p.quantity * lp.price) AS current_value,
                ((lp.price - p.average_buy_price) * p.quantity) AS unrealized_pnl
            FROM portfolio p
            JOIN latest_prices lp
            ON p.symbol = lp.symbol
            WHERE p.account_id = 1
            AND p.mode = ?;
            """;

        return jdbcTemplate.queryForList(sql, mode, mode, mode);
    }
}