package trading212.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TradingBotService {

    private final PortfolioService portfolioService;
    private final PriceService priceService;
    private final JdbcTemplate jdbcTemplate;
    private final TaskScheduler taskScheduler;

    protected ScheduledFuture<?> tradingTask;
    protected ScheduledFuture<?> trainingTask;
    protected final AtomicBoolean isRunning = new AtomicBoolean(false);
    protected final AtomicReference<String> currentMode = new AtomicReference<>("TRAINING");

    // Training state (to resume after pause)
    private BigDecimal lastPrice = null;
    private LocalDateTime lastTimestamp = null;
    private int lastIndex = 0;

    @Value("${trading.bot.symbol:BTC}")
    protected String symbol;

    @Value("${trading.parameters.short-ma-period}")
    protected int shortMaPeriod;

    @Value("${trading.parameters.long-ma-period}")
    protected int longMaPeriod;

    @Value("${trading.parameters.trade-percentage}")
    protected BigDecimal tradePercentage;

    public TradingBotService(JdbcTemplate jdbcTemplate, PortfolioService portfolioService,
                             PriceService priceService, TaskScheduler taskScheduler) {
        this.jdbcTemplate = jdbcTemplate;
        this.portfolioService = portfolioService;
        this.priceService = priceService;
        this.taskScheduler = taskScheduler;
    }

    /** Start bot in the selected mode */
    public void startBot(String mode) {
        currentMode.set(mode);
        isRunning.set(true);
        updateBotStatus(true, mode);

        if ("TRAINING".equals(mode)) {
            stopTradingTask();
            stopTrainingTask();
            trainingTask = taskScheduler.scheduleWithFixedDelay(this::runTrainingStep, Duration.ofMillis(50));
        } else if ("TRADING".equals(mode)) {
            stopTrainingTask();
            runTradingMode();
        }
    }

    /** Stop the bot */
    public void stopBot() {
        isRunning.set(false);
        stopTradingTask();
        stopTrainingTask();
        updateBotStatus(false, currentMode.get());
    }

    /** Reset bot and portfolio */
    public void resetBot() {
        stopBot();
        portfolioService.resetPortfolio();
        priceService.resetPriceHistory(symbol);
        lastPrice = null;
        lastTimestamp = null;
        lastIndex = 0;
    }

    /** Run trading mode using a scheduled task */
    public void runTradingMode() {
        stopTradingTask();
        tradingTask = taskScheduler.scheduleWithFixedDelay(this::runTradingStep, Duration.ofSeconds(7));
    }

    private void stopTradingTask() {
        if (tradingTask != null && !tradingTask.isCancelled()) {
            tradingTask.cancel(true);
        }
    }

    private void stopTrainingTask() {
        if (trainingTask != null && !trainingTask.isCancelled()) {
            trainingTask.cancel(true);
        }
    }

    /** Execute one step of trading */
    protected void runTradingStep() {
        if (!isRunning.get() || "TRAINING".equals(currentMode.get())) return;

        try {
            BigDecimal currentPrice = priceService.fetchCurrentPrice();
            if (currentPrice != null) {
                Timestamp now = new Timestamp(System.currentTimeMillis());
                priceService.savePriceHistory(symbol, currentPrice, "TRADING", now);
                evaluateAndTrade(currentPrice, "TRADING", LocalDateTime.now());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Execute one step of training */
    private void runTrainingStep() {
        if (!isRunning.get() || !"TRAINING".equals(currentMode.get())) return;

        // do one iteration per step
        int i = lastIndex;
        BigDecimal price = lastPrice != null ? lastPrice : new BigDecimal("50000");
        LocalDateTime timestamp = lastTimestamp != null ? lastTimestamp : LocalDateTime.now();

        double change = (Math.random() - 0.5) * 1000;
        price = price.add(new BigDecimal(change)).max(new BigDecimal("10000"));

        priceService.savePriceHistory(symbol, price, "TRAINING", Timestamp.valueOf(timestamp));

        if (i > longMaPeriod) {
            evaluateAndTrade(price, "TRAINING", timestamp);
        }

        // update state
        lastPrice = price;
        lastTimestamp = timestamp.plusMinutes(30);
        lastIndex = i + 1;
    }

    protected void evaluateAndTrade(BigDecimal currentPrice, String mode, LocalDateTime timestamp) {
        BigDecimal shortMA = priceService.calculateMA(shortMaPeriod, mode);
        BigDecimal longMA = priceService.calculateMA(longMaPeriod, mode);

        if (shortMA == null || longMA == null) return;

        BigDecimal currentPosition = portfolioService.getCurrentPosition(symbol, mode);
        BigDecimal balance = portfolioService.getAccountBalance(mode);

        // Buy signal
        if (shortMA.compareTo(longMA) > 0 && currentPosition.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal tradeAmount = balance.multiply(tradePercentage);
            BigDecimal quantity = tradeAmount.divide(currentPrice, 8, RoundingMode.HALF_UP);
            if (quantity.compareTo(new BigDecimal("0.00001")) > 0) {
                executeTrade("BUY", quantity, currentPrice, mode, timestamp);
            }
        }
        // Sell signal
        else if (shortMA.compareTo(longMA) < 0 && currentPosition.compareTo(BigDecimal.ZERO) > 0) {
            executeTrade("SELL", currentPosition, currentPrice, mode, timestamp);
        }
    }

    protected void executeTrade(String tradeType, BigDecimal quantity, BigDecimal price, String mode, LocalDateTime timestamp) {
        BigDecimal totalValue = quantity.multiply(price);
        BigDecimal profitLoss = BigDecimal.ZERO;

        if ("BUY".equals(tradeType)) {
            portfolioService.upsertBalance(totalValue, mode);
            portfolioService.upsertPortfolio(quantity, price, symbol, tradeType, mode);
        } else {
            profitLoss = portfolioService.calculateProfitLoss(quantity, price, symbol, mode);
            portfolioService.upsertPortfolio(quantity, totalValue, symbol, tradeType, mode);
        }

        jdbcTemplate.update(
            "INSERT INTO trades (account_id, symbol, trade_type, quantity, price, total_value, profit_loss, timestamp, mode) " +
            "VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)",
            symbol, tradeType, quantity, price, totalValue, profitLoss, Timestamp.valueOf(timestamp), mode
        );
    }

    private void updateBotStatus(boolean running, String mode) {
        jdbcTemplate.update(
            "UPDATE bot_status SET is_running = ?, mode = ?, last_run = ? WHERE id = 1",
            running, mode, running ? new Timestamp(System.currentTimeMillis()) : null
        );
    }

    public Map<String, Object> getBotStatus() {
        return jdbcTemplate.queryForMap("SELECT * FROM bot_status WHERE id = 1");
    }
}