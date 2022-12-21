package com.sj.sjgoogleplaybilling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;

import java.util.List;

public interface PurchaseCanceledListener {
    void onPurchaseCanceled(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases);
}

