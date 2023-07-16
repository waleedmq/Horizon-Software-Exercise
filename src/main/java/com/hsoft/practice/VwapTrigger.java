package com.hsoft.practice;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hsoft.api.MarketDataListener;
import com.hsoft.api.PricingDataListener;
import com.hsoft.api.VwapTriggerListener;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for the candidate to resolve the exercise
 */

public class VwapTrigger implements PricingDataListener, MarketDataListener {

  private final VwapTriggerListener vwapTriggerListener;
  private static Logger logger = LogManager.getLogger();
  //product and their newest fair values stored in hashmap with productId as key and fairValue as value
  private ConcurrentHashMap<String, Double> productFairValues;
  //product transactions stored in a hashmap with productId as key and last 5 transactions stored as value in the form [quantity, price]
  private ConcurrentHashMap <String, ArrayBlockingQueue<double[]>> productTransactions;

  /**
   * This constructor is mainly available to ease unit test by not having to provide a VwapTriggerListener
   */
  protected VwapTrigger() {
    this.vwapTriggerListener = (productId, vwap, fairValue) -> {
      // ignore
    };
    productFairValues = new ConcurrentHashMap<>();
    productTransactions = new ConcurrentHashMap<>();
  }

  public VwapTrigger(VwapTriggerListener vwapTriggerListener) {
    this.vwapTriggerListener = vwapTriggerListener;
    productFairValues = new ConcurrentHashMap<>();
    productTransactions = new ConcurrentHashMap<>();
  }

  @Override
  public synchronized void transactionOccurred(String productId, long quantity, double price) {
    // This method will be called when a new transaction is received
    // You can then perform your check
    // And, if matching the requirement, notify the event via 'this.vwapTriggerListener.vwapTriggered(xxx);

      if(quantity > 0 && price > 0) {
          logger.info("Transaction occurred product: " + productId + " quantity: " + quantity + " price: " + price);

          // 5 most recent transactions stored as [quantity, price]
          ArrayBlockingQueue<double[]> lastFiveTransactions;

          if (!productTransactions.containsKey(productId)) {
              lastFiveTransactions = new ArrayBlockingQueue<>(5);
          } else {
              lastFiveTransactions = productTransactions.get(productId);

              if (lastFiveTransactions.size() >= 5) {
                  lastFiveTransactions.remove();
              }
          }

          lastFiveTransactions.add(new double[] {quantity, price});
          productTransactions.put(productId, lastFiveTransactions);

          if (productFairValues.containsKey(productId) && calculateVWAP(productId) > productFairValues.get(productId)) {
              vwapTriggerListener.vwapTriggered(productId, calculateVWAP(productId), productFairValues.get(productId));
              logger.info("vwapTriggerListener triggered with Fair Value: " + productFairValues.get(productId) + " and VWAP: " + calculateVWAP(productId) + " for product: " + productId);
          }
      }
  }

  @Override
  public synchronized void fairValueChanged(String productId, double fairValue) {
    // This method will be called when a new fair value is received
    // You can then perform your check
    // And, if matching the requirement, notify the event via 'this.vwapTriggerListener.vwapTriggered(xxx);'
      logger.info("Fair value for product " + productId + " changed to " + fairValue);

      productFairValues.put(productId, fairValue);

      if(productTransactions.containsKey(productId) && calculateVWAP(productId) > fairValue){
          vwapTriggerListener.vwapTriggered(productId, calculateVWAP(productId), fairValue);
          logger.info("vwapTriggerListener triggered with Fair Value: " + fairValue + " and VWAP: " + calculateVWAP(productId) + " for product: " + productId);
      }
  }

    //check if product exists then run this method to calculate the VWAP using the productId
    public synchronized double calculateVWAP(String productId) {
      ArrayBlockingQueue <double[]> lastFiveTransactions = productTransactions.get(productId);
      double totalQuantity = 0;
      double totalPrice = 0;

      for(double[] transaction : lastFiveTransactions){
          totalQuantity += transaction[0];
          totalPrice += (transaction[0] * transaction[1]);
      }

      return totalPrice / totalQuantity;
  }
}