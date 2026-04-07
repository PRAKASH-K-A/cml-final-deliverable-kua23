package com.stocker;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * LAB 11: OptionPricingService - Central Service for Option Pricing
 * 
 * This service maintains:
 * 1. Current spot prices per symbol (updated on each trade)
 * 2. Black-Scholes calculator instances
 * 3. Generated option prices for monitoring
 * 
 * The service is called whenever a trade occurs (in Execution reporting).
 * It calculates fresh option prices based on the new spot price and broadcasts
 * these updates to connected UI clients via WebSocket.
 * 
 * Key Design Decision: We use fixed strike prices per symbol for this lab.
 * In production, the system would track multiple strikes (option chains).
 */
public class OptionPricingService {
    
    private final Map<String, Double> spotPrices = new ConcurrentHashMap<>();
    private final Map<String, Double> strikeBySymbol = new ConcurrentHashMap<>();
    private final Map<String, OptionPrice> latestOptionPrices = new ConcurrentHashMap<>();
    
    private final BlackScholesCalculator calculator;
    private final OrderBroadcaster broadcaster;
    
    // Hardcoded strike prices for each symbol (these would normally come from a database)
    private static final Map<String, Double> DEFAULT_STRIKES = new ConcurrentHashMap<String, Double>() {{
        put("GOOG", 150.0);
        put("MSFT", 300.0);
        put("IBM", 180.0);
        put("AAPL", 175.0);
        put("AMZN", 165.0);
    }};
    
    public OptionPricingService(OrderBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
        this.calculator = new BlackScholesCalculator(0.20, 90);  // 20% volatility, 90 days to expiration
        
        // Initialize strike prices
        strikeBySymbol.putAll(DEFAULT_STRIKES);
        
        System.out.println("[LAB 11] Option Pricing Service initialized");
        System.out.println("[LAB 11] Volatility: 20% | Time to Expiration: 90 days (0.25 years)");
    }
    
    /**
     * Update the spot price for a symbol based on a trade execution
     * and recalculate option prices
     * 
     * This is called whenever a trade occurs (in OrderApplication.processNewOrder)
     * 
     * @param symbol The security symbol
     * @param tradePrice The price at which the trade executed
     * @param tradeQty The quantity that was traded
     */
    public void updateSpotPrice(String symbol, double tradePrice, double tradeQty) {
        // Update the current spot price
        spotPrices.put(symbol, tradePrice);
        
        // Recalculate option prices based on new spot price
        OptionPrice optionData = calculateOptionPrice(symbol, tradePrice);
        
        if (optionData != null) {
            optionData.setLastTradePrice(tradePrice);
            optionData.setLastTradeQty(tradeQty);
            
            // Store latest option prices
            latestOptionPrices.put(symbol, optionData);
            
            // Broadcast to UI
            broadcastOptionUpdate(optionData);
            
            System.out.println(String.format(
                "[LAB 11] Option Update | %s | Spot: $%.2f | Call: $%.2f | Put: $%.2f | Delta: %.4f",
                symbol, optionData.getSpotPrice(), optionData.getCallPrice(), 
                optionData.getPutPrice(), optionData.getDelta()));
        }
    }
    
    /**
     * Calculate option prices for a given symbol at its current spot price
     * 
     * @param symbol The security symbol
     * @param spotPrice The current spot price
     * @return OptionPrice object with call/put prices and Greeks
     */
    private OptionPrice calculateOptionPrice(String symbol, double spotPrice) {
        Double strikePrice = strikeBySymbol.get(symbol);
        
        if (strikePrice == null) {
            System.out.println("[LAB 11] ⚠ No strike price configured for symbol: " + symbol);
            return null;
        }
        
        try {
            // Validate inputs
            if (spotPrice <= 0) {
                System.out.println("[LAB 11] ⚠ Invalid spot price for " + symbol + ": " + spotPrice);
                return null;
            }
            
            OptionPrice optionData = new OptionPrice(symbol, spotPrice, strikePrice);
            
            // Calculate option prices using Black-Scholes
            double callPrice = calculator.calculateCallPrice(spotPrice, strikePrice);
            double putPrice = calculator.calculatePutPrice(spotPrice, strikePrice);
            
            optionData.setCallPrice(callPrice);
            optionData.setPutPrice(putPrice);
            optionData.setVolatility(calculator.getVolatility());
            optionData.setTimeToExpiration(calculator.getTimeToExpiration());
            
            // Calculate Greeks for the call option
            BlackScholesCalculator.OptionGreeks greeks = calculator.calculateGreeks(spotPrice, strikePrice, true);
            optionData.setDelta(greeks.delta);
            optionData.setGamma(greeks.gamma);
            optionData.setVega(greeks.vega);
            optionData.setTheta(greeks.theta);
            optionData.setRho(greeks.rho);
            
            return optionData;
        } catch (Exception e) {
            System.err.println("[LAB 11] ERROR calculating option prices for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Broadcast option price update to UI via WebSocket
     * 
     * @param optionData The option pricing data to broadcast
     */
    public void broadcastOptionUpdate(OptionPrice optionData) {
        if (broadcaster != null) {
            broadcaster.broadcastOptionPrice(optionData);
        }
    }
    
    /**
     * Get the latest calculated option prices for a symbol
     * Used by UI queries or monitoring
     * 
     * @param symbol The security symbol
     * @return Latest OptionPrice data or null if not available
     */
    public OptionPrice getLatestOptionPrice(String symbol) {
        return latestOptionPrices.get(symbol);
    }
    
    /**
     * Get the current spot price for a symbol
     * 
     * @param symbol The security symbol
     * @return Current spot price or 0.0 if not available
     */
    public double getSpotPrice(String symbol) {
        return spotPrices.getOrDefault(symbol, 0.0);
    }
    
    /**
     * Update option parameters (volatility or time to expiration)
     * This would typically be called administratively to adjust model parameters
     * 
     * @param newVolatility New volatility (e.g., 0.20 for 20%)
     * @param daysToExpiration New time to expiration in days
     */
    public void updateModelParameters(double newVolatility, double daysToExpiration) {
        calculator.setVolatility(newVolatility);
        calculator.setTimeToExpiration(daysToExpiration);
        System.out.println(String.format(
            "[LAB 11] Model parameters updated: Volatility=%.0f%%, Time=%d days",
            newVolatility * 100, (int)daysToExpiration));
    }
    
    /**
     * Register a custom strike price for a symbol
     * Used when adding new securities to the trading system
     * 
     * @param symbol The security symbol
     * @param strikePrice The option strike price for this symbol
     */
    public void registerStrike(String symbol, double strikePrice) {
        strikeBySymbol.put(symbol, strikePrice);
        System.out.println("[LAB 11] Strike price registered: " + symbol + " = $" + strikePrice);
    }
    
    /**
     * Manual calculation of option price for a given symbol (used for testing/verification)
     * 
     * @param symbol The security symbol
     * @return OptionPrice with current calculations
     */
    public OptionPrice recalculateAndBroadcast(String symbol) {
        Double spotPrice = spotPrices.get(symbol);
        if (spotPrice == null || spotPrice <= 0) {
            System.out.println("[LAB 11] Cannot recalculate: No valid spot price for " + symbol);
            return null;
        }
        
        OptionPrice optionData = calculateOptionPrice(symbol, spotPrice);
        if (optionData != null) {
            broadcastOptionUpdate(optionData);
        }
        return optionData;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[Option Pricing Service]\n");
        for (Map.Entry<String, Double> entry : spotPrices.entrySet()) {
            String symbol = entry.getKey();
            Double spot = entry.getValue();
            OptionPrice latest = latestOptionPrices.get(symbol);
            if (latest != null) {
                sb.append(String.format("  %s: Spot=$%.2f | Call=$%.2f | Put=$%.2f\n",
                    symbol, spot, latest.getCallPrice(), latest.getPutPrice()));
            }
        }
        return sb.toString();
    }
}
