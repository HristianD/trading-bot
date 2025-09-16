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

    // Retrieve how much of the asset the bot has
    public BigDecimal getCurrentPosition(String symbol, String mode) {
        String sql = "SELECT COALESCE(quantity, 0) FROM portfolio WHERE account_id = 1 AND symbol = ? AND mode = ?";
        List<BigDecimal> results = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getBigDecimal(1), symbol, mode);
        
        return results.isEmpty() ? BigDecimal.ZERO : results.get(0);
    }
    
    // Retrieve account balance according to the bot's mode
    public BigDecimal getAccountBalance(String mode) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE id = 1 AND mode = ?", BigDecimal.class, mode);
    }

    @Transactional
    public void upsertPortfolio(BigDecimal quantity, BigDecimal price, String symbol, String tradeType, String mode) {
        if ("BUY".equals(tradeType)) {
            BigDecimal totalValue = quantity.multiply(price);
            BigDecimal averagePrice = totalValue.divide(quantity, 8, RoundingMode.HALF_UP);
            
            // Insert a new portfolio entry or update existing one if it exists
            // If the symbol already exists in the portfolio for this mode:
            // - Increase the quantity
            // - Recalculate the average buy price based on weighted average
            String sql = "INSERT INTO portfolio (account_id, symbol, quantity, average_buy_price, mode) " +
                         "VALUES (1, ?, ?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE " +
                         "quantity = quantity + VALUES(quantity), " +
                         "average_buy_price = ((quantity * average_buy_price) + (VALUES(quantity) * VALUES(average_buy_price))) / (quantity + VALUES(quantity))";
            
            jdbcTemplate.update(sql, symbol, quantity, averagePrice, mode);
        } else {
            // SELL
            BigDecimal totalValue = quantity.multiply(price);
            
            // Update account balance by adding the total value of sold assets
            jdbcTemplate.update("UPDATE account SET balance = balance + ? WHERE id = 1 AND mode = ?", totalValue, mode);
            
            // Reduce the quantity of the asset in portfolio
            jdbcTemplate.update(
                "UPDATE portfolio SET quantity = quantity - ? WHERE account_id = 1 AND symbol = ? AND mode = ?",
                quantity, symbol, mode
            );
            
            // Remove portfolio entry if quantity is effectively zero
            jdbcTemplate.update(
                "DELETE FROM portfolio WHERE account_id = 1 AND symbol = ? AND quantity <= 0.00001 AND mode = ?",
                symbol, mode
            );
        }
    }
    
    // Insert a new portfolio entry (used when initializing a new asset)
    public void createPortfolio(BigDecimal quantity, BigDecimal price, String symbol, String mode) {
        jdbcTemplate.update(
            "INSERT INTO portfolio (account_id, symbol, quantity, average_buy_price, mode) VALUES (1, ?, ?, ?, ?)",
            symbol, quantity, price, mode
        );
    }

    // Reset the account and portfolio to initial state
    public void resetPortfolio() {
        // Reset account balance
        jdbcTemplate.update("UPDATE account SET balance = initial_balance WHERE id = 1");
        
        // Clear portfolio
        jdbcTemplate.update("DELETE FROM portfolio WHERE account_id = 1");

        // Clear trades
        jdbcTemplate.update("DELETE FROM trades WHERE account_id = 1");
    }

    // Update account balance after a trade (used for BUY operations)
    public void upsertBalance(BigDecimal totalValue, String mode) {
        jdbcTemplate.update("INSERT INTO account (id, balance, mode) VALUES (1, ?, ?) ON DUPLICATE KEY UPDATE balance = balance - VALUES(balance)", totalValue, mode);
    }

    // Calculate the profit or loss of a given quantity of a symbol
    public BigDecimal calculateProfitLoss(BigDecimal quantity, BigDecimal price, String symbol, String mode) {
        String avgPriceSql = "SELECT average_buy_price FROM portfolio WHERE account_id = 1 AND symbol = ? AND mode = ?";
        BigDecimal avgBuyPrice = jdbcTemplate.queryForObject(avgPriceSql, BigDecimal.class, symbol, mode);
        
        // Profit/loss = quantity * (current price - average buy price)
        return quantity.multiply(price.subtract(avgBuyPrice));
    }
    
    // Get detailed account info including portfolio and total value for the given mode
    public Map<String, Object> getAccountInfo(String mode) {
        // Fetch account info for the given mode
        String accountSql = "SELECT * FROM account WHERE id = 1 AND mode = ?";
        Map<String, Object> account = jdbcTemplate.queryForMap(accountSql, mode);

        // Calculate portfolio value using latest prices for each symbol
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
    
    // Retrieve recent trades for the given mode, ordered by timestamp descending
    public List<Map<String, Object>> getTradeHistory(String mode) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM trades WHERE account_id = 1 AND mode = ? ORDER BY timestamp DESC LIMIT 100", mode
        );
    }
    
    // Get detailed portfolio info including current price, current value, and unrealized PnL
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