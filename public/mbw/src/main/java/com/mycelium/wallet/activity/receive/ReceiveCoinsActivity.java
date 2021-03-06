/*
 * Copyright 2013, 2014 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.mycelium.wallet.activity.receive;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import com.google.common.base.Preconditions;
import com.mrd.bitlib.model.Address;
import com.mrd.bitlib.util.CoinUtil;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.activity.GetAmountActivity;
import com.mycelium.wallet.activity.util.QrImageView;
import com.mycelium.wallet.event.SyncFailed;
import com.mycelium.wallet.event.SyncStopped;
import com.mycelium.wapi.model.TransactionSummary;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.currency.ExactBitcoinValue;
import com.squareup.otto.Subscribe;
import com.mycelium.wapi.wallet.currency.BitcoinValue;
import com.mycelium.wapi.wallet.currency.CurrencyValue;
import com.mycelium.wapi.wallet.currency.ExchangeBasedBitcoinValue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

//todo HD for the future: keep receiving slots for 20 addresses. assign a name

public class ReceiveCoinsActivity extends Activity {

   private static final int GET_AMOUNT_RESULT_CODE = 1;
   private static final String LAST_ADDRESS_BALANCE = "lastAddressBalance";
   private static final String RECEIVING_SINCE = "receivingSince";
   private static final String AMOUNT = "amount";
   public static final String SYNC_ERRORS = "syncErrors";
   private static final int MAX_SYNC_ERRORS = 8;

   @InjectView(R.id.tvAmountLabel) TextView tvAmountLabel;
   @InjectView(R.id.tvAmount) TextView tvAmount;
   @InjectView(R.id.tvWarning) TextView tvWarning;
   @InjectView(R.id.tvTitle) TextView tvTitle;
   @InjectView(R.id.tvAddress1) TextView tvAddress1;
   @InjectView(R.id.tvAddress2) TextView tvAddress2;
   @InjectView(R.id.tvAddress3) TextView tvAddress3;
   @InjectView(R.id.ivNfc) ImageView ivNfc;
   @InjectView(R.id.ivQrCode) QrImageView ivQrCode;
   @InjectView(R.id.btShare) Button btShare;

   private MbwManager _mbwManager;
   private Address _address;
   private boolean _havePrivateKey;
   private CurrencyValue _amount;
   private Long _receivingSince;
   private CurrencyValue _lastAddressBalance;
   private int _syncErrors = 0;

   public static void callMe(Activity currentActivity, Address address, boolean havePrivateKey) {
      Intent intent = new Intent(currentActivity, ReceiveCoinsActivity.class);
      intent.putExtra("address", address);
      intent.putExtra("havePrivateKey", havePrivateKey);
      currentActivity.startActivity(intent);
   }

   /**
    * Called when the activity is first created.
    */
   @Override
   public void onCreate(Bundle savedInstanceState) {
      this.requestWindowFeature(Window.FEATURE_NO_TITLE);
      this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
      super.onCreate(savedInstanceState);
      setContentView(R.layout.receive_coins_activity);
      ButterKnife.inject(this);

      _mbwManager = MbwManager.getInstance(getApplication());

      // Get intent parameters
      _address = Preconditions.checkNotNull((Address) getIntent().getSerializableExtra("address"));
      _havePrivateKey = getIntent().getBooleanExtra("havePrivateKey", false);

      // Load saved state
      if (savedInstanceState != null) {
         _amount = (CurrencyValue) savedInstanceState.getSerializable(AMOUNT);
         _receivingSince = savedInstanceState.getLong(RECEIVING_SINCE);
         _lastAddressBalance = (CurrencyValue) savedInstanceState.getSerializable(LAST_ADDRESS_BALANCE);
         _syncErrors = savedInstanceState.getInt(SYNC_ERRORS);
      } else {
         _receivingSince = new Date().getTime();
      }

      // Amount Hint
      tvAmount.setHint(getResources().getString(R.string.amount_hint_denomination,
            _mbwManager.getBitcoinDenomination().toString()));
      shareByNfc();
   }

   @TargetApi(16)
   protected void shareByNfc() {
      if (Build.VERSION.SDK_INT < 16) {
         // the function isNdefPushEnabled is only available for SdkVersion >= 16
         // We would be theoretically able to push the message over Ndef, but it is not
         // possible to check if Ndef/NFC is available or not - so dont try it at all, if
         // SdkVersion is too low
         return;
      }

      NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
      if (nfc != null && nfc.isNdefPushEnabled()) {
         nfc.setNdefPushMessageCallback(new NfcAdapter.CreateNdefMessageCallback() {
            @Override
            public NdefMessage createNdefMessage(NfcEvent event) {
               NdefRecord uriRecord = NdefRecord.createUri(getPaymentUri());
               return new NdefMessage(new NdefRecord[]{uriRecord});
            }
         }, this);
         ivNfc.setVisibility(View.VISIBLE);
         ivNfc.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
               Utils.showSimpleMessageDialog(ReceiveCoinsActivity.this, getString(R.string.nfc_payment_request_hint));
            }
         });
      } else {
         ivNfc.setVisibility(View.GONE);
      }
   }

   @Override
   protected void onSaveInstanceState(Bundle outState) {
      if (!CurrencyValue.isNullOrZero(_amount)) {
         outState.putSerializable(AMOUNT, _amount);
      }
      outState.putLong(RECEIVING_SINCE, _receivingSince);
      outState.putInt(SYNC_ERRORS, _syncErrors);
      outState.putSerializable(LAST_ADDRESS_BALANCE, _lastAddressBalance);
      super.onSaveInstanceState(outState);
   }

   @Override
   protected void onPause() {
      _mbwManager.stopWatchingAddress();
      _mbwManager.getEventBus().unregister(this);
      super.onPause();
   }

   @Override
   protected void onResume() {
      super.onResume();
      _mbwManager.getEventBus().register(this);
      if (_syncErrors < MAX_SYNC_ERRORS)
      _mbwManager.watchAddress(_address);
      updateUi();
   }

   BitcoinValue getBitcoinAmount() {
      if (CurrencyValue.isNullOrZero(_amount)) {
         return null;
      }

      if (!_amount.isBtc()) {
         // convert the amount to btc, but only once and stay within btc for all next calls
         _amount = ExchangeBasedBitcoinValue.fromValue(_amount, _mbwManager.getExchangeRateManager());
      }

      return (BitcoinValue) _amount;
   }

   private void updateUi() {
      final String qrText = getPaymentUri();

      if (CurrencyValue.isNullOrZero(_amount)) {
         tvTitle.setText(R.string.bitcoin_address_title);
         btShare.setText(R.string.share_bitcoin_address);
         tvAmountLabel.setText(R.string.optional_amount);
         tvAmount.setText("");
      } else {
         tvTitle.setText(R.string.payment_request);
         btShare.setText(R.string.share_payment_request);
         tvAmountLabel.setText(R.string.amount_title);
         tvAmount.setText(
               Utils.getFormattedValueWithUnit(getBitcoinAmount(), _mbwManager.getBitcoinDenomination())
         );
      }

      // QR code
      ivQrCode.setQrCode(qrText);

      // Show warning if the record has no private key
      if (_havePrivateKey) {
         tvWarning.setVisibility(View.GONE);
      } else {
         tvWarning.setVisibility(View.VISIBLE);
      }

      String[] addressStrings = Utils.stringChopper(getBitcoinAddress(), 12);
      tvAddress1.setText(addressStrings[0]);
      tvAddress2.setText(addressStrings[1]);
      tvAddress3.setText(addressStrings[2]);

      updateAmount();
   }

   private void updateAmount() {
      if (CurrencyValue.isNullOrZero(_amount)) {
         // No amount to show
         tvAmount.setText("");
      } else {
         // Set Amount
         tvAmount.setText(
               Utils.getFormattedValueWithUnit(getBitcoinAmount(), _mbwManager.getBitcoinDenomination())
         );
      }
   }

   private String getPaymentUri() {
      final StringBuilder uri = new StringBuilder("bitcoin:");
      uri.append(getBitcoinAddress());
      if (!CurrencyValue.isNullOrZero(_amount)) {
         uri.append("?amount=").append(CoinUtil.valueString(getBitcoinAmount().getLongValue(), false));
      }
      return uri.toString();
   }

   private String getBitcoinAddress() {
      return _address.toString();
   }

   public void shareRequest(View view) {
      Intent s = new Intent(android.content.Intent.ACTION_SEND);
      s.setType("text/plain");
      if (CurrencyValue.isNullOrZero(_amount)) {
         s.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(R.string.bitcoin_address_title));
         s.putExtra(Intent.EXTRA_TEXT, getBitcoinAddress());
         startActivity(Intent.createChooser(s, getResources().getString(R.string.share_bitcoin_address)));
      } else {
         s.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(R.string.payment_request));
         s.putExtra(Intent.EXTRA_TEXT, getPaymentUri());
         startActivity(Intent.createChooser(s, getResources().getString(R.string.share_payment_request)));
      }
   }

   public void copyToClipboard(View view) {
      String text;
      if (CurrencyValue.isNullOrZero(_amount)) {
         text = getBitcoinAddress();
      } else {
         text = getPaymentUri();
      }
      Utils.setClipboardString(text, this);
      Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
   }

   public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
      if (requestCode == GET_AMOUNT_RESULT_CODE && resultCode == RESULT_OK) {
         // Get result from address chooser (may be null)
         _amount = (CurrencyValue) intent.getSerializableExtra(GetAmountActivity.AMOUNT);
      } else {
         super.onActivityResult(requestCode, resultCode, intent);
      }
   }

   @OnClick(R.id.btEnterAmount)
   public void onEnterClick() {
      if (CurrencyValue.isNullOrZero(_amount)) {
         GetAmountActivity.callMe(ReceiveCoinsActivity.this, null, GET_AMOUNT_RESULT_CODE);
      } else {
         // call the amount activity with the exact amount, so that the user sees the same amount he had entered
         // it in non-BTC
         GetAmountActivity.callMe(ReceiveCoinsActivity.this, _amount.getExactValueIfPossible(), GET_AMOUNT_RESULT_CODE);
      }
   };

   @Subscribe
   public void syncError(SyncFailed event){
      _syncErrors++;
      // stop syncing after a certain amount of errors (no network available)
      if (_syncErrors > MAX_SYNC_ERRORS){
         _mbwManager.stopWatchingAddress();
      }
   }

   @Subscribe
   public void syncStopped(SyncStopped event) {
      TextView tvRecv = (TextView) findViewById(R.id.tvReceived);
      TextView tvRecvWarning = (TextView) findViewById(R.id.tvReceivedWarningAmount);
      final WalletAccount selectedAccount = _mbwManager.getSelectedAccount();
      final List<TransactionSummary> transactionsSince = selectedAccount.getTransactionsSince(_receivingSince);
      final ArrayList<TransactionSummary> interesting = new ArrayList<TransactionSummary>();
      CurrencyValue sum = ExactBitcoinValue.ZERO;
      for (TransactionSummary item : transactionsSince) {
         if (item.toAddresses.contains(_address)) {
            interesting.add(item);
            sum = item.value;
         }
      }

      if (interesting.size() > 0) {
         tvRecv.setText(getString(R.string.incoming_payment) + Utils.getFormattedValueWithUnit(sum, _mbwManager.getBitcoinDenomination()));
         // if the user specified an amount, also check it if it matches up...
         if (!CurrencyValue.isNullOrZero(_amount)) {
            tvRecvWarning.setVisibility ( sum.equals(_amount) ? View.GONE : View.VISIBLE);
         } else {
            tvRecvWarning.setVisibility ( View.GONE );
         }
         tvRecv.setVisibility(View.VISIBLE);
         if (!sum.equals(_lastAddressBalance)) {
            NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext())
                  .setSound(soundUri, AudioManager.STREAM_NOTIFICATION); //This sets the sound to play
            notificationManager.notify(0, mBuilder.build());

            _lastAddressBalance = sum;
         }
      } else {
         tvRecv.setVisibility(View.GONE);
      }
   }

}
