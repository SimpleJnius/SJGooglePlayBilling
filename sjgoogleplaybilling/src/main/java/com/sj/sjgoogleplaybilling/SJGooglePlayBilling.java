package com.sj.sjgoogleplaybilling;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchaseHistoryResponseListener;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchaseHistoryParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.collect.ImmutableList;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class SJGooglePlayBilling {
    public BillingClient billingClient = null;
    public BillingFlowParams billingFlowParams = null;
    public final HashMap<String, ProductDetails> mProductDetails = new HashMap<>();
    public PurchasesUpdatedListener purchasesUpdatedListener = null;
    public ConsumeResponseListener consumeResponseListener = null;
    public AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener = null;
    public PurchaseHistoryResponseListener purchaseHistoryResponseListener = null;
    public PurchasesResponseListener purchasesResponseListener = null;
    public PurchaseCanceledListener purchaseCanceledListener = null;
    public PurchaseErrorListener purchaseErrorListener = null;
    public BillingServiceDisconnectedListener billingServiceDisconnectedListener = null;

    public BillingResult makePayment(Context context, Activity activity, String productType, String productId) {
        initializeBillingClient(context, productType);
        startBillingClientConnection(productType, productId);
        prepareBillingFlow(productId);

        BillingResult billingResult = null;
        if (productType.equals( BillingClient.ProductType.SUBS) &&
                billingClient.isFeatureSupported("subscriptions").getResponseCode() == BillingClient.BillingResponseCode.OK) {
            // Launch the billing flow
            billingResult = billingClient.launchBillingFlow(activity, billingFlowParams);
        }else if (productType.equals(BillingClient.ProductType.INAPP)) {
            billingResult = billingClient.launchBillingFlow(activity, billingFlowParams);
        } return billingResult;
    }

    private void prepareBillingFlow(String productId) {
        final ProductDetails productDetails = mProductDetails.get(productId);
        final List<ProductDetails.SubscriptionOfferDetails> subscriptionOfferDetails =
                Objects.requireNonNull(productDetails).getSubscriptionOfferDetails();

        ImmutableList<BillingFlowParams.ProductDetailsParams> productDetailsParamsList =
                ImmutableList.of(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                                .setProductDetails(Objects.requireNonNull(productDetails))
                                // to get an offer token, call ProductDetails.getSubscriptionOfferDetails()
                                // for a list of offers that are available to the user
                                //.setOfferToken(Objects.requireNonNull(subscriptionOfferDetails).get(0).getOfferToken())
                                .build()
                );

        billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build();

    }

    private void startBillingClientConnection(String productType, String productId) {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() ==  BillingClient.BillingResponseCode.OK) {
                    QueryProductDetailsParams queryProductDetailsParams =
                            QueryProductDetailsParams.newBuilder().setProductList(
                                    ImmutableList.of(
                                            QueryProductDetailsParams.Product.newBuilder()
                                                    .setProductId(productId)
                                                    .setProductType(
                                                            productType.equals("subs") ?
                                                                    BillingClient.ProductType.SUBS :
                                                                    BillingClient.ProductType.INAPP)
                                                    .build()))
                                    .build();

                    billingClient.queryProductDetailsAsync(
                            queryProductDetailsParams,
                            new ProductDetailsResponseListener() {
                                public void onProductDetailsResponse(@NonNull BillingResult billingResult,
                                                                     @NonNull List<ProductDetails> productDetailsList) {
                                    // check billingResult
                                    // process returned productDetailsList
                                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK){
                                        for (ProductDetails productDetail : productDetailsList)
                                            mProductDetails.put(productDetail.getProductId(), productDetail);
                                    }
                                }
                            }
                    );
                }
            }
            @Override
            public void onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                if (billingServiceDisconnectedListener != null)
                    billingServiceDisconnectedListener.onBillingServiceDisconnected();
            }
        });
    }

    void initializeBillingClient(Context context, String productType){
        purchasesUpdatedListener = new PurchasesUpdatedListener() {
            @Override
            public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (Purchase purchase : purchases) {
                        if (productType.equals(BillingClient.ProductType.INAPP))
                            handlePurchaseConsumable(purchase);
                        else handlePurchaseNonConsumable(purchase);
                    }
                } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                    // Handle an error caused by a user cancelling the purchase flow.
                    if (purchaseCanceledListener != null)
                        purchaseCanceledListener.onPurchaseCanceled(billingResult, purchases);
                } else {
                    // Handle any other error codes.
                    if (purchaseErrorListener != null)
                        purchaseErrorListener.onPurchaseError(billingResult, purchases);

                }
            }
        };

        billingClient = BillingClient.newBuilder(context)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases()
                .build();
    }

    void handlePurchaseConsumable(Purchase purchase) {
        // Purchase retrieved from BillingClient#queryPurchasesAsync or your PurchasesUpdatedListener.

        // Verify the purchase.
        // Ensure entitlement was not already granted for this purchaseToken.
        // Grant entitlement to the user.

        ConsumeParams consumeParams =
                ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();
        if (consumeResponseListener == null)
            consumeResponseListener = new ConsumeResponseListener() {
                @Override
                public void onConsumeResponse(@NonNull BillingResult billingResult, @NonNull String purchaseToken) {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        // Handle the success of the consume operation.
                    }
                }
            };

        billingClient.consumeAsync(consumeParams, consumeResponseListener);
    }

    void handlePurchaseNonConsumable(Purchase purchase) {
        if (acknowledgePurchaseResponseListener == null)
            acknowledgePurchaseResponseListener = new AcknowledgePurchaseResponseListener() {
                @Override
                public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {

                }
            };
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams =
                        AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                billingClient.acknowledgePurchase(acknowledgePurchaseParams, acknowledgePurchaseResponseListener);
            }
        }
    }

    // call this method onResume to handle cases when purchase is made but
    // your app lost track or was unaware of purchases
    public void justInCasePurchaseListener() {
        if (purchaseHistoryResponseListener == null) {
            this.purchasesResponseListener = new PurchasesResponseListener() {
                public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, @NonNull List purchases) {
                    // check billingResult
                    // process returned purchase list, e.g. display the plans user owns

                }
            };
        }
        billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                purchasesResponseListener
        );
    }

    // get purchase history
    public void getPurchaseHistory() {
        if (purchaseHistoryResponseListener == null) {
            this.purchaseHistoryResponseListener = new PurchaseHistoryResponseListener() {
                public void onPurchaseHistoryResponse(
                        @NonNull BillingResult billingResult, List purchasesHistoryList) {
                    // check billingResult
                    // process returned purchase history list, e.g. display purchase history
                }
            };
        }
        billingClient.queryPurchaseHistoryAsync(
                QueryPurchaseHistoryParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                purchaseHistoryResponseListener
        );


    }

    public void setPurchaseHistoryResponseListener(PurchaseHistoryResponseListener purchaseHistoryResponseListener) {
        this.purchaseHistoryResponseListener = purchaseHistoryResponseListener;
    }

    public void setPurchasesResponseListener(PurchasesResponseListener purchasesResponseListener) {
        this.purchasesResponseListener = purchasesResponseListener;
    }

    public void setConsumeResponseListener(ConsumeResponseListener consumeResponseListener) {
        this.consumeResponseListener = consumeResponseListener;
    }

    public void setPurchaseCanceledListener(PurchaseCanceledListener purchaseCanceledListener) {
        this.purchaseCanceledListener = purchaseCanceledListener;
    }

    public void setPurchaseErrorListener(PurchaseErrorListener purchaseErrorListener) {
        this.purchaseErrorListener = purchaseErrorListener;
    }

    public void setBillingServiceDisconnectedListener(BillingServiceDisconnectedListener billingServiceDisconnectedListener) {
        this.billingServiceDisconnectedListener = billingServiceDisconnectedListener;
    }

    public void setAcknowledgePurchaseResponseListener(AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener) {
        this.acknowledgePurchaseResponseListener = acknowledgePurchaseResponseListener;
    }
}

