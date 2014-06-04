/*
 * BTC-e client
 *     Copyright (C) 2014  QuarkDev Solutions <quarkdev.solutions@gmail.com>
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.QuarkLabs.BTCeClient.fragments;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.NotificationManager;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.RingtoneManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import com.QuarkLabs.BTCeClient.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.*;

public class HomeFragment extends Fragment {

    private static int NOTIFICATIONS_COUNT = 1;
    private TableLayout mTickersContainer;
    private BroadcastReceiver mGetStatsReceiver;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        getActivity().getActionBar().setTitle(R.string.app_name);
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {

        mTickersContainer = (TableLayout) getView().findViewById(R.id.tickersContainer);
        populateTickersContainer(mTickersContainer);

        //Broadcast receiver initialization
        mGetStatsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (isVisible()) {
                    updateStats(mTickersContainer, MyActivity.tickersStorage.loadData());
                }
            }
        };

        LocalBroadcastManager.getInstance(getActivity().getApplicationContext())
                .registerReceiver(mGetStatsReceiver, new IntentFilter("UpdateTickers"));

        //Trade listener, once "Buy" or "Sell" clicked, send the order to server
        View.OnClickListener tradeListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new RegisterTradeRequestTask().execute((v.getId() == R.id.BuyButton) ? "buy" : "sell");
            }
        };

        Button SellButton = (Button) getView().findViewById(R.id.SellButton);
        Button BuyButton = (Button) getView().findViewById(R.id.BuyButton);
        SellButton.setOnClickListener(tradeListener);
        BuyButton.setOnClickListener(tradeListener);

        Button UpdateAccountInfoButton = (Button) getView().findViewById(R.id.UpdateAccountInfoButton);

        UpdateAccountInfoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new UpdateFundsTask().execute();
            }
        });

        //start service to get new data once Dashboard is opened
        getActivity().sendBroadcast(new Intent(getActivity(), StartServiceReceiver.class));
    }

    /**
     * Updates Dashboard with a fetched data
     *
     * @param tickersContainer
     * @param Tickers
     */
    private void updateStats(ViewGroup tickersContainer, Map<String, JSONObject> Tickers) {

        for (String pair : Tickers.keySet()) {
            View v = tickersContainer.findViewWithTag(pair);
            if (v != null) {
                String[] array = {"last", "sell", "buy"};
                for (String indicator : array) {
                    TextView field = (TextView) v.findViewWithTag(indicator);
                    Double newValue = Tickers.get(pair).optDouble(indicator);
                    Double oldValue = 0.0;
                    try {
                        oldValue = Double.parseDouble(String.valueOf(field.getText()));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                    if (newValue > oldValue) {
                        field.setTextColor(Color.GREEN);
                    } else {
                        field.setTextColor(Color.RED);
                    }
                    field.setText(formatDouble(newValue));
                    TextView Info2xFee = (TextView) v.findViewById(R.id.Info2xFee);
                    Info2xFee.setText(formatDouble(newValue * 0.004));
                }
            }
        }

    }

    /**
     * Formats decimal with a pattern "#.#######"
     *
     * @param value
     * @return
     */
    private String formatDouble(double value) {
        DecimalFormat decimalFormat = new DecimalFormat("#.#######");
        return decimalFormat.format(value);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.actions_menu_home, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add:
                final CheckBoxListAdapter checkBoxListAdapter = new CheckBoxListAdapter(getActivity(),
                        getResources().getStringArray(R.array.ExchangePairs),
                        CheckBoxListAdapter.SettingsScope.PAIRS);
                ListView listView = new ListView(getActivity());
                listView.setAdapter(checkBoxListAdapter);
                new AlertDialog.Builder(getActivity())
                        .setTitle(this.getString(R.string.SelectPairsPromptTitle))
                        .setView(listView)
                        .setNeutralButton(getResources().getString(R.string.DialogSave),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        checkBoxListAdapter.saveValuesToPreferences();
                                        populateTickersContainer(mTickersContainer);
                                        getActivity().sendBroadcast(new Intent(getActivity(), StartServiceReceiver.class));
                                    }
                                }
                        )
                        .show();
                break;
            case R.id.action_refresh:
                getActivity().sendBroadcast(new Intent(getActivity(), StartServiceReceiver.class));
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroyView() {
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mGetStatsReceiver);
        super.onDestroyView();
    }

    /**
     * Returns TableLayout with TableRows depending on the list of pairs to watch
     *
     * @param tickersContainer
     * @return
     */
    private void populateTickersContainer(TableLayout tickersContainer) {

        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        SharedPreferences sh = PreferenceManager.getDefaultSharedPreferences(getActivity());

        //listener for quick copy of price value to the Trade section
        View.OnClickListener fillPrice = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText TradePrice = (EditText) getView().findViewById(R.id.TradePrice);
                Spinner TradeCurrency = (Spinner) getView().findViewById(R.id.TradeCurrency);
                Spinner TradePriceCurrency = (Spinner) getView().findViewById(R.id.TradePriceCurrency);
                String pair = ((TextView) ((ViewGroup) v.getParent()).getChildAt(0)).getText().toString();
                ArrayAdapter arrayAdapter = (ArrayAdapter) TradeCurrency.getAdapter();
                int spinnerPosition = arrayAdapter.getPosition(pair.substring(0, 3));
                TradeCurrency.setSelection(spinnerPosition);
                spinnerPosition = arrayAdapter.getPosition(pair.substring(4));
                TradePriceCurrency.setSelection(spinnerPosition);
                TradePrice.setText(((TextView) v).getText());
            }
        };
        Set<String> pairsSet = sh.getStringSet("PairsToDisplay", new HashSet<String>());
        tickersContainer.removeViews(1, tickersContainer.getChildCount() - 1);
        if (pairsSet.size() == 0) {

            //if no pairs to watch, then add NoPairsToDisplay text
            TextView textView = new TextView(getActivity());
            textView.setGravity(Gravity.CENTER);
            textView.setText(getResources().getString(R.string.NoPairsToDisplay));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tickersContainer.addView(textView);
            TableRow.LayoutParams layoutParams = new TableRow
                    .LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            textView.setLayoutParams(layoutParams);
            textView.setTextColor(Color.WHITE);

        } else {

            String[] pairsArray = pairsSet.toArray(new String[pairsSet.size()]);
            Arrays.sort(pairsArray);
            for (String x : pairsArray) {
                TableRow infoRow = (TableRow) inflater.inflate(R.layout.ticker_item, tickersContainer, false);
                infoRow.setTag(x);
                TextView Last = (TextView) infoRow.findViewById(R.id.InfoLast);
                TextView Sell = (TextView) infoRow.findViewById(R.id.InfoSell);
                TextView Buy = (TextView) infoRow.findViewById(R.id.InfoBuy);
                Last.setTag("last");
                Sell.setTag("sell");
                Buy.setTag("buy");
                Last.setOnClickListener(fillPrice);
                Sell.setOnClickListener(fillPrice);
                Buy.setOnClickListener(fillPrice);
                TextView infoTitle = (TextView) infoRow.findViewById(R.id.CurrencyName);
                infoTitle.setText(x);
                tickersContainer.addView(infoRow);
            }
        }
    }

    /**
     * Refreshes funds table with fetched data
     *
     * @param response
     */
    private void refreshFunds(JSONObject response) {
        try {
            if (response == null) {
                Toast.makeText(getActivity(), getResources().getString(R.string.GeneralErrorText), Toast.LENGTH_LONG).show();
                return;
            }
            String notificationText;
            if (response.getInt("success") == 1) {

                View.OnClickListener fillAmount = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        EditText tradeAmount = (EditText) getView().findViewById(R.id.TradeAmount);
                        tradeAmount.setText(((TextView) v).getText());
                    }
                };

                notificationText = getResources().getString(R.string.FundsInfoUpdatedtext);
                TableLayout fundsContainer = (TableLayout) getView().findViewById(R.id.FundsContainer);
                fundsContainer.removeAllViews();
                JSONObject funds = response.getJSONObject("return").getJSONObject("funds");
                JSONArray fundsNames = response.getJSONObject("return").getJSONObject("funds").names();
                List<String> arrayList = new ArrayList<>();

                for (int i = 0; i < fundsNames.length(); i++) {
                    arrayList.add(fundsNames.getString(i));
                }
                Collections.sort(arrayList);
                TableRow.LayoutParams layoutParams = new TableRow
                        .LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);

                for (String anArrayList : arrayList) {

                    TableRow row = new TableRow(getActivity());
                    TextView currency = new TextView(getActivity());
                    TextView amount = new TextView(getActivity());
                    currency.setText(anArrayList.toUpperCase());
                    amount.setText(funds.getString(anArrayList));
                    currency.setLayoutParams(layoutParams);
                    currency.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                    currency.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    currency.setGravity(Gravity.CENTER);
                    amount.setLayoutParams(layoutParams);
                    amount.setGravity(Gravity.CENTER);
                    amount.setOnClickListener(fillAmount);
                    row.addView(currency);
                    row.addView(amount);
                    fundsContainer.addView(row);
                }

            } else {
                notificationText = response.getString("error");
            }

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getActivity())
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(getResources().getString(R.string.app_name))
                    .setContentText(notificationText);

            mBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            NotificationManager mNotificationManager =
                    (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(ConstantHolder.ACCOUNT_INFO_NOTIF_ID, mBuilder.build());

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private class RegisterTradeRequestTask extends AsyncTask<String, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(String... params) {
            String tradeAmount = ((EditText) getView().findViewById(R.id.TradeAmount))
                    .getText().toString();
            String tradeCurrency = ((Spinner) getView().findViewById(R.id.TradeCurrency))
                    .getSelectedItem().toString();
            String tradePrice = ((EditText) getView().findViewById(R.id.TradePrice))
                    .getText().toString();
            String tradePriceCurrency = ((Spinner) getView().findViewById(R.id.TradePriceCurrency))
                    .getSelectedItem().toString();
            String tradeAction = params[0];
            String pair = tradeCurrency.toLowerCase() + "_" + tradePriceCurrency.toLowerCase();
            JSONObject response = null;
            try {
                response = MyActivity.app.trade(pair, tradeAction, tradePrice, tradeAmount);
            } catch (UnsupportedEncodingException | JSONException e) {
                e.printStackTrace();
            }
            return response;
        }

        @Override
        protected void onPostExecute(JSONObject jsonObject) {
            if (jsonObject != null) {
                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getActivity())
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle(getResources().getString(R.string.app_name))
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                NotificationManager mNotificationManager =
                        (NotificationManager) getActivity()
                                .getSystemService(Context.NOTIFICATION_SERVICE);
                if (jsonObject.optInt("success") == 1) {
                    mBuilder.setContentText("Order was successfully added");
                    mNotificationManager.notify(ConstantHolder.TRADE_REGISTERED_NOTIF_ID,
                            mBuilder.build());
                    refreshFunds(jsonObject);
                } else {
                    mBuilder.setContentText(jsonObject.optString("error"));
                    mNotificationManager.notify(ConstantHolder.TRADE_REGISTERED_NOTIF_ID,
                            mBuilder.build());
                }
            } else {
                Toast.makeText(getActivity(), getResources()
                        .getString(R.string.GeneralErrorText), Toast.LENGTH_LONG).show();
            }
        }
    }

    private class UpdateFundsTask extends AsyncTask<Void, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(Void... params) {
            JSONObject response = null;
            try {
                response = MyActivity.app.getAccountInfo();
            } catch (UnsupportedEncodingException | JSONException e) {
                e.printStackTrace();
            }
            return response;
        }

        @Override
        protected void onPostExecute(JSONObject jsonObject) {
            refreshFunds(jsonObject);
        }
    }
}

