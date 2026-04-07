package com.stocker;

/**
 * LAB 11: BLACK-SCHOLES OPTION PRICING MODEL
 * 
 * Implements the Black-Scholes formula for European-style options.
 * 
 * The Black-Scholes model calculates the theoretical price of options
 * given the following inputs:
 *   S(t): Current spot price of the underlying asset
 *   K:    Strike price of the option
 *   T:    Time to expiration (in years)
 *   r:    Risk-free interest rate (annualized)
 *   σ:    Volatility of the underlying (annualized standard deviation)
 * 
 * Formula:
 *   C(S, t) = S*N(d1) - K*e^(-rT)*N(d2)  [CALL OPTION]
 *   P(S, t) = K*e^(-rT)*N(d2) - S*N(d1)  [PUT OPTION]
 * 
 * Where:
 *   d1 = (ln(S/K) + (r + σ²/2)*T) / (σ*√T)
 *   d2 = d1 - σ*√T
 *   N(x) = Standard normal cumulative distribution function (CDF)
 * 
 * Key Insight: The option price is extremely sensitive to the current spot price S.
 * In a trading environment, with each new execution (trade), the spot price changes,
 * so we recalculate option prices continuously.
 */
public class BlackScholesCalculator {
    
    // Risk-free rate (assumed 2.0% per annum for this lab)
    private static final double RISK_FREE_RATE = 0.02;
    
    // Default volatility (20% per annum - typical for equities)
    private double volatility;
    
    // Time to expiration in years (default: 90 days ≈ 0.25 years)
    private double timeToExpiration;
    
    public BlackScholesCalculator() {
        this.volatility = 0.20;  // 20% volatility
        this.timeToExpiration = 0.25;  // 90 days
    }
    
    public BlackScholesCalculator(double volatility, double daysToExpiration) {
        this.volatility = volatility;
        this.timeToExpiration = daysToExpiration / 365.0;  // Convert days to years
    }
    
    /**
     * Calculate call option price using Black-Scholes formula
     * 
     * @param spotPrice Current market price of the underlying
     * @param strikePrice Strike price of the call option
     * @return Theoretical call option price
     */
    public double calculateCallPrice(double spotPrice, double strikePrice) {
        validateInputs(spotPrice, strikePrice);
        
        double d1 = calculateD1(spotPrice, strikePrice);
        double d2 = d1 - volatility * Math.sqrt(timeToExpiration);
        
        double callPrice = spotPrice * cumulativeNormalDistribution(d1) 
                         - strikePrice * Math.exp(-RISK_FREE_RATE * timeToExpiration) 
                         * cumulativeNormalDistribution(d2);
        
        return Math.max(0, callPrice);  // Option price can't be negative
    }
    
    /**
     * Calculate put option price using Black-Scholes formula
     * 
     * @param spotPrice Current market price of the underlying
     * @param strikePrice Strike price of the put option
     * @return Theoretical put option price
     */
    public double calculatePutPrice(double spotPrice, double strikePrice) {
        validateInputs(spotPrice, strikePrice);
        
        double d1 = calculateD1(spotPrice, strikePrice);
        double d2 = d1 - volatility * Math.sqrt(timeToExpiration);
        
        double putPrice = strikePrice * Math.exp(-RISK_FREE_RATE * timeToExpiration) 
                        * cumulativeNormalDistribution(-d2) 
                        - spotPrice * cumulativeNormalDistribution(-d1);
        
        return Math.max(0, putPrice);  // Option price can't be negative
    }
    
    /**
     * Calculate the Greeks: Delta, Gamma, Vega, Theta, Rho
     * These measure the sensitivity of option price to changes in input parameters
     */
    public OptionGreeks calculateGreeks(double spotPrice, double strikePrice, boolean isCall) {
        validateInputs(spotPrice, strikePrice);
        
        double d1 = calculateD1(spotPrice, strikePrice);
        double d2 = d1 - volatility * Math.sqrt(timeToExpiration);
        double sqrtT = Math.sqrt(timeToExpiration);
        
        OptionGreeks greeks = new OptionGreeks();
        
        // Delta: Rate of change of option price with respect to spot price
        greeks.delta = isCall ? cumulativeNormalDistribution(d1) 
                              : cumulativeNormalDistribution(d1) - 1;
        
        // Gamma: Rate of change of delta with respect to spot price
        double densityD1 = standardNormalPDF(d1);
        greeks.gamma = densityD1 / (spotPrice * volatility * sqrtT);
        
        // Vega: Sensitivity to volatility (same for calls and puts)
        greeks.vega = spotPrice * densityD1 * sqrtT / 100;  // Per 1% change
        
        // Theta: Rate of change with respect to time (time decay)
        double exp_rT = Math.exp(-RISK_FREE_RATE * timeToExpiration);
        if (isCall) {
            greeks.theta = (-spotPrice * densityD1 * volatility / (2 * sqrtT) 
                          - RISK_FREE_RATE * strikePrice * exp_rT * cumulativeNormalDistribution(d2)) / 365;
        } else {
            greeks.theta = (-spotPrice * densityD1 * volatility / (2 * sqrtT) 
                          + RISK_FREE_RATE * strikePrice * exp_rT * cumulativeNormalDistribution(-d2)) / 365;
        }
        
        // Rho: Sensitivity to interest rate
        if (isCall) {
            greeks.rho = strikePrice * timeToExpiration * exp_rT * cumulativeNormalDistribution(d2) / 100;
        } else {
            greeks.rho = -strikePrice * timeToExpiration * exp_rT * cumulativeNormalDistribution(-d2) / 100;
        }
        
        return greeks;
    }
    
    /**
     * Private helper: Calculate d1 component of Black-Scholes formula
     */
    private double calculateD1(double spotPrice, double strikePrice) {
        double numerator = Math.log(spotPrice / strikePrice) 
                         + (RISK_FREE_RATE + 0.5 * volatility * volatility) * timeToExpiration;
        double denominator = volatility * Math.sqrt(timeToExpiration);
        return numerator / denominator;
    }
    
    /**
     * Standard Normal Probability Density Function (PDF)
     * f(x) = (1/√(2π)) * e^(-x²/2)
     */
    private double standardNormalPDF(double x) {
        return Math.exp(-x * x / 2) / Math.sqrt(2 * Math.PI);
    }
    
    /**
     * Cumulative Normal Distribution Function (CDF) approximation
     * Uses Hart's code for accuracy
     * Approximates N(x) using error function
     */
    private double cumulativeNormalDistribution(double x) {
        // Handle edge cases
        if (x < -8.0) return 0.0;
        if (x > 8.0) return 1.0;
        
        double b1 =  0.319381530;
        double b2 = -0.356563782;
        double b3 =  1.781477937;
        double b4 = -1.821255978;
        double b5 =  1.330274429;
        double p  =  0.2316419;
        double c  =  0.39894228;
        
        double absX = Math.abs(x);
        double t = 1.0 / (1.0 + p * absX);
        double b = c * Math.exp(-x * x / 2) * t;
        double n = b * (b1 + t * (b2 + t * (b3 + t * (b4 + t * b5))));
        
        if (x < 0) {
            n = 1 - n;
        }
        
        return n;
    }
    
    /**
     * Validate inputs for Black-Scholes calculation
     */
    private void validateInputs(double spotPrice, double strikePrice) {
        if (spotPrice <= 0) {
            throw new IllegalArgumentException("Spot price must be positive: " + spotPrice);
        }
        if (strikePrice <= 0) {
            throw new IllegalArgumentException("Strike price must be positive: " + strikePrice);
        }
        if (volatility <= 0) {
            throw new IllegalArgumentException("Volatility must be positive: " + volatility);
        }
        if (timeToExpiration <= 0) {
            throw new IllegalArgumentException("Time to expiration must be positive: " + timeToExpiration);
        }
    }
    
    // Getters and setters
    public double getVolatility() { return volatility; }
    
    public void setVolatility(double volatility) { this.volatility = volatility; }
    
    public double getTimeToExpiration() { return timeToExpiration; }
    
    public void setTimeToExpiration(double daysToExpiration) {
        this.timeToExpiration = daysToExpiration / 365.0;
    }
    
    /**
     * Inner class to hold the Greeks (option sensitivity metrics)
     */
    public static class OptionGreeks {
        public double delta;   // Price sensitivity
        public double gamma;   // Delta sensitivity
        public double vega;    // Volatility sensitivity
        public double theta;   // Time decay per day
        public double rho;     // Interest rate sensitivity
        
        @Override
        public String toString() {
            return String.format("Greeks[delta=%.4f, gamma=%.6f, vega=%.4f, theta=%.4f, rho=%.4f]",
                    delta, gamma, vega, theta, rho);
        }
    }
    
    /**
     * Test the Black-Scholes implementation with known values
     * Standard test case: S=100, K=100, r=5%, σ=20%, T=1 year
     * Expected Call ≈ 10.45, Expected Put ≈ 5.57
     */
    public static void main(String[] args) {
        BlackScholesCalculator calc = new BlackScholesCalculator(0.20, 365);
        
        double spotPrice = 100.0;
        double strikePrice = 100.0;
        
        double callPrice = calc.calculateCallPrice(spotPrice, strikePrice);
        double putPrice = calc.calculatePutPrice(spotPrice, strikePrice);
        
        System.out.println("[LAB 11] Black-Scholes Test");
        System.out.println("Spot Price: $" + spotPrice);
        System.out.println("Strike Price: $" + strikePrice);
        System.out.println("Volatility: 20%");
        System.out.println("Time to Expiration: 1 year");
        System.out.println("Risk-Free Rate: 2%");
        System.out.println();
        System.out.println("Call Option Price: $" + String.format("%.2f", callPrice));
        System.out.println("Put Option Price: $" + String.format("%.2f", putPrice));
        System.out.println("Call-Put Parity Check: " + 
                (Math.abs(callPrice - putPrice - (spotPrice - strikePrice * Math.exp(-0.02) * 1)) < 0.01 ? "✓ Valid" : "✗ Invalid"));
    }
}
