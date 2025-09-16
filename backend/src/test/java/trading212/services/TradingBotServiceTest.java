package trading212.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingBotServiceTest {

    @Mock
    private PortfolioService portfolioService;
    
    @Mock
    private PriceService priceService;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private TaskScheduler taskScheduler;
    
    @Mock
    @SuppressWarnings("rawtypes")
    private ScheduledFuture scheduledFuture;
    
    @Spy
    @InjectMocks
    private TradingBotService tradingBotService;
    
    private final String symbol = "BTC";
    private final int shortMaPeriod = 10;
    private final int longMaPeriod = 20;
    private final BigDecimal tradePercentage = new BigDecimal("0.1");

    @BeforeEach
    void setUp() {
        tradingBotService.shortMaPeriod = shortMaPeriod;
        tradingBotService.longMaPeriod = longMaPeriod;
        tradingBotService.tradePercentage = tradePercentage;
        tradingBotService.symbol = symbol;
    }

    @Test
    void testStartBotTrainingMode() {
        when(taskScheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class)))
            .thenReturn(mock(ScheduledFuture.class));
        
        tradingBotService.startBot("TRAINING");
        
        assertTrue(tradingBotService.isRunning.get());
        assertEquals("TRAINING", tradingBotService.currentMode.get());
        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
    }

    @Test
    void testStartBotTradingMode() {
        // Mock the private method using doNothing on the spy
        doNothing().when(tradingBotService).runTradingMode();
        
        tradingBotService.startBot("TRADING");
        
        assertTrue(tradingBotService.isRunning.get());
        assertEquals("TRADING", tradingBotService.currentMode.get());
        verify(tradingBotService).runTradingMode();
    }

    @Test
    void testStopBot() {
        tradingBotService.isRunning.set(true);
        tradingBotService.tradingTask = scheduledFuture;
        tradingBotService.trainingTask = scheduledFuture;
        
        tradingBotService.stopBot();
        
        assertFalse(tradingBotService.isRunning.get());
        verify(scheduledFuture, times(2)).cancel(true);
        verify(jdbcTemplate).update(anyString(), any(), any(), any());
    }

    @Test
    void testExecuteTradingCycleWhenNotRunning() {
        tradingBotService.isRunning.set(false);
        tradingBotService.runTradingStep();
        
        verify(priceService, never()).fetchCurrentPrice();
    }

    @Test
    void testExecuteTradeBuy() {
        BigDecimal quantity = new BigDecimal("0.5");
        BigDecimal price = new BigDecimal("50000");
        LocalDateTime timestamp = LocalDateTime.now();
        
        tradingBotService.executeTrade("BUY", quantity, price, "TRAINING", timestamp);
        
        verify(portfolioService).upsertBalance(any(), eq("TRAINING"));
        verify(portfolioService).upsertPortfolio(eq(quantity), eq(price), eq(symbol), eq("BUY"), eq("TRAINING"));
        verify(jdbcTemplate).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void testResetBot() {
        tradingBotService.isRunning.set(true);
        tradingBotService.tradingTask = scheduledFuture;
        tradingBotService.trainingTask = scheduledFuture;
        
        tradingBotService.resetBot();
        
        assertFalse(tradingBotService.isRunning.get());
        verify(portfolioService).resetPortfolio();
        verify(priceService).resetPriceHistory(symbol);
        verify(scheduledFuture, times(2)).cancel(true);
    }

    @Test
    void testGetBotStatus() {
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(Map.of("is_running", true, "mode", "TRAINING"));
        
        Map<String, Object> result = tradingBotService.getBotStatus();
        
        assertNotNull(result);
        assertEquals(true, result.get("is_running"));
        assertEquals("TRAINING", result.get("mode"));
    }
}