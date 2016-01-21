package com.example.austin.demoapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.view.ViewGroup;
import android.view.LayoutInflater;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class DemoActivity extends AppCompatActivity {

    private static final String FILE_NAME = "settings";
    private static final String SEX_KEY = "settings_sex";
    private static final String WEIGHT_KEY = "settings_weight";

    private int drinksConsumed = 0;
    private boolean userSex;
    private int userWeight;
    private double BAC = 0;

    // For time calculations
    private ArrayList<DateTime> drinkTimes = new ArrayList<>();
    private LineGraphSeries<DataPoint> series;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);
        // Toolbar setup
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Drink increase button
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        TextView drinkCount = (TextView) findViewById(R.id.DrinkCount);

        drinkCount.setText("Drink Count: 0");
        drinkCount.setTextColor(Color.rgb(0, 150, 255));

        GraphView graph = (GraphView) findViewById(R.id.graph);
        graph.setVisibility(View.INVISIBLE);

        final Handler handler = new Handler();
        Timer timer = new Timer();
        TimerTask update = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        if (drinksConsumed != 0) {
                            drinkDisplay();
                            BACDisplay();
                            changeColor();
                            timeDisplay(true);
                            try {
                                graphDisplay();
                            }//try
                            catch(Exception e) { System.out.println("Graph not initialized"); }
                        }//if drinksConsumed
                    }//run
                });//post
            }//run
        };//TimerTask
        timer.schedule(update, 0, 60000);

        load(getApplicationContext());

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            // Clicking FAB
            public void onClick(View view) {    addDrink(view); }//onClick

        });//fab.setOnClickListener
    }//onCreate


    @Override
    // Volume up button adds a drink
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP)){
            addDrink(findViewById(android.R.id.content));
        }//if keyCode
        return true;
    }//onKeyDown

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_demo, menu);
        return true;
    }//onCreateOptionsMenu

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id)
        {
            case R.id.action_settings: // Settings option - setup of sex,weight
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                settingsDialog sD_fragment = new settingsDialog();
                sD_fragment.show(ft, "txn_tag");
                return true;

            case R.id.reset: // Reset option - restarts activity
                finish();
                startActivity(getIntent());

                return true;
        }//switch

        return super.onOptionsItemSelected(item);

    }//onOptionsItemsSelected


    // Changes variables for calculation and displays text/snackbar
    public void addDrink(View view){

        drinkTimes.add(new DateTime());
        drinksConsumed = drinkTimes.size();

        drinkDisplay();
        BACDisplay();
        changeColor();
        timeDisplay(false);

        if(drinksConsumed == 1) {
            graphSetup();
        }//if
        else {  graphDisplay(); }

        // Snackbar display for action
        Snackbar snackbar = Snackbar.make(view, "1 drink consumed", Snackbar.LENGTH_LONG)
                .setAction("UNDO", new View.OnClickListener() {
                    @Override
                    // Snackbar undo click
                    public void onClick(View view) {
                        if (drinksConsumed != 0) {
                            drinksConsumed--;
                            drinkTimes.remove(drinksConsumed);
                        }//if drinksConsumed

                        // Re-display
                        drinkDisplay();
                        BACDisplay();
                        graphDisplay();
                        changeColor();
                        timeDisplay(true);

                    }//onClick
                });

        snackbar.setActionTextColor(Color.WHITE);
        ViewGroup group = (ViewGroup) snackbar.getView();
        group.setBackgroundColor(BAC_color());
        snackbar.show();

    }//addDrink


    // Calculation Methods


    // Calculates BAC based on drinks consumed, sex, weight, and time (min) since start
    public double BAC_calc(int drinks, boolean sex, int weight, double time)
    {
        double sexRatio = sex ? 0.58 : 0.49; // sex ? male : female
        return drinks != 0 ? (drinks * 0.967 / (weight * 0.454 * sexRatio)) - 0.017 / 60 * time : 0.0; //BAC Formula

    }//BAC_calc

    // Returns difference in minutes
    public int timeDiff(DateTime date_1, DateTime date_2)
    {
        long milli_diff = date_2.getMillis() - date_1.getMillis();
        return (int) milli_diff / 1000 / 60;
    }//timeDiff

    // Calculates time between initial Date and input Date
    public int elapsedTime(DateTime date)
    {
        return drinksConsumed != 0 ? timeDiff(drinkTimes.get(0),date) : 0;
    }//elapsedTime

    //Calculates time between previous Date and input Date
    public int prevTime(DateTime date, boolean isUpdate)
    {
        try {
            return timeDiff(drinkTimes.get(drinksConsumed-2 + (isUpdate ? 1 : 0)),date);
        }//try
        catch (IndexOutOfBoundsException e) {
            return elapsedTime(date);
        }//catch
    }//prevTime

    // Converts int minutes to a string formatted as time
    public String minToDisplay(int minutes)
    {
        String hrText = "";
        String minText = "";

        int hours = minutes / 60;
        minutes = minutes % 60;

        if (hours != 0) {
            hrText = String.valueOf(hours) + " hr ";
        }//if hours

        if (hours == 0 || minutes != 0) {
            minText = String.valueOf(minutes) + " min";
        }//if hours

        return hrText + minText;

    }//minToDisplay


    // Display Methods


    public void drinkDisplay() {

        TextView drinkCount = (TextView) findViewById(R.id.DrinkCount);
        String drinkText = "Drink Count: " + String.valueOf(drinksConsumed);
        drinkCount.setText(drinkText);

    }//drinkDisplay

    public void BACDisplay() {

        TextView BAC_view = (TextView) findViewById(R.id.BAC);
        if (userWeight != 0) {
            BAC = BAC_calc(drinksConsumed,userSex, userWeight, elapsedTime(new DateTime()));
            String BAC_text = "BAC: " + String.format("%1.2g%n", BAC);
            BAC_view.setText(BAC_text);
        }//if userWeight
        else {  BAC_view.setText("Input settings for BAC calculation");  }

    }//BACDisplay

    public void timeDisplay(boolean isUpdate) {

        TextView timeFromStart = (TextView) findViewById(R.id.time_start);
        TextView timeFromLast = (TextView) findViewById(R.id.time_last);

        String time_start = "Time since starting: " + minToDisplay(elapsedTime(new DateTime()));
        timeFromStart.setText(time_start);


        String time_last = "Time since last drink: " + minToDisplay(prevTime(new DateTime(), isUpdate));
        timeFromLast.setText(time_last);

    }//timeDisplay

    public void graphDisplay() {

        GraphView graph = (GraphView) findViewById(R.id.graph);
        double now = new DateTime().getSecondOfDay() + new DateTime().getDayOfYear()*86400;
        DataPoint data = new DataPoint(now,BAC);

        graph.getViewport().setMaxX(now);
        graph.getViewport().setMaxY(BAC);

        series.appendData(data, true, 720);

        graph.getGridLabelRenderer().setVerticalLabelsColor(BAC_color());

    }//graphDisplay

    public void graphSetup() {
        GraphView graph = (GraphView) findViewById(R.id.graph);
        GridLabelRenderer label = graph.getGridLabelRenderer();
        Viewport view = graph.getViewport();
        label.setLabelFormatter(new DefaultLabelFormatter() {

            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {

                    int hour = (((int) value) % 86400) / 3600;
                    if (hour == 0) {
                        hour = 12;
                    }

                    int min = ((((int) value) % 86400) % 3600) / 60;

                    return String.valueOf(hour) + String.format(":%02d", min);
                }//if isValueX
                else {
                    return String.format("%1.2f%n", value);
                }//else
            }//formatLabel
        });//graph

        double start = (double) drinkTimes.get(0).getSecondOfDay() + drinkTimes.get(0).getDayOfYear()*86400;

        label.setGridColor(Color.WHITE);
        label.setHorizontalLabelsColor(Color.WHITE);
        label.setVerticalLabelsColor(BAC_color());
        view.setXAxisBoundsManual(true);
        view.setYAxisBoundsManual(true);
        view.setMinX(start);
        view.setMaxX(start + 240);
        view.setMinY(0d);
        view.setMaxY(0.05d);

        DataPoint[] data = new DataPoint[1];
        data[0] = new DataPoint(start,BAC);
        try {
            series.resetData(data);
        }//try
        catch (NullPointerException e) {
            series = new LineGraphSeries<>(data);

            graph.addSeries(series);
        }//catch

        graph.setVisibility(View.VISIBLE);

    }//graphSetup


    // Color Methods


    // Changes the colors of the drink/BAC text, floating action button, and toolbar
    public void changeColor() {

        TextView drinkCount = (TextView) findViewById(R.id.DrinkCount);
        TextView BAC_view = (TextView) findViewById(R.id.BAC);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        int color = BAC_color();

        drinkCount.setTextColor(color);
        BAC_view.setTextColor(color);
        fab.setBackgroundTintList(ColorStateList.valueOf(color));
        toolbar.setBackgroundTintList(ColorStateList.valueOf(color));

    }//changeColor

    // Returns different colors based on BAC
    public int BAC_color()
    {
        int change;
        if (BAC > 0.18) {
            change = Color.rgb(220,0,0);
        }//red
        else if (BAC > 0.08) {
            change = Color.rgb(240,110,0);
        }//orange
        else if (BAC > 0) {
            change = Color.rgb(0,200,0);
        }//green
        else {
            change = Color.rgb(0,150,255);
        }//blue

        return change;
    }//BAC_color


    // Load/save methods


    public void load(Context context)
    {
        SharedPreferences settings;
        settings = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        userSex = settings.getBoolean(SEX_KEY, false);
        userWeight = settings.getInt(WEIGHT_KEY, 0);
    }//load

    public void save(Context context, Boolean isMale, int weight)
    {
        SharedPreferences settings;
        SharedPreferences.Editor editor;
        settings = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        editor = settings.edit();
        editor.clear();

        editor.putBoolean(SEX_KEY, isMale);
        editor.putInt(WEIGHT_KEY, weight);
        editor.apply();
    }//save




    // Opens on menu option selection
    public static class settingsDialog extends DialogFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setStyle(DialogFragment.STYLE_NORMAL, R.style.settings_dialog);
        }//onCreate

        @Override
        public void onStart() {
            super.onStart();
            Dialog d = getDialog();
            if (d!=null){
                int width = ViewGroup.LayoutParams.MATCH_PARENT;
                int height = ViewGroup.LayoutParams.MATCH_PARENT;
                d.getWindow().setLayout(width, height);
            }//if
        }//onStart


        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

            return inflater.inflate(R.layout.settings, container, false);

        }//onCreateView


    }//settingsDialog

    // Event handler for submit button on settingsDialog
    public void submit(View view) {
        EditText weight_input = (EditText) view.getRootView().findViewById(R.id.weight);
        try {
            userWeight = Integer.parseInt(weight_input.getText().toString());
            save(getApplicationContext(),userSex,userWeight);
        }//try
        catch(NumberFormatException e) { System.out.println("No input"); }// Do nothing
        finish();
        startActivity(getIntent());
    }//submit

    // Event handler for radio buttons on settingsDialog:
    // Returns true for male selection and false for female selection
    public void sexSelected(View view)
    {
        userSex = view.getId() == R.id.male;
    }//sexSelected

}//DemoActivity
