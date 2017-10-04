/*
 * Copyright 2012-2014 One Platform Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onepf.oms.appstore.skubitUtils;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;

import com.skubit.android.billing.IBillingService;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.onepf.oms.Appstore;
import org.onepf.oms.AppstoreInAppBillingService;
import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.SkuManager;
import org.onepf.oms.appstore.SkubitAppstore;
import org.onepf.oms.appstore.googleUtils.IabException;
import org.onepf.oms.appstore.googleUtils.IabHelper;
import org.onepf.oms.appstore.googleUtils.IabResult;
import org.onepf.oms.appstore.googleUtils.Inventory;
import org.onepf.oms.appstore.googleUtils.Purchase;
import org.onepf.oms.appstore.googleUtils.Security;
import org.onepf.oms.appstore.googleUtils.SkuDetails;
import org.onepf.oms.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.onepf.oms.appstore.googleUtils.IabHelper.GET_SKU_DETAILS_ITEM_LIST;
import static org.onepf.oms.appstore.googleUtils.IabHelper.IABHELPER_BAD_RESPONSE;
import static org.onepf.oms.appstore.googleUtils.IabHelper.IABHELPER_INVALID_CONSUMPTION;
import static org.onepf.oms.appstore.googleUtils.IabHelper.IABHELPER_MISSING_TOKEN;
import static org.onepf.oms.appstore.googleUtils.IabHelper.IABHELPER_REMOTE_EXCEPTION;
import static org.onepf.oms.appstore.googleUtils.IabHelper.IABHELPER_SEND_INTENT_FAILED;
import static org.onepf.oms.appstore.googleUtils.IabHelper.IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE;
import static org.onepf.oms.appstore.googleUtils.IabHelper.IABHELPER_UNKNOWN_ERROR;
import static org.onepf.oms.appstore.googleUtils.IabHelper.IABHELPER_UNKNOWN_PURCHASE_RESPONSE;
import static org.onepf.oms.appstore.googleUtils.IabHelper.IABHELPER_USER_CANCELLED;
import static org.onepf.oms.appstore.googleUtils.IabHelper.IABHELPER_VERIFICATION_FAILED;
import static org.onepf.oms.appstore.googleUtils.IabHelper.INAPP_CONTINUATION_TOKEN;
import static org.onepf.oms.appstore.googleUtils.IabHelper.ITEM_TYPE_INAPP;
import static org.onepf.oms.appstore.googleUtils.IabHelper.ITEM_TYPE_SUBS;
import static org.onepf.oms.appstore.googleUtils.IabHelper.RESPONSE_BUY_INTENT;
import static org.onepf.oms.appstore.googleUtils.IabHelper.RESPONSE_CODE;
import static org.onepf.oms.appstore.googleUtils.IabHelper.RESPONSE_GET_SKU_DETAILS_LIST;
import static org.onepf.oms.appstore.googleUtils.IabHelper.RESPONSE_INAPP_ITEM_LIST;
import static org.onepf.oms.appstore.googleUtils.IabHelper.RESPONSE_INAPP_PURCHASE_DATA;
import static org.onepf.oms.appstore.googleUtils.IabHelper.RESPONSE_INAPP_PURCHASE_DATA_LIST;
import static org.onepf.oms.appstore.googleUtils.IabHelper.RESPONSE_INAPP_SIGNATURE;
import static org.onepf.oms.appstore.googleUtils.IabHelper.RESPONSE_INAPP_SIGNATURE_LIST;
import static org.onepf.oms.appstore.googleUtils.IabHelper.getResponseDesc;
import static org.onepf.oms.appstore.skubitUtils.BillingResponseCodes.RESULT_BILLING_UNAVAILABLE;
import static org.onepf.oms.appstore.skubitUtils.BillingResponseCodes.RESULT_ERROR;
import static org.onepf.oms.appstore.skubitUtils.BillingResponseCodes.RESULT_OK;

/**
 * Provides convenience methods for in-app billing. You can create one instance of this
 * class for your application and use it to process in-app billing operations.
 * It provides synchronous (blocking) and asynchronous (non-blocking) methods for
 * many common in-app billing operations, as well as automatic signature
 * verification.
 * <p/>
 * After instantiating, you must perform setup in order to start using the object.
 * To perform setup, call the {@link #startSetup} method and provide a listener;
 * that listener will be notified when setup is complete, after which (and not before)
 * you may call other methods.
 * <p/>
 * After setup is complete, you will typically want to request an inventory of owned
 * items and subscriptions. See {@link #queryInventory}, {@link #queryInventoryAsync}
 * and related methods.
 * <p/>
 * When you are done with this object, don't forget to call {@link #dispose}
 * to ensure proper cleanup. This object holds a binding to the in-app billing
 * service, which will leak unless you dispose of it correctly. If you created
 * the object on an Activity's onCreate method, then the recommended
 * place to dispose of it is the Activity's onDestroy method.
 * <p/>
 * A note about threading: When using this object from a background thread, you may
 * call the blocking versions of methods; when using from a UI thread, call
 * only the asynchronous versions and handle the results via callbacks.
 * Also, notice that you can only call one asynchronous operation at a time;
 * attempting to start a second asynchronous operation while the first one
 * has not yet completed will result in an exception being thrown.
 *
 * @author Bruno Oliveira (Google)
 * @author sisbell (Modified original class for Skubit support)
 */
public class SkubitIabHelper implements AppstoreInAppBillingService {
    /**
     * TODO: move to Options?
     * Skubit doesn't support getSkuDetails for more than 30 SKUs at once
     */
    public static final int QUERY_SKU_DETAILS_BATCH_SIZE = 30;

    // Is setup done?
    protected boolean mSetupDone = false;

    // Are subscriptions supported?
    protected boolean mSubscriptionsSupported = false;

    // Is an asynchronous operation in progress?
    // (only one at a time can be in progress)
    protected boolean mAsyncInProgress = false;

    // (for logging/debugging)
    // if mAsyncInProgress == true, what asynchronous operation is in progress?
    protected String mAsyncOperation = "";

    // Context we were passed during initialization
    protected Context mContext;

    // Connection to the service
    @Nullable
    protected IBillingService mService;

    @Nullable
    protected ServiceConnection mServiceConn;

    /**
     * for debug purposes
     */
    protected ComponentName mComponentName;

    // The request code used to launch purchase flow
    protected int mRequestCode;

    // The item type of the current purchase flow
    protected String mPurchasingItemType;

    // Public key for verifying signature, in base64 encoding
    @Nullable
    protected String mSignatureBase64 = null;

    /**
     * TODO: IabHelper for Skubit and OpenStore must not be same
     */
    private Appstore mAppstore;

    /**
     * Creates an instance. After creation, it will not yet be ready to use. You must perform
     * setup by calling {@link #startSetup} and wait for setup to complete. This constructor does not
     * block and is safe to call from a UI thread.
     *
     * @param context         Your application or Activity context. Needed to bind to the in-app billing service.
     * @param base64PublicKey Your application's public key, encoded in base64.
     *                        This is used for verification of purchase signatures. You can find your app's base64-encoded
     *                        public key in your application's page on Google Play Developer Console. Note that this
     *                        is NOT your "developer public key".
     * @param appstore        TODO
     */
    public SkubitIabHelper(@Nullable Context context, String base64PublicKey, Appstore appstore) {
        if (context == null) {
            throw new IllegalArgumentException("context is null");
        }
        mContext = context.getApplicationContext();
        mSignatureBase64 = base64PublicKey;
        mAppstore = appstore;
        Logger.d("Skubit IAB helper created.");
    }

    /**
     * Starts the setup process. This will start up the setup process asynchronously.
     * You will be notified through the listener when the setup process is complete.
     * This method is safe to call from a UI thread.
     *
     * @param listener The listener to notify when the setup process is complete.
     */
    public void startSetup(@Nullable final IabHelper.OnIabSetupFinishedListener listener) {
        // If already set up, can't do it again.
        if (mSetupDone) throw new IllegalStateException("IAB helper is already set up.");

        // Connection to IAB service
        Logger.d("Starting in-app billing setup.");
        mServiceConn = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                Logger.d("Billing service disconnected.");
                mService = null;
            }

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Logger.d("Billing service connected.");
                mService = getServiceFromBinder(service);
                mComponentName = name;
                String packageName = mContext.getPackageName();
                try {
                    Logger.d("Checking for in-app billing 1 support.");

                    // check for in-app billing v1 support
                    int response = mService.isBillingSupported(1, packageName, ITEM_TYPE_INAPP);
                    if (response != RESULT_OK) {
                        if (listener != null) listener.onIabSetupFinished(new IabResult(response,
                                "Error checking for billing v1 support."));

                        // if in-app purchases aren't supported, neither are subscriptions.
                        mSubscriptionsSupported = false;
                        return;
                    }
                    Logger.d("In-app billing version 1 supported for ", packageName);

                    // check for v3 subscriptions support
                    response = mService.isBillingSupported(1, packageName, ITEM_TYPE_SUBS);
                    if (response == RESULT_OK) {
                        Logger.d("Subscriptions AVAILABLE.");
                        mSubscriptionsSupported = true;
                    } else {
                        Logger.d("Subscriptions NOT AVAILABLE. Response: ", response);
                    }

                    mSetupDone = true;
                } catch (RemoteException e) {
                    if (listener != null) {
                        listener.onIabSetupFinished(new IabResult(IABHELPER_REMOTE_EXCEPTION,
                                "RemoteException while setting up in-app billing."));
                    }
                    Logger.e("RemoteException while setting up in-app billing", e);
                    return;
                }

                if (listener != null) {
                    listener.onIabSetupFinished(new IabResult(RESULT_OK, "Setup successful."));
                }
            }
        };

        Intent serviceIntent = getServiceIntent();
        final List<ResolveInfo> infoList = mContext.getPackageManager().queryIntentServices(serviceIntent, 0);
        if (infoList != null && !infoList.isEmpty()) {
            // service available to handle that Intent
            mContext.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
        } else {
            // no service available to handle that Intent
            if (listener != null) {
                listener.onIabSetupFinished(new IabResult(RESULT_BILLING_UNAVAILABLE,
                        "Billing service unavailable on device."));
            }
        }
    }

    /**
     * IabHelper code is shared between OpenStore and Skubit, but services has different names
     */
    protected Intent getServiceIntent() {
        final Intent intent = new Intent(SkubitAppstore.VENDING_ACTION);
        intent.setPackage(SkubitAppstore.SKUBIT_INSTALLER);
        return intent;
    }

    /**
     * Override to return needed service interface
     */
    @Nullable
    protected IBillingService getServiceFromBinder(IBinder service) {
        return IBillingService.Stub.asInterface(service);
    }

    /**
     * Dispose of object, releasing resources. It's very important to call this
     * method when you are done with this object. It will release any resources
     * used by it such as service connections. Naturally, once the object is
     * disposed of, it can't be used again.
     */
    public void dispose() {
        Logger.d("Disposing.");
        mSetupDone = false;
        if (mServiceConn != null) {
            Logger.d("Unbinding from service.");
            if (mContext != null) mContext.unbindService(mServiceConn);
            mServiceConn = null;
            mService = null;
            mPurchaseListener = null;
        }
    }

    /**
     * Returns whether subscriptions are supported.
     */
    public boolean subscriptionsSupported() {
        return mSubscriptionsSupported;
    }

    public void setSubscriptionsSupported(boolean subscriptionsSupported) {
        mSubscriptionsSupported = subscriptionsSupported;
    }

    public void setSetupDone(boolean setupDone) {
        mSetupDone = setupDone;
    }


    // The listener registered on launchPurchaseFlow, which we have to call back when
    // the purchase finishes
    @Nullable
    IabHelper.OnIabPurchaseFinishedListener mPurchaseListener;

    /**
     * Initiate the UI flow for an in-app purchase. Call this method to initiate an in-app purchase,
     * which will involve bringing up the Google Play screen. The calling activity will be paused while
     * the user interacts with Google Play, and the result will be delivered via the activity's
     * {@link android.app.Activity#onActivityResult} method, at which point you must call
     * this object's {@link #handleActivityResult} method to continue the purchase flow. This method
     * MUST be called from the UI thread of the Activity.
     *
     * @param act         The calling activity.
     * @param sku         The sku of the item to purchase.
     * @param itemType    indicates if it's a product or a subscription (ITEM_TYPE_INAPP or ITEM_TYPE_SUBS)
     * @param requestCode A request code (to differentiate from other responses --
     *                    as in {@link android.app.Activity#startActivityForResult}).
     * @param listener    The listener to notify when the purchase process finishes
     * @param extraData   Extra data (developer payload), which will be returned with the purchase data
     *                    when the purchase completes. This extra data will be permanently bound to that purchase
     *                    and will always be returned when the purchase is queried.
     */
    public void launchPurchaseFlow(@NotNull Activity act, String sku, @NotNull String itemType, List<String> oldSkus, int requestCode,
                                   @Nullable IabHelper.OnIabPurchaseFinishedListener listener, String extraData) {
        checkSetupDone("launchPurchaseFlow");
        flagStartAsync("launchPurchaseFlow");
        IabResult result;

        if (itemType.equals(ITEM_TYPE_SUBS) && !mSubscriptionsSupported) {
            IabResult r = new IabResult(IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE,
                    "Subscriptions are not available.");
            if (listener != null) listener.onIabPurchaseFinished(r, null);
            flagEndAsync();
            return;
        }

        try {
            Logger.d("Constructing buy intent for ", sku, ", item type: ", itemType);
            if (mService == null) {
                Logger.e("In-app billing error: Unable to buy item, Error response: service is not connected.");
                result = new IabResult(RESULT_ERROR, "Unable to buy item");
                if (listener != null) listener.onIabPurchaseFinished(result, null);
                flagEndAsync();
                return;
            }
            Bundle buyIntentBundle = mService.getBuyIntent(1, getPackageName(), sku, itemType, extraData);
            int response = getResponseCodeFromBundle(buyIntentBundle);
            if (response != RESULT_OK) {
                Logger.e("In-app billing error: Unable to buy item, Error response: " + getResponseDesc(response));

                result = new IabResult(response, "Unable to buy item");
                if (listener != null) listener.onIabPurchaseFinished(result, null);
                flagEndAsync();
                return;
            }

            PendingIntent pendingIntent = buyIntentBundle.getParcelable(RESPONSE_BUY_INTENT);
            Logger.d("Launching buy intent for ", sku, ". Request code: ", requestCode);
            mRequestCode = requestCode;
            mPurchaseListener = listener;
            mPurchasingItemType = itemType;
            act.startIntentSenderForResult(pendingIntent.getIntentSender(),
                    requestCode, new Intent(),
                    Integer.valueOf(0), Integer.valueOf(0),
                    Integer.valueOf(0));
        } catch (SendIntentException e) {
            Logger.e("In-app billing error: SendIntentException while launching purchase flow for sku " + sku);
            e.printStackTrace();

            result = new IabResult(IABHELPER_SEND_INTENT_FAILED, "Failed to send intent.");
            if (listener != null) listener.onIabPurchaseFinished(result, null);
        } catch (RemoteException e) {
            Logger.e("In-app billing error: RemoteException while launching purchase flow for sku " + sku);
            e.printStackTrace();

            result = new IabResult(IABHELPER_REMOTE_EXCEPTION, "Remote exception while starting purchase flow");
            if (listener != null) listener.onIabPurchaseFinished(result, null);
        }
        flagEndAsync();
    }

    /**
     * Handles an activity result that's part of the purchase flow in in-app billing. If you
     * are calling {@link #launchPurchaseFlow}, then you must call this method from your
     * Activity's {@link android.app.Activity#onActivityResult} method. This method
     * MUST be called from the UI thread of the Activity.
     *
     * @param requestCode The requestCode as you received it.
     * @param resultCode  The resultCode as you received it.
     * @param data        The data (Intent) as you received it.
     * @return Returns true if the result was related to a purchase flow and was handled;
     * false if the result was not related to a purchase, in which case you should
     * handle it normally.
     */
    public boolean handleActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IabResult result;
        if (requestCode != mRequestCode) return false;

        checkSetupDone("handleActivityResult");

        // end of async purchase operation
        flagEndAsync();

        if (data == null) {
            Logger.e("In-app billing error: Null data in IAB activity result.");
            result = new IabResult(IABHELPER_BAD_RESPONSE, "Null data in IAB result");
            if (mPurchaseListener != null) mPurchaseListener.onIabPurchaseFinished(result, null);
            return true;
        }

        int responseCode = getResponseCodeFromIntent(data);
        String purchaseData = data.getStringExtra(RESPONSE_INAPP_PURCHASE_DATA);
        String dataSignature = data.getStringExtra(RESPONSE_INAPP_SIGNATURE);

        if (resultCode == Activity.RESULT_OK && responseCode == RESULT_OK) {
            processPurchaseSuccess(data, purchaseData, dataSignature);
        } else if (resultCode == Activity.RESULT_OK) {
            // result code was OK, but in-app billing response was not OK.
            processPurchaseFail(responseCode);
        } else if (resultCode == Activity.RESULT_CANCELED) {
            Logger.d("Purchase canceled - Response: ", getResponseDesc(responseCode));
            result = new IabResult(IABHELPER_USER_CANCELLED, "User canceled.");
            if (mPurchaseListener != null) mPurchaseListener.onIabPurchaseFinished(result, null);
        } else {
            Logger.e("In-app billing error: Purchase failed. Result code: " + Integer.toString(resultCode)
                    + ". Response: " + getResponseDesc(responseCode));
            result = new IabResult(IABHELPER_UNKNOWN_PURCHASE_RESPONSE, "Unknown purchase response.");
            if (mPurchaseListener != null) mPurchaseListener.onIabPurchaseFinished(result, null);
        }
        return true;
    }

    public void processPurchaseFail(int responseCode) {
        IabResult result;
        Logger.d("Result code was OK but in-app billing response was not OK: ", getResponseDesc(responseCode));
        if (mPurchaseListener != null) {
            result = new IabResult(responseCode, "Problem purchashing item.");
            mPurchaseListener.onIabPurchaseFinished(result, null);
        }
    }

    public void processPurchaseSuccess(@NotNull Intent data, @Nullable String purchaseData, @Nullable String dataSignature) {
        IabResult result;
        Logger.d("Successful resultcode from purchase activity.");
        Logger.d("Purchase data: ", purchaseData);
        Logger.d("Data signature: ", dataSignature);
        Logger.d("Extras: ", data.getExtras());
        Logger.d("Expected item type: ", mPurchasingItemType);

        if (purchaseData == null || dataSignature == null) {
            Logger.e("In-app billing error: BUG: either purchaseData or dataSignature is null.");
            Logger.d("Extras: ", data.getExtras());
            result = new IabResult(IABHELPER_UNKNOWN_ERROR, "IAB returned null purchaseData or dataSignature");
            if (mPurchaseListener != null) mPurchaseListener.onIabPurchaseFinished(result, null);
            return;
        }

        Purchase purchase;
        try {
            purchase = new Purchase(mPurchasingItemType, purchaseData, dataSignature, mAppstore.getAppstoreName());
            String sku = purchase.getSku();
            purchase.setSku(SkuManager.getInstance().getSku(mAppstore.getAppstoreName(), sku));

            if (!isValidDataSignature(mSignatureBase64, purchaseData, dataSignature)) {
                Logger.e("In-app billing error: Purchase signature verification FAILED for sku " + sku);
                result = new IabResult(IABHELPER_VERIFICATION_FAILED, "Signature verification failed for sku " + sku);
                if (mPurchaseListener != null)
                    mPurchaseListener.onIabPurchaseFinished(result, purchase);
                return;
            }

            Logger.d("Purchase signature successfully verified.");
        } catch (JSONException e) {
            Logger.e("In-app billing error: Failed to parse purchase data.");
            e.printStackTrace();
            result = new IabResult(IABHELPER_BAD_RESPONSE, "Failed to parse purchase data.");
            if (mPurchaseListener != null) mPurchaseListener.onIabPurchaseFinished(result, null);
            return;
        }

        if (mPurchaseListener != null) {
            mPurchaseListener.onIabPurchaseFinished(new IabResult(RESULT_OK, "Success"), purchase);
        }
    }

    @Nullable
    public Inventory queryInventory(boolean querySkuDetails, List<String> moreSkus) throws IabException {
        return queryInventory(querySkuDetails, moreSkus, null);
    }

    /**
     * Queries the inventory. This will query all owned items from the server, as well as
     * information on additional skus, if specified. This method may block or take long to execute.
     * Do not call from a UI thread. For that, use the non-blocking version {@link #queryInventoryAsync}.
     *
     * @param querySkuDetails if true, SKU details (price, description, etc) will be queried as well
     *                        as purchase information.
     * @param moreItemSkus    additional PRODUCT skus to query information on, regardless of ownership.
     *                        Ignored if null or if querySkuDetails is false.
     * @param moreSubsSkus    additional SUBSCRIPTIONS skus to query information on, regardless of ownership.
     *                        Ignored if null or if querySkuDetails is false.
     * @throws IabException if a problem occurs while refreshing the inventory.
     */
    public Inventory queryInventory(boolean querySkuDetails, List<String> moreItemSkus,
                                    List<String> moreSubsSkus) throws IabException {
        checkSetupDone("queryInventory");
        try {
            Inventory inv = new Inventory();
            int r = queryPurchases(inv, ITEM_TYPE_INAPP);
            if (r != RESULT_OK) {
                throw new IabException(r, "Error refreshing inventory (querying owned items).");
            }

            if (querySkuDetails) {
                r = querySkuDetails(ITEM_TYPE_INAPP, inv, moreItemSkus);
                if (r != RESULT_OK) {
                    throw new IabException(r, "Error refreshing inventory (querying prices of items).");
                }
            }

            // if subscriptions are supported, then also query for subscriptions
            if (mSubscriptionsSupported) {
                r = queryPurchases(inv, ITEM_TYPE_SUBS);
                if (r != RESULT_OK) {
                    throw new IabException(r, "Error refreshing inventory (querying owned subscriptions).");
                }

                if (querySkuDetails) {
                    r = querySkuDetails(ITEM_TYPE_SUBS, inv, moreSubsSkus);
                    if (r != RESULT_OK) {
                        throw new IabException(r, "Error refreshing inventory (querying prices of subscriptions).");
                    }
                }
            }

            return inv;
        } catch (RemoteException e) {
            throw new IabException(IABHELPER_REMOTE_EXCEPTION, "Remote exception while refreshing inventory.", e);
        } catch (JSONException e) {
            throw new IabException(IABHELPER_BAD_RESPONSE, "Error parsing JSON response while refreshing inventory.", e);
        }
    }

    /**
     * Listener that notifies when an inventory query operation completes.
     */
    public interface QueryInventoryFinishedListener {
        /**
         * Called to notify that an inventory query operation completed.
         *
         * @param result The result of the operation.
         * @param inv    The inventory.
         */
        public void onQueryInventoryFinished(IabResult result, Inventory inv);
    }


    /**
     * Asynchronous wrapper for inventory query. This will perform an inventory
     * query as described in {@link #queryInventory}, but will do so asynchronously
     * and call back the specified listener upon completion. This method is safe to
     * call from a UI thread.
     *
     * @param querySkuDetails as in {@link #queryInventory}
     * @param moreSkus        as in {@link #queryInventory}
     * @param listener        The listener to notify when the refresh operation completes.
     */
    public void queryInventoryAsync(final boolean querySkuDetails,
                                    final List<String> moreSkus,
                                    @NotNull final QueryInventoryFinishedListener listener) {
        final Handler handler = new Handler();
        checkSetupDone("queryInventory");
        flagStartAsync("refresh inventory");
        (new Thread(new Runnable() {
            public void run() {
                IabResult result = new IabResult(RESULT_OK, "Inventory refresh successful.");
                Inventory inv = null;
                try {
                    inv = queryInventory(querySkuDetails, moreSkus);
                } catch (IabException ex) {
                    result = ex.getResult();
                }

                flagEndAsync();

                final IabResult result_f = result;
                final Inventory inv_f = inv;
                handler.post(new Runnable() {
                    public void run() {
                        listener.onQueryInventoryFinished(result_f, inv_f);
                    }
                });
            }
        })).start();
    }

    public void queryInventoryAsync(@NotNull QueryInventoryFinishedListener listener) {
        queryInventoryAsync(true, null, listener);
    }

    public void queryInventoryAsync(boolean querySkuDetails, @NotNull QueryInventoryFinishedListener listener) {
        queryInventoryAsync(querySkuDetails, null, listener);
    }


    /**
     * Consumes a given in-app product. Consuming can only be done on an item
     * that's owned, and as a reslibs
     * res
     * src
     * unity_src
     * wp8_dll_srcult of consumption, the user will no longer own it.
     * This method may block or take long to return. Do not call from the UI thread.
     * For that, see {@link #consumeAsync}.
     *
     * @param itemInfo The PurchaseInfo that represents the item to consume.
     * @throws IabException if there is a problem during consumption.
     */
    public void consume(@NotNull Purchase itemInfo) throws IabException {
        checkSetupDone("consume");

        if (!itemInfo.getItemType().equals(ITEM_TYPE_INAPP)) {
            throw new IabException(IABHELPER_INVALID_CONSUMPTION,
                    "Items of type '" + itemInfo.getItemType() + "' can't be consumed.");
        }

        try {
            String token = itemInfo.getToken();
            String sku = itemInfo.getSku();
            if (token == null || token.equals("")) {
                Logger.e("In-app billing error: Can't consume ", sku, ". No token.");
                throw new IabException(IABHELPER_MISSING_TOKEN, "PurchaseInfo is missing token for sku: "
                        + sku + " " + itemInfo);
            }

            Logger.d("Consuming sku: ", sku, ", token: ", token);
            if (mService == null) {
                Logger.d("Error consuming consuming sku ", sku, ". Service is not connected.");
                throw new IabException(RESULT_ERROR, "Error consuming sku " + sku);
            }
            int response = mService.consumePurchase(1, getPackageName(), token);
            if (response == RESULT_OK) {
                Logger.d("Successfully consumed sku: ", sku);
            } else {
                Logger.d("Error consuming consuming sku ", sku, ". ", getResponseDesc(response));
                throw new IabException(response, "Error consuming sku " + sku);
            }
        } catch (RemoteException e) {
            throw new IabException(IABHELPER_REMOTE_EXCEPTION, "Remote exception while consuming. PurchaseInfo: " + itemInfo, e);
        }
    }

    public String getPackageName() {
        return mContext.getPackageName();
    }

    /**
     * Callback that notifies when a consumption operation finishes.
     */
    public interface OnConsumeFinishedListener {
        /**
         * Called to notify that a consumption has finished.
         *
         * @param purchase The purchase that was (or was to be) consumed.
         * @param result   The result of the consumption operation.
         */
        public void onConsumeFinished(Purchase purchase, IabResult result);
    }

    /**
     * Callback that notifies when a multi-item consumption operation finishes.
     */
    public interface OnConsumeMultiFinishedListener {
        /**
         * Called to notify that a consumption of multiple items has finished.
         *
         * @param purchases The purchases that were (or were to be) consumed.
         * @param results   The results of each consumption operation, corresponding to each
         *                  sku.
         */
        public void onConsumeMultiFinished(List<Purchase> purchases, List<IabResult> results);
    }

    /**
     * Asynchronous wrapper to item consumption. Works like {@link #consume}, but
     * performs the consumption in the background and notifies completion through
     * the provided listener. This method is safe to call from a UI thread.
     *
     * @param purchase The purchase to be consumed.
     * @param listener The listener to notify when the consumption operation finishes.
     */
    public void consumeAsync(Purchase purchase, OnConsumeFinishedListener listener) {
        checkSetupDone("consume");
        List<Purchase> purchases = new ArrayList<Purchase>();
        purchases.add(purchase);
        consumeAsyncInternal(purchases, listener, null);
    }

    /**
     * Same as {@link org.onepf.oms.appstore.googleUtils.IabHelper#consumeAsync(Purchase, org.onepf.oms.appstore.googleUtils.IabHelper.OnConsumeFinishedListener)},
     * but for multiple items at once.
     *
     * @param purchases The list of PurchaseInfo objects representing the purchases to consume.
     * @param listener  The listener to notify when the consumption operation finishes.
     */
    public void consumeAsync(@NotNull List<Purchase> purchases, OnConsumeMultiFinishedListener listener) {
        checkSetupDone("consume");
        consumeAsyncInternal(purchases, null, listener);
    }


    /**
     * Checks that setup was done; if not, throws an exception.
     * <p/>
     * <p>OpenIAB specific: NOT USED</p>
     * <p/>
     * <code>setupDone</code> state is tracked by {@link OpenIabHelper}, so check here duplicates
     * already existed logic. At the same time we discovered race condition problem based on end-user
     * crash reports
     * <p>Time to time when common onSetupSuccessfulListener calls queryInventory() IabHelper.setupDone is false
     * We tried to solve it with volatile modifier and synchronized blocks. Both approaches failed.
     * Reasons are still unclear. The same flow in wrapper works perfect (OpenIabHelper)
     * <p><pre>
     *  java.lang.IllegalStateException: IAB helper is not set up. Can't perform operation: queryInventory
     * at IabHelper.checkSetupDone(IabHelper.java:806)
     * at IabHelper.queryInventory(IabHelper.java:566)
     * at OpenIabHelper.queryInventory(OpenIabHelper.java:930)
     * at OpenIabHelper$5.run(OpenIabHelper.java:957)
     * at java.lang.Thread.run(Thread.java:864)
     * </pre></p>
     *
     * @see <a href="https://github.com/onepf/OpenIAB/issues/199">https://github.com/onepf/OpenIAB/issues/199</a>
     */
    void checkSetupDone(String operation) {
    }

    // Workaround to bug where sometimes response codes come as Long instead of Integer
    int getResponseCodeFromBundle(@NotNull Bundle b) {
        Object o = b.get(RESPONSE_CODE);
        if (o == null) {
            Logger.d("Bundle with null response code, assuming OK (known issue)");
            return RESULT_OK;
        } else if (o instanceof Integer) return ((Integer) o).intValue();
        else if (o instanceof Long) return (int) ((Long) o).longValue();
        else {
            Logger.e("In-app billing error: ", "Unexpected type for bundle response code.");
            Logger.e("In-app billing error: ", o.getClass().getName());
            throw new RuntimeException("Unexpected type for bundle response code: " + o.getClass().getName());
        }
    }

    // Workaround to bug where sometimes response codes come as Long instead of Integer
    int getResponseCodeFromIntent(@NotNull Intent i) {
        Object o = i.getExtras().get(RESPONSE_CODE);
        if (o == null) {
            Logger.e("In-app billing error: Intent with no response code, assuming OK (known issue)");
            return RESULT_OK;
        } else if (o instanceof Integer) return ((Integer) o).intValue();
        else if (o instanceof Long) return (int) ((Long) o).longValue();
        else {
            Logger.e("In-app billing error: Unexpected type for intent response code.");
            Logger.e("In-app billing error: ", o.getClass().getName());
            throw new RuntimeException("Unexpected type for intent response code: " + o.getClass().getName());
        }
    }

    void flagStartAsync(String operation) {
        if (mAsyncInProgress) throw new IllegalStateException("Can't start async operation (" +
                operation + ") because another async operation(" + mAsyncOperation + ") is in progress.");
        mAsyncOperation = operation;
        mAsyncInProgress = true;
        Logger.d("Starting async operation: ", operation);
    }

    void flagEndAsync() {
        Logger.d("Ending async operation: ", mAsyncOperation);
        mAsyncOperation = "";
        mAsyncInProgress = false;
    }


    int queryPurchases(@NotNull Inventory inv, String itemType) throws JSONException, RemoteException {
        // Query purchases
        Logger.d("Querying owned items, item type: ", itemType);
        Logger.d("Package name: ", getPackageName());
        boolean verificationFailed = false;
        String continueToken = null;

        do {
            Logger.d("Calling getPurchases with continuation token: ", continueToken);
            if (mService == null) {
                Logger.d("getPurchases() failed: service is not connected.");
                return RESULT_ERROR;
            }
            Bundle ownedItems = mService.getPurchases(1, getPackageName(), itemType, continueToken);

            int response = getResponseCodeFromBundle(ownedItems);
            Logger.d("Owned items response: ", response);
            if (response != RESULT_OK) {
                Logger.d("getPurchases() failed: ", getResponseDesc(response));
                return response;
            }
            if (!ownedItems.containsKey(RESPONSE_INAPP_ITEM_LIST)
                    || !ownedItems.containsKey(RESPONSE_INAPP_PURCHASE_DATA_LIST)
                    || !ownedItems.containsKey(RESPONSE_INAPP_SIGNATURE_LIST)) {
                Logger.e("In-app billing error: Bundle returned from getPurchases() doesn't contain required fields.");
                return IABHELPER_BAD_RESPONSE;
            }

            ArrayList<String> ownedSkus = ownedItems.getStringArrayList(RESPONSE_INAPP_ITEM_LIST);
            ArrayList<String> purchaseDataList = ownedItems.getStringArrayList(RESPONSE_INAPP_PURCHASE_DATA_LIST);
            ArrayList<String> signatureList = ownedItems.getStringArrayList(RESPONSE_INAPP_SIGNATURE_LIST);

            for (int i = 0; i < purchaseDataList.size(); ++i) {
                String purchaseData = purchaseDataList.get(i);
                String signature = signatureList.get(i);
                String sku = ownedSkus.get(i);

                if (isValidDataSignature(mSignatureBase64, purchaseData, signature)) {
                    Logger.d("Sku is owned: ", sku);
                    Purchase purchase = new Purchase(itemType, purchaseData, signature, mAppstore.getAppstoreName());
                    String storeSku = purchase.getSku();
                    purchase.setSku(SkuManager.getInstance().getSku(mAppstore.getAppstoreName(), storeSku));

                    if (TextUtils.isEmpty(purchase.getToken())) {
                        Logger.w("In-app billing warning: BUG: empty/null token!");
                        Logger.d("Purchase data: ", purchaseData);
                    }

                    // Record ownership and token
                    inv.addPurchase(purchase);
                } else {
                    Logger.w("In-app billing warning: Purchase signature verification **FAILED**. Not adding item.");
                    Logger.d("   Purchase data: ", purchaseData);
                    Logger.d("   Signature: ", signature);
                    verificationFailed = true;
                }
            }

            continueToken = ownedItems.getString(INAPP_CONTINUATION_TOKEN);
            Logger.d("Continuation token: ", continueToken);
        } while (!TextUtils.isEmpty(continueToken));

        return verificationFailed ? IABHELPER_VERIFICATION_FAILED : RESULT_OK;
    }

    /**
     * @param inv      - Inventory with application SKUs
     * @param moreSkus - storeSKUs (processed in {@link OpenIabHelper#queryInventory(boolean, List, List)}
     */
    int querySkuDetails(String itemType, @NotNull Inventory inv, @Nullable List<String> moreSkus) throws RemoteException, JSONException {
        Logger.d("querySkuDetails() Querying SKU details.");
        final SkuManager skuManager = SkuManager.getInstance();
        final String appstoreName = mAppstore.getAppstoreName();

        Set<String> storeSkus = new TreeSet<String>();
        for (String sku : inv.getAllOwnedSkus(itemType)) {
            storeSkus.add(skuManager.getStoreSku(appstoreName, sku));
        }
        if (moreSkus != null) {
            for (String sku : moreSkus) {
                storeSkus.add(skuManager.getStoreSku(appstoreName, sku));
            }
        }
        if (storeSkus.isEmpty()) {
            Logger.d("querySkuDetails(): nothing to do because there are no SKUs.");
            return RESULT_OK;
        }

        // Split the sku list in blocks of no more than QUERY_SKU_DETAILS_BATCH_SIZE elements.
        ArrayList<ArrayList<String>> batches = new ArrayList<ArrayList<String>>();
        ArrayList<String> tmpBatch = new ArrayList<String>(QUERY_SKU_DETAILS_BATCH_SIZE);
        int iSku = 0;
        for (String sku : storeSkus) {
            tmpBatch.add(sku);
            iSku++;
            if (tmpBatch.size() == QUERY_SKU_DETAILS_BATCH_SIZE || iSku == storeSkus.size()) {
                batches.add(tmpBatch);
                tmpBatch = new ArrayList<String>(QUERY_SKU_DETAILS_BATCH_SIZE);
            }
        }

        Logger.d("querySkuDetails() batches: ", batches.size(), ", ", batches);

        for (ArrayList<String> batch : batches) {
            Bundle querySkus = new Bundle();
            querySkus.putStringArrayList(GET_SKU_DETAILS_ITEM_LIST, batch);
            if (mService == null) {
                Logger.e("In-app billing error: unable to get sku details: service is not connected.");
                return IABHELPER_BAD_RESPONSE;
            }
            Bundle skuDetails = mService.getSkuDetails(1, mContext.getPackageName(), itemType, querySkus);

            if (!skuDetails.containsKey(RESPONSE_GET_SKU_DETAILS_LIST)) {
                int response = getResponseCodeFromBundle(skuDetails);
                if (response != RESULT_OK) {
                    Logger.d("getSkuDetails() failed: ", getResponseDesc(response));
                    return response;
                } else {
                    Logger.e("In-app billing error: getSkuDetails() returned a bundle with neither an error nor a detail list.");
                    return IABHELPER_BAD_RESPONSE;
                }
            }

            ArrayList<String> responseList = skuDetails.getStringArrayList(RESPONSE_GET_SKU_DETAILS_LIST);

            for (String thisResponse : responseList) {
                SkuDetails d = new SkuDetails(itemType, thisResponse);
                d.setSku(SkuManager.getInstance().getSku(appstoreName, d.getSku()));
                Logger.d("querySkuDetails() Got sku details: ", d);
                inv.addSkuDetails(d);
            }
        }

        return RESULT_OK;
    }

    void consumeAsyncInternal(@NotNull final List<Purchase> purchases,
                              @Nullable final OnConsumeFinishedListener singleListener,
                              @Nullable final OnConsumeMultiFinishedListener multiListener) {
        final Handler handler = new Handler();
        flagStartAsync("consume");
        (new Thread(new Runnable() {
            public void run() {
                final List<IabResult> results = new ArrayList<IabResult>();
                for (Purchase purchase : purchases) {
                    try {
                        consume(purchase);
                        results.add(new IabResult(RESULT_OK, "Successful consume of sku " + purchase.getSku()));
                    } catch (IabException ex) {
                        results.add(ex.getResult());
                    }
                }

                flagEndAsync();
                if (singleListener != null) {
                    handler.post(new Runnable() {
                        public void run() {
                            singleListener.onConsumeFinished(purchases.get(0), results.get(0));
                        }
                    });
                }
                if (multiListener != null) {
                    handler.post(new Runnable() {
                        public void run() {
                            multiListener.onConsumeMultiFinished(purchases, results);
                        }
                    });
                }
            }
        })).start();
    }

    boolean isValidDataSignature(@Nullable String base64PublicKey, @NotNull String purchaseData, @NotNull String signature) {
        if (base64PublicKey == null) return true;
        boolean isValid = Security.verifyPurchase(base64PublicKey, purchaseData, signature);
        if (!isValid) {
            Logger.w("In-app billing warning: Purchase signature verification **FAILED**.");
        }
        return isValid;
    }
}
