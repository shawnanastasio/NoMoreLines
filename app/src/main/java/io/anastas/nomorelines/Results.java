package io.anastas.nomorelines;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.google.android.gms.location.places.GeoDataClient;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.PlaceDetectionClient;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

class PopularTimeUtil {
    private static String[] intToDay = {"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    public static int getCurrentPopularity(PopularTimes popularTimes) {
        Calendar c = Calendar.getInstance();
        int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
        int hourOfDay = c.get(Calendar.HOUR_OF_DAY);
        List<Integer> times = null;
        for (PopularTime p : popularTimes.populartimes) {
            if (p.name.equals(intToDay[dayOfWeek])) {
                times = p.data;
            }
        }
        if (times == null) {
            Log.d("getCurrentPopularity", "couldn't look up day?? " + dayOfWeek);
            return 0;
        }

        return times.get(hourOfDay);
    }
}

class PlaceComparator implements Comparator<Place> {
    private int[] pop;
    public PlaceComparator(int[] popularities) {
        pop = popularities;
    }

    public int compare(Place p1, Place p2) {
        //return pop.get(p1) - pop.get(p2);
        //return pi
        return 0;
    }
}

class PlaceWrapper implements Comparable<PlaceWrapper> {
    public Place p;
    public int pop;

    public PlaceWrapper(Place p, int pop) {
        this.p = p;
        this.pop = pop;
    }

    public int compareTo(PlaceWrapper other) {
        return this.pop - other.pop;
    }
}


class ResultFetcher extends AsyncTask<String, String, String> {
    protected GooglePlaces googlePlaces;
    protected ListView view;
    protected GPSTracker gps;
    protected double radius;
    protected String types;
    protected AppCompatActivity activity;

    protected PlacesList result;
    protected PopularTimes[] popularTimes;
    public ResultFetcher(AppCompatActivity activity, ListView view, GPSTracker gps, double radius, String types) {
        this.activity = activity;
        googlePlaces = new GooglePlaces();
        this.view = view;
        this.gps = gps;
        this.radius = radius;
        this.types = types;
        this.result = null;
    }

    protected String doInBackground(String... params) {
        Log.d("result fetcher", "hello world");
        try {
            result = googlePlaces.search(gps.getLatitude(), gps.getLongitude(), radius, types);
            Log.d("ResultFetcher", "Got status: " + result.status);
            if (result.results == null) {
                return "no";
            }
            this.popularTimes = new PopularTimes[result.results.size()];
            int i = 0;
            for (Place p : result.results) {
                popularTimes[i++] = googlePlaces.getPlacePopularTimes(p.place_id);
            }

        } catch (Exception e) {
            Log.d("ResultFetcher", e.toString());
        }
        return "no";
    }

    protected void onPostExecute(String fileUrl) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                int listEntriesSize = 0;
                PlaceWrapper[] results = new PlaceWrapper[result.results.size()];
                for (Place p : result.results) {
                    Log.d("ResultFetcher", "Place: " + p.name + "id: " + p.place_id);
                    if (popularTimes[listEntriesSize].populartimes.size() != 0) {
                        int pop = (PopularTimeUtil.getCurrentPopularity(popularTimes[listEntriesSize]));
                        Log.d("ResultFetcher_pop", "Got cur pop: "
                                + pop);

                        results[listEntriesSize] = new PlaceWrapper(p, pop);
                    } else {
                        results[listEntriesSize] = new PlaceWrapper(p, 0);
                    }

                    ++listEntriesSize;
                }

                // Sort by pop
                Arrays.sort(results);

                // Generate strings
                String[] strings = new String[results.length];
                int i=0;
                for (PlaceWrapper p : results) {
                    strings[i] = p.p.name + "\nBusyness: " + p.pop + "%";
                    ++i;
                }

                // Render array into list view
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, strings);
                view.setClickable(true);
                view.setAdapter(adapter);
            }
        });
    }
}

public class Results extends AppCompatActivity {
    protected GeoDataClient mGeoDataClient;
    protected PlaceDetectionClient mPlaceDetectionClient;

    // GUI
    protected ArrayAdapter listAdapter;
    protected ListView listView;
    protected String[] listContents;
    protected int listSize;

    // Text input
    protected TextInputEditText searchBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Fix keyboard
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        // Add Places API stuff
        mGeoDataClient = Places.getGeoDataClient(this, null);
        mPlaceDetectionClient = Places.getPlaceDetectionClient(this, null);

        // Instantiate listView
        listView = findViewById(R.id.result_list);
        listView.setClickable(false);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {

                String text = (String)listView.getItemAtPosition(position);
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + text.split("\n")[0]));
                startActivity(browserIntent);
            }
        });

        // Display loading
        String[] strings = {"Loading..."};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, strings);
        listView.setAdapter(adapter);

        GPSTracker gps = new GPSTracker(this);
        // check if GPS location can get
        if (gps.canGetLocation()) {
            Log.d("Your Location", "latitude:" + gps.getLatitude() + ", longitude: " + gps.getLongitude());
        } else {
            // Can't get user's current location
            Log.d("Your location", "Couldn't get location!");
        }

        // Fetch results
        new ResultFetcher(this, listView, gps, 1000, "restaurant").execute();
    }

    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_results, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
