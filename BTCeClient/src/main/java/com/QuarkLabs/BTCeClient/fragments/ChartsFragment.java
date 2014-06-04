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
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import com.QuarkLabs.BTCeClient.CheckBoxListAdapter;
import com.QuarkLabs.BTCeClient.R;
import org.stockchart.StockChartView;
import org.stockchart.core.*;
import org.stockchart.indicators.*;
import org.stockchart.series.StockSeries;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChartsFragment extends Fragment {
    private Map<String, View> mCharts;
    private View mRootView;
    private LayoutInflater mInflater;
    private List<String> mCookies = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        getActivity().getActionBar().setTitle(getResources().getStringArray(R.array.NavSections)[5]);
        mInflater = inflater;
        if (mRootView == null) {
            mRootView = inflater.inflate(R.layout.fragment_chart, container, false);
            updateChartsSet(inflater);
        }
        return mRootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        updateCharts();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                updateCharts();
                break;
            case R.id.action_add:
                final CheckBoxListAdapter checkBoxListAdapter = new CheckBoxListAdapter(getActivity(),
                        getResources().getStringArray(R.array.ExchangePairs),
                        CheckBoxListAdapter.SettingsScope.CHARTS);
                ListView v = new ListView(getActivity());
                v.setAdapter(checkBoxListAdapter);
                new AlertDialog.Builder(getActivity())
                        .setTitle(getResources().getString(R.string.SelectPairsPromptTitle))
                        .setView(v)
                        .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                checkBoxListAdapter.saveValuesToPreferences();
                                updateChartsSet(mInflater);
                                updateCharts();
                            }
                        })
                        .show();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.actions_add_refresh, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * Starts fetching charts data via AsyncTasks
     */
    private void updateCharts() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getActivity()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        if (networkInfo == null || !networkInfo.isConnected()) {
            Toast.makeText(getActivity(), "No connection", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mCharts.size() > 0) {
            Toast.makeText(getActivity(), "Updating charts", Toast.LENGTH_SHORT).show();
            new PrepareToGetChartDataTask().execute();
        }
    }

    /**
     * Creates views for charts
     *
     * @param inflater
     */

    private void updateChartsSet(LayoutInflater inflater) {

        LinearLayout chartsContainer = (LinearLayout) mRootView.findViewById(R.id.ChartsContainer);
        chartsContainer.removeAllViews();
        mCharts = new HashMap<>();
        SharedPreferences sh = PreferenceManager.getDefaultSharedPreferences(getActivity());
        Set<String> hashSet = sh.getStringSet("ChartsToDisplay", new HashSet<String>());
        TextView noCharts = new TextView(getActivity());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        lp.gravity = Gravity.CENTER;
        noCharts.setLayoutParams(lp);
        noCharts.setGravity(Gravity.CENTER);
        noCharts.setText("NO CHARTS");
        noCharts.setTypeface(Typeface.DEFAULT_BOLD);
        //if no pairs to display found in prefs, display "NO CHARTS" text
        if (hashSet.size() == 0) {
            chartsContainer.addView(noCharts);
        }
        String[] pairs = hashSet.toArray(new String[hashSet.size()]);
        Arrays.sort(pairs);

        for (String x : pairs) {
            View element = inflater.inflate(R.layout.chart_item, chartsContainer, false);
            chartsContainer.addView(element);
            mCharts.put(x, element);
        }

        CompoundButton.OnCheckedChangeListener IndicatorListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ViewGroup viewGroup;
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    viewGroup = (ViewGroup) buttonView.getParent().getParent().getParent();
                } else {
                    viewGroup = (ViewGroup) buttonView.getParent();
                }
                StockChartView stockChartView = (StockChartView) viewGroup.findViewById(R.id.StockChartView);
                IndicatorManager iManager = stockChartView.getIndicatorManager();
                if (stockChartView.findSeriesByName("price") != null) {
                    if (isChecked) {
                        //if switch turned on and chart has data
                        switch (buttonView.getId()) {
                            case R.id.enableEMAIndicator:
                                iManager.addEma(stockChartView.findSeriesByName("price"), 0);
                                break;
                            case R.id.enableMACDIndicator:
                                iManager.addMacd(stockChartView.findSeriesByName("price"), 0);
                                break;
                            case R.id.enableRSIIndicator:
                                iManager.addRsi(stockChartView.findSeriesByName("price"), 0);
                                break;
                            case R.id.enableSMAIndicator:
                                iManager.addSma(stockChartView.findSeriesByName("price"), 0);
                                break;
                            default:
                                break;
                        }
                        stockChartView.recalcIndicators();
                        stockChartView.recalc();
                        stockChartView.invalidate();
                    } else {
                        //if switch turned off and chart has no data
                        List<AbstractIndicator> indicators = stockChartView.getIndicatorManager().getIndicators();
                        for (AbstractIndicator x : indicators) {
                            switch (buttonView.getId()) {
                                case R.id.enableEMAIndicator:
                                    if (x instanceof EmaIndicator) {
                                        stockChartView.getIndicatorManager().removeIndicator(x);
                                    }
                                    break;
                                case R.id.enableMACDIndicator:
                                    if (x instanceof MacdIndicator) {
                                        stockChartView.getIndicatorManager().removeIndicator(x);
                                    }
                                    break;
                                case R.id.enableRSIIndicator:
                                    if (x instanceof RsiIndicator) {
                                        stockChartView.getIndicatorManager().removeIndicator(x);
                                    }
                                    break;
                                case R.id.enableSMAIndicator:
                                    if (x instanceof SmaIndicator) {
                                        stockChartView.getIndicatorManager().removeIndicator(x);
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                        stockChartView.recalc();
                        stockChartView.invalidate();
                    }
                } else {
                    //if chart has no data, ignore switch
                    Switch sw = (Switch) buttonView;
                    sw.setChecked(!isChecked);
                }
            }
        };

        //add listeners to switches
        for (String x : mCharts.keySet()) {
            ((Switch) mCharts.get(x).findViewById(R.id.enableEMAIndicator))
                    .setOnCheckedChangeListener(IndicatorListener);
            ((Switch) mCharts.get(x).findViewById(R.id.enableRSIIndicator))
                    .setOnCheckedChangeListener(IndicatorListener);
            ((Switch) mCharts.get(x).findViewById(R.id.enableSMAIndicator))
                    .setOnCheckedChangeListener(IndicatorListener);
            ((Switch) mCharts.get(x).findViewById(R.id.enableMACDIndicator))
                    .setOnCheckedChangeListener(IndicatorListener);
        }
    }

    /**
     * Shows error, helper function
     */
    private void showError() {
        final String errorText = getResources().getString(R.string.GeneralErrorText);
        if (isVisible() && getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity(), errorText, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private class FetchChartDataTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            if (params.length == 0) {
                throw new IllegalArgumentException("Pair should be specified");
            }
            String pair = params[0].replace("/", "_").toLowerCase();
            getChartData(pair,
                    (StockChartView) mCharts.get(params[0]).findViewById(R.id.StockChartView));
            return null;
        }

        /**
         * Fetches data for a particular pair
         *
         * @param pair
         * @param chart
         */
        private void getChartData(String pair, StockChartView chart) {

            StringBuilder out = new StringBuilder();
            try {
                URL url = new URL("https://btc-e.com/exchange/" + pair);
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

                for (String cookie : mCookies) {
                    connection.addRequestProperty("Cookie", cookie);
                }
                connection.connect();
                if (connection.getResponseCode() != HttpsURLConnection.HTTP_OK) {
                    showError();
                    return;
                }
                BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                while ((line = rd.readLine()) != null) {
                    out.append(line);
                }
                rd.close();
                connection.disconnect();

            } catch (IOException e) {
                e.printStackTrace();
            }

            Pattern pattern = Pattern.compile("ToDataTable\\(\\[\\[(.+?)\\]\\], true");
            Matcher matcher = pattern.matcher(out.toString());
            if (matcher.find()) {
                //chart data
                String[] data = matcher.group(1).split("\\],\\[");
                if (isVisible() && getActivity() != null) {
                    populateChart(pair, data, chart);
                }
            } else {
                //if data not found, maybe cookie was outdated
                mCookies.clear();
                showError();
            }
        }

        /**
         * Populates particular chart with a fetched data
         *
         * @param pair
         * @param data
         * @param chartView
         */
        private void populateChart(String pair, final String[] data, final StockChartView chartView) {
            final StockSeries fPriceSeries = new StockSeries();
            fPriceSeries.setViewType(StockSeries.ViewType.CANDLESTICK);
            fPriceSeries.setName("price");
            chartView.reset();
            chartView.getCrosshair().setAuto(true);
            Area area = chartView.addArea();
            area.getLegend().getAppearance().getFont().setSize(16);
            area.setTitle(pair.replace("_", "/").toUpperCase());

            for (String x : data) {
                String[] values = x.split(", ");
                fPriceSeries.addPoint(Double.parseDouble(values[2]),
                        Double.parseDouble(values[4]),
                        Double.parseDouble(values[1]),
                        Double.parseDouble(values[3]));
            }
            area.getSeries().add(fPriceSeries);
            //provider for bottom axis (time)
            Axis.ILabelFormatProvider bottomLfp = new Axis.ILabelFormatProvider() {
                @Override
                public String getAxisLabel(Axis axis, double v) {
                    int index = fPriceSeries.convertToArrayIndex(v);
                    if (index < 0)
                        index = 0;
                    if (index >= 0) {
                        if (index >= fPriceSeries.getPointCount())
                            index = fPriceSeries.getPointCount() - 1;

                        return data[index].split(", ")[0].replace("\"", "");
                    }
                    return null;
                }
            };
            //provider for crosshair
            Crosshair.ILabelFormatProvider dp = new Crosshair.ILabelFormatProvider() {
                @Override
                public String getLabel(Crosshair crosshair, Plot plot, double v, double v2) {
                    int index = fPriceSeries.convertToArrayIndex(v);
                    if (index < 0)
                        index = 0;
                    if (index >= 0) {
                        if (index >= fPriceSeries.getPointCount())
                            index = fPriceSeries.getPointCount() - 1;

                        return data[index].split(", ")[0].replace("\"", "")
                                + " - "
                                + (new DecimalFormat("#.#####")
                                .format(v2));
                    }
                    return null;
                }
            };

            chartView.getCrosshair().setLabelFormatProvider(dp);
            //provider for right axis (value)
            Axis.ILabelFormatProvider rightLfp = new Axis.ILabelFormatProvider() {
                @Override
                public String getAxisLabel(Axis axis, double v) {
                    DecimalFormat decimalFormat = new DecimalFormat("#.#######");
                    return decimalFormat.format(v);
                }
            };
            area.getRightAxis().setLabelFormatProvider(rightLfp);
            area.getBottomAxis().setLabelFormatProvider(bottomLfp);

            //by some reason rise and fall appearance should be switched
            Appearance riseAppearance = fPriceSeries.getRiseAppearance();
            Appearance riseAppearance1 = new Appearance();
            riseAppearance1.fill(riseAppearance);
            Appearance fallAppearance = fPriceSeries.getFallAppearance();
            riseAppearance.fill(fallAppearance);
            fallAppearance.fill(riseAppearance1);

            //styling: setting fonts
            area.getPlot()
                    .getAppearance()
                    .getFont()
                    .setSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16,
                            getResources().getDisplayMetrics()));
            area.getLeftAxis()
                    .setSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5,
                            getResources().getDisplayMetrics()));
            area.getTopAxis()
                    .setSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5,
                            getResources().getDisplayMetrics()));
            area.getBottomAxis()
                    .setSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 15,
                            getResources().getDisplayMetrics()));
            area.getRightAxis()
                    .setSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40,
                            getResources().getDisplayMetrics()));
            area.getBottomAxis()
                    .getAppearance()
                    .getFont()
                    .setSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9,
                            getResources().getDisplayMetrics()));
            area.getRightAxis()
                    .getAppearance()
                    .getFont()
                    .setSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9,
                            getResources().getDisplayMetrics()));
            chartView.getCrosshair().getAppearance().getFont()
                    .setSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11,
                            getResources().getDisplayMetrics()));

            if (isVisible() && getActivity() != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        chartView.invalidate();
                    }
                });
            }
        }

    }

    private class PrepareToGetChartDataTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            if (mCookies.size() == 0) {
                try {
                    URL url = new URL("https://btc-e.com/");
                    HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

                    if (connection.getResponseCode() != HttpsURLConnection.HTTP_OK) {
                        showError();
                        return null;
                    }

                    mCookies.addAll(connection.getHeaderFields().get("Set-Cookie"));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    Pattern pattern = Pattern.compile("document.cookie=\"(a=(.+?));");
                    Matcher matcher = pattern.matcher(reader.readLine());

                    //if cookie not found
                    if (!matcher.find()) {
                        showError();
                        return null;
                    }
                    reader.close();
                    connection.disconnect();
                    mCookies.add(matcher.group(1));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Set<String> chartNames = mCharts.keySet();
            String[] chartsNamesSorted = chartNames.toArray(new String[chartNames.size()]);
            Arrays.sort(chartsNamesSorted);

            for (String x : chartsNamesSorted) {
                new FetchChartDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, x);
            }
        }
    }

}
