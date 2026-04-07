package com.stocker;

import java.time.Instant;

import com.google.gson.annotations.SerializedName;

/**
 * LAB 11: OptionPrice - Data Transfer Object for Option Pricing Data
 * 
 * This class represents option pricing information for a given underlying security
 * and is serialized to JSON for real-time broadcast to the Angular UI via WebSocket.
 * 
 * Contains:
 * - Current option prices (call and put)
 * - The underlying spot price
 * - Option Greeks (Delta, Gamma, Vega, Theta, Rho)
 * - Timestamp of the calculation
 */
public class OptionPrice {
    
    @SerializedName("symbol")
    private String symbol;
    
    @SerializedName("spotPrice")
    private double spotPrice;
    
    @SerializedName("strikePrice")
    private double strikePrice;
    
    @SerializedName("callPrice")
    private double callPrice;
    
    @SerializedName("putPrice")
    private double putPrice;
    
    @SerializedName("delta")
    private double delta;
    
    @SerializedName("gamma")
    private double gamma;
    
    @SerializedName("vega")
    private double vega;
    
    @SerializedName("theta")
    private double theta;
    
    @SerializedName("rho")
    private double rho;
    
    @SerializedName("volatility")
    private double volatility;
    
    @SerializedName("timeToExpiration")
    private double timeToExpiration;
    
    @SerializedName("timestamp")
    private String timestamp;
    
    @SerializedName("lastTradeQty")
    private double lastTradeQty;
    
    @SerializedName("lastTradePrice")
    private double lastTradePrice;
    
    public OptionPrice() {
        this.timestamp = Instant.now().toString();
    }
    
    public OptionPrice(String symbol, double spotPrice, double strikePrice) {
        this.symbol = symbol;
        this.spotPrice = spotPrice;
        this.strikePrice = strikePrice;
        this.timestamp = Instant.now().toString();
    }
    
    // Getters and Setters
    
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public double getSpotPrice() { return spotPrice; }
    public void setSpotPrice(double spotPrice) { this.spotPrice = spotPrice; }
    
    public double getStrikePrice() { return strikePrice; }
    public void setStrikePrice(double strikePrice) { this.strikePrice = strikePrice; }
    
    public double getCallPrice() { return callPrice; }
    public void setCallPrice(double callPrice) { this.callPrice = callPrice; }
    
    public double getPutPrice() { return putPrice; }
    public void setPutPrice(double putPrice) { this.putPrice = putPrice; }
    
    public double getDelta() { return delta; }
    public void setDelta(double delta) { this.delta = delta; }
    
    public double getGamma() { return gamma; }
    public void setGamma(double gamma) { this.gamma = gamma; }
    
    public double getVega() { return vega; }
    public void setVega(double vega) { this.vega = vega; }
    
    public double getTheta() { return theta; }
    public void setTheta(double theta) { this.theta = theta; }
    
    public double getRho() { return rho; }
    public void setRho(double rho) { this.rho = rho; }
    
    public double getVolatility() { return volatility; }
    public void setVolatility(double volatility) { this.volatility = volatility; }
    
    public double getTimeToExpiration() { return timeToExpiration; }
    public void setTimeToExpiration(double timeToExpiration) { this.timeToExpiration = timeToExpiration; }
    
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    
    public double getLastTradeQty() { return lastTradeQty; }
    public void setLastTradeQty(double lastTradeQty) { this.lastTradeQty = lastTradeQty; }
    
    public double getLastTradePrice() { return lastTradePrice; }
    public void setLastTradePrice(double lastTradePrice) { this.lastTradePrice = lastTradePrice; }
    
    @Override
    public String toString() {
        return String.format(
            "OptionPrice[symbol=%s, spot=%.2f, strike=%.2f, call=%.2f, put=%.2f, delta=%.4f]",
            symbol, spotPrice, strikePrice, callPrice, putPrice, delta);
    }
}
