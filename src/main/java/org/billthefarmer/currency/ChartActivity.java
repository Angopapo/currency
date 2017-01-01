////////////////////////////////////////////////////////////////////////////////
//
//  Currency - An android currency converter.
//
//  Copyright (C) 2016	Bill Farmer
//
//  This program is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
//  Bill Farmer	 william j farmer [at] yahoo [dot] co [dot] uk.
//
///////////////////////////////////////////////////////////////////////////////

package org.billthefarmer.currency;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.DefaultFillFormatter;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;

import java.text.NumberFormat;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// ChartActivity class

public class ChartActivity extends Activity
{
    public static final String TAG = "Chart";
    public static final String DATA_TAG = "data";

    public static final String ECB_QUARTER_URL =
	"http://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist-90d.xml";
    public static final String ECB_HIST_URL =
	"http://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.xml";

    public static final long MSEC_DAY = 1000 * 60 * 60 * 24;

    private DataFragment dataFragment;
    private TextView customView;
    private LineChart chart;

    private Map<String, Map<String,Double>> histMap;

    private List<Entry> entryList;
    private LineDataSet dataSet;
    private LineData lineData;

    private boolean wifi = true;
    private boolean roaming = false;

    private int first;
    private int second;

    private String firstName;
    private String secondName;

    // On create

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chart);

        // Find the retained fragment on activity restarts
        FragmentManager fm = getFragmentManager();
        dataFragment = (DataFragment) fm.findFragmentByTag(DATA_TAG);

        // Create the fragment the first time
        if (dataFragment == null)
	{
            // add the fragment
            dataFragment = new DataFragment();
            fm.beginTransaction().add(dataFragment, DATA_TAG).commit();
        }

	// Enable back navigation on action bar
	ActionBar actionBar = getActionBar();
	if (actionBar != null)
	{
	    actionBar.setDisplayHomeAsUpEnabled(true);

	    // Set custom view
	    actionBar.setCustomView(R.layout.text);
	    actionBar.setDisplayShowCustomEnabled(true);

	    // Get custom view
	    customView = (TextView)actionBar.getCustomView();
	}

	// Get chart
	chart = (LineChart)findViewById(R.id.chart);

	// Get text and colour
	Resources resources = getResources();
	String updating = resources.getString(R.string.updating);
	int dark = resources.getColor(android.R.color.secondary_text_dark);

	// Set chart parameters
	if (chart != null)
	{
	    // Set the no data text and colour, only seen once
	    chart.setNoDataText(updating);
	    chart.setNoDataTextColor(dark);

	    // Set auto scaling
	    chart.setAutoScaleMinMaxEnabled(true);
	    chart.setKeepPositionOnRotation(true);

	    // Set date formatter for x axis and colour
	    XAxis xAxis = chart.getXAxis();
	    xAxis.setValueFormatter(new DateAxisValueFormatter());
	    xAxis.setGranularity(1f);
	    xAxis.setTextColor(dark);

	    // Set y axis colour
	    YAxis leftAxis = chart.getAxisLeft();
	    leftAxis.setTextColor(dark);

	    YAxis rightAxis = chart.getAxisRight();
	    // Set y axis colour
	    rightAxis.setTextColor(dark);

	    // No legend - only one dataset
	    Legend legend = chart.getLegend();
	    legend.setEnabled(false);

	    // No desctription
	    Description description = chart.getDescription();
	    description.setEnabled(false);
	}

	// Get the intent for the parameters
	Intent intent = getIntent();
	first = intent.getIntExtra(Main.CHART_FIRST, 0);
	second = intent.getIntExtra(Main.CHART_SECOND, 0);

	// Look up the names
	firstName = Main.CURRENCY_NAMES[first];
	secondName = Main.CURRENCY_NAMES[second];

	// Generate the label
	String label = secondName + "/" + firstName;
	if (customView != null)
	    customView.setText(label);
    }

    // On resume

    @Override
    protected void onResume()
    {
	super.onResume();

	// Get preferences
	SharedPreferences preferences =
 	    PreferenceManager.getDefaultSharedPreferences(this);

	wifi = preferences.getBoolean(Main.PREF_WIFI, true);
	roaming = preferences.getBoolean(Main.PREF_ROAMING, false);

	// Get data fragment
	if (dataFragment != null)
	    histMap = dataFragment.getData();

	// Check retained data
	if (histMap != null)
	{
	    SimpleDateFormat dateParser =
		new SimpleDateFormat(Main.DATE_FORMAT, Locale.getDefault());
	    Resources resources = getResources();

	    // Create the entry list
	    entryList = new ArrayList<Entry>();

	    // Iterate through the dates
	    for (String key: histMap.keySet())
	    {
		float day = 0;

		// Parse the date and turn it into a day number
		try
		{
		    Date date = dateParser.parse(key);
		    day = date.getTime() / MSEC_DAY;
		}

		// Ignore invalid dates
		catch (Exception e)
		{
		    continue;
		}

		// Get the map for each date
		Map<String, Double> entryMap = histMap.get(key);
		float value = 1;

		// Get the value for each date
		try
		{
		    double first = entryMap.get(firstName);
		    double second = entryMap.get(secondName);
		    value = (float)(first / second);
		}

		// Ignore missing values
		catch (Exception e)
		{
		    continue;
		}

		// Add the entry to the list
		entryList.add(0, new Entry(day, value));
	    }

	    // Get the colour
	    int bright = resources.getColor(android.R.color.holo_blue_bright);

	    // Check the chart
	    if (chart != null)
	    {
		// Create the dataset
		dataSet = new LineDataSet(entryList, secondName);

		// Set dataset parameters and colour
		dataSet.setDrawCircles(false);
		dataSet.setDrawValues(false);
		dataSet.setColor(bright);

		// Add the data to the chart and refresh
		lineData = new LineData(dataSet);
		chart.setData(lineData);
		chart.invalidate();
	    }

	    // Don't do an online update
	    return;
	}

	// Check connectivity before update
	ConnectivityManager manager =
	    (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
	NetworkInfo info = manager.getActiveNetworkInfo();

	// Check connection
	if (info == null || !info.isConnected())
	    return;

	// Check wifi
	if (wifi && info.getType() != ConnectivityManager.TYPE_WIFI)
	    return;

	// Check roaming
	if (!roaming && info.isRoaming())
	    return;

	// Schedule the update
	ParseTask parseTask = new ParseTask(this);
	parseTask.execute(ECB_QUARTER_URL);
    }

    // On pause

    @Override
    protected void onPause()
    {
	super.onPause();

        // Store the historical data in the fragment
	if (dataFragment != null)
	    dataFragment.setData(histMap);
    }

    // On create options menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
	// Inflate the menu; this adds items to the action bar if it
	// is present.

	MenuInflater inflater = getMenuInflater();
	inflater.inflate(R.menu.chart, menu);

	return true;
    }

    // On options item selected

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
	// Get id

	int id = item.getItemId();
	switch (id)
	{
	    // Home
	case android.R.id.home:
	    finish();
	    break;

	    // Invert chart
	case R.id.action_invert:
	    return onInvertClick();

	    // Refresh chart
	case R.id.action_refresh:
	    return onRefreshClick(ECB_QUARTER_URL);

	    // Refresh with historical data
	case R.id.action_hist:
	    return onRefreshClick(ECB_HIST_URL);

	default:
	    return false;
	}

	return true;
    }

    // on invert click
    private boolean onInvertClick()
    {
	SimpleDateFormat dateParser =
	    new SimpleDateFormat(Main.DATE_FORMAT, Locale.getDefault());

	// Get updating text
	Resources resources = getResources();
	String updating = resources.getString(R.string.updating);

	// Reverse currency indeces
	int index = first;
	first = second;
	second = index;

	// And names
	firstName = Main.CURRENCY_NAMES[first];
	secondName = Main.CURRENCY_NAMES[second];

	// Set custom text to updating, since this may take a few secs
	if (customView != null)
	    customView.setText(updating);

	// Clear the entry list
	entryList.clear();

	// Iterate through the dates
	for (String key: histMap.keySet())
	{
	    float day = 0;

	    // Parse the date and turn it into a day number
	    try
	    {
		Date date = dateParser.parse(key);
		day = date.getTime() / MSEC_DAY;
	    }

	    catch (Exception e) {}

	    // Get the map for each date
	    Map<String, Double> entryMap = histMap.get(key);
	    float value = 1;

	    // Get the value for each date
	    try
	    {
		double first = entryMap.get(firstName);
		double second = entryMap.get(secondName);
		value = (float)(first / second);
	    }

	    // Ignore missing values
	    catch (Exception e)
	    {
		continue;
	    }

	    // Add the entry to the list
	    entryList.add(0, new Entry(day, value));
	}

	// Check the chart
	if (chart != null)
	{
	    // Add the data to the chart and refresh
	    dataSet.setValues(entryList);
	    lineData.notifyDataChanged();
	    chart.notifyDataSetChanged();
	    chart.invalidate();
	}

	// Restore the custom view to the current currencies
	String label = secondName + "/" + firstName;
	if (customView != null)
	    customView.setText(label);

	return true;
    }

    // on refresh click
    private boolean onRefreshClick(String url)
    {
	// Check connectivity before update
	ConnectivityManager manager =
	    (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
	NetworkInfo info = manager.getActiveNetworkInfo();

	// Check connection
	if (info == null || !info.isConnected())
	    return false;

	// Check wifi
	if (wifi && info.getType() != ConnectivityManager.TYPE_WIFI)
	    return false;

	// Check roaming
	if (!roaming && info.isRoaming())
	    return false;

	// Get updating text
	Resources resources = getResources();
	String updating = resources.getString(R.string.updating);

	// Set custom text to updating, since this may take a few secs
	if (customView != null)
	    customView.setText(updating);

	// Schedule the update
	ParseTask parseTask = new ParseTask(this);
	parseTask.execute(url);

	return true;
    }

    // ParseTask class

    private class ParseTask
	extends AsyncTask<String, String, Map<String, Map<String, Double>>>
    {
	Context context;

	// Constructor
	private ParseTask(Context context)
	{
	    this.context = context;
	}

	// The system calls this to perform work in a worker thread
	// and delivers it the parameters given to AsyncTask.execute()
	@Override
	protected Map doInBackground(String... urls)
	{
	    // Get a parser
	    ChartParser parser = new ChartParser();

	    // Start the parser and report progress with the date
	    if (parser.startParser(urls[0]) == true)
		publishProgress(parser.getDate());

	    // Return the map
	    return parser.getMap();
	}

	// Ignoring the date as not used
	@Override
	protected void onProgressUpdate(String... date) {}

	// The system calls this to perform work in the UI thread and
	// delivers the result from doInBackground()
	@Override
	protected void onPostExecute(Map<String, Map<String,Double>> map)
	{
	    // Check map
	    if (map == null)
		return;

	    // Save map
	    histMap = map;

	    SimpleDateFormat dateParser =
		new SimpleDateFormat(Main.DATE_FORMAT, Locale.getDefault());
	    Resources resources = context.getResources();

	    // Create a new entry list
	    entryList = new ArrayList<Entry>();

	    // Iterate through the dates
	    for (String key: map.keySet())
	    {
		float day = 0;

		// Parse the date and turn it into a day number
		try
		{
		    Date date = dateParser.parse(key);
		    day = date.getTime() / MSEC_DAY;
		}

		// Ignore invalid dates
		catch (Exception e)
		{
		    continue;
		}

		// Get the map for each date
		Map<String, Double> entryMap = map.get(key);
		float value = 1;

		// Get the value for each date
		try
		{
		    double first = entryMap.get(firstName);
		    double second = entryMap.get(secondName);
		    value = (float)(first / second);
		}

		// Ignore missing values
		catch (Exception e)
		{
		    continue;
		}

		// Add the entry to the list
		entryList.add(0, new Entry(day, value));
	    }

	    // Get the colour
	    int bright = resources.getColor(android.R.color.holo_blue_bright);

	    // Check the chart
	    if (chart != null)
	    {
		// Create the dataset
		dataSet = new LineDataSet(entryList, secondName);

		// Set dataset parameters and colour
		dataSet.setDrawCircles(false);
		dataSet.setDrawValues(false);
		dataSet.setColor(bright);

		lineData = new LineData(dataSet);
		chart.setData(lineData);
		chart.invalidate();
	    }

	    // Restore the custom view to the current currencies
	    String label = secondName + "/" + firstName;
	    if(customView != null)
		customView.setText(label);
	}
    }

    // DateAxisValueFormatter class

    private class DateAxisValueFormatter implements IAxisValueFormatter
    {
	DateFormat dateFormat;

	// Constructor
	private DateAxisValueFormatter()
	{
	    dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
	}

	// Get formatted value
	@Override
	public String getFormattedValue(float value, AxisBase axis)
	{
	    // "value" represents the position of the label on the
	    // axis (x or y). Create a date from the day number and
	    // format it.
	    Date date = new Date((int)value * MSEC_DAY);
	    return dateFormat.format(date);
	}
    }
}
