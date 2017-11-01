package com.huyvo.cmpe277.sjsu.weatherapp;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.support.design.widget.TabLayout;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;

import com.huyvo.cmpe277.sjsu.weatherapp.activities.BaseActivityWithFragment;
import com.huyvo.cmpe277.sjsu.weatherapp.activities.CityListViewActivity;
import com.huyvo.cmpe277.sjsu.weatherapp.activities.SettingsActivity;
import com.huyvo.cmpe277.sjsu.weatherapp.activities.WeatherFragment;
import com.huyvo.cmpe277.sjsu.weatherapp.activities.WeatherPageAdapter;
import com.huyvo.cmpe277.sjsu.weatherapp.model.WeatherModel;
import com.huyvo.cmpe277.sjsu.weatherapp.service.intent.UpdateForecastIntentService;
import com.huyvo.cmpe277.sjsu.weatherapp.service.intent.today.FetchTodayWeatherIntentService;
import com.huyvo.cmpe277.sjsu.weatherapp.util.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static com.huyvo.cmpe277.sjsu.weatherapp.util.Constants.MainViewMessages.UPDATE;
import static com.huyvo.cmpe277.sjsu.weatherapp.util.Constants.MainViewMessages.UPDATE_FORECAST;

public class MainActivity extends BaseActivityWithFragment implements ViewPager.OnPageChangeListener{

    public final static String TAG = "MainActivity";

    private PostFinishedListener postFinishedListener;
    private int mPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Logger.d(TAG, "onCreate");

        onLoadUI();

        Intent i = getIntent();
        mPosition = i.getIntExtra("position", -1);
        onLoadData();



    }

    public void setCurrentItem(int position){
        ViewPager pager = (ViewPager) findViewById(R.id.city_viewpager);
        pager.setCurrentItem(position);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.weather, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.weather:
                startActivity(new Intent(this, CityListViewActivity.class));
                break;
            case R.id.setting:
                startActivity(new Intent(this, SettingsActivity.class));
                break;

            default:
                break;
        }

        return true;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
       // Logger.d(TAG, "onPageScrolled="+position);

    }

    @Override
    public void onPageSelected(final int position) {
        Logger.d(TAG, "onPageSelected="+position);
        WeatherForecastContainer container = WeatherForecastContainer.getInstance();
        boolean shouldRequestFetchWeather = container.shouldRequestFetchWeather(WeatherApp.getLatLngList().get(position));
        if(shouldRequestFetchWeather){

            new Thread(new Runnable() {
                @Override
                public void run() {
                    Logger.d(TAG, position+"");
                    String location = WeatherApp.getLatLngList().get(position);
                    fetchTodayWeather(location);
                    fetchForecast(location);
                }
            }).start();
        }
    }

    private void fetchTodayWeather(String location){
        Intent i = new Intent(this, FetchTodayWeatherIntentService.class);
        i.putExtra(FetchTodayWeatherIntentService.FETCH_WEATHER, location);
        i.putExtra(FetchTodayWeatherIntentService.WHO, new Messenger(mHandler));
        startService(i);
    }

    public void fetchForecast(String location){
        Intent intent = new Intent(MainActivity.this, UpdateForecastIntentService.class);
        intent.putExtra(UpdateForecastIntentService.WHO, new Messenger(mHandler));
        intent.putExtra(UpdateForecastIntentService.FETCH_FORECAST, location);
        startService(intent);
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    @Override
    protected void onLoadUI() {
        ViewPager pager = (ViewPager) findViewById(R.id.city_viewpager);
        pager.addOnPageChangeListener(this);
        PagerAdapter adapter = new WeatherPageAdapter(getSupportFragmentManager());
        pager.setAdapter(adapter);
        TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(pager, true);
    }

    @Override
    protected void onFetchPeriodically() {
        // Update Every 3 hours if user is on screen
        /**
         ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
         executorService.scheduleAtFixedRate(new Runnable() {
        @Override
        public void run() {
        synchronized (WeatherApp.getLatLngList()) {

        for (String location : WeatherApp.getLatLngList()) {
        fetchForecast(location);
        fetchTodayWeather(location);
        }
        }
        }
        }, 0, 3, TimeUnit.HOURS);*/
    }

    @Override
    protected void onLoadData() {
        List<String> mLocations = WeatherApp.getLatLngList();
        if(!mLocations.isEmpty()){
            postFinishedListener = new LoadAllDataRunnable(mLocations);
            new Thread((Runnable) postFinishedListener).start();
            onFetchPeriodically();
        }
    }

    private class LoadAllDataRunnable implements Runnable, PostFinishedListener, Postable {
        private Queue<String> mLocations;
        public LoadAllDataRunnable(List<String> locations){

            mLocations = new LinkedList<>(locations);
        }

        @Override
        public void done() {
            postMessage();
        }

        @Override
        public void run() {
            postMessage();
        }

        @Override
        public void postMessage() {
            if(!mLocations.isEmpty()) {
                String location = mLocations.remove();
                try {
                    WeatherForecastContainer weatherForecastContainer = WeatherForecastContainer.getInstance();
                    List<WeatherModel> weatherModels = weatherForecastContainer.getWeatherModels(location);

                    TodayWeatherContainer todayWeatherContainer = TodayWeatherContainer.getInstance();
                    WeatherModel weatherModel = todayWeatherContainer.getWeatherModel(location);

                    LoadingPair loadingPair = new LoadingPair();
                    loadingPair.mModels = weatherModels;
                    loadingPair.model = weatherModel;

                    Message message = mHandler.obtainMessage();
                    message.obj = loadingPair;

                    message.what = -1;
                    mHandler.sendMessage(message);
                }catch (Exception e){}

            }
        }
    }


    interface PostFinishedListener{
        void done();
    }

    Handler mHandler = new Handler(){
        public void handleMessage(Message msg) {
            Bundle reply = msg.getData();
            if(reply != null){
                String location = reply.getString(UpdateForecastIntentService.FETCH_FORECAST, null);
                if(location != null){
                    List<WeatherModel> models = WeatherForecastContainer.getInstance().getWeatherModels(location);
                    int position = WeatherApp.getLatLngList().indexOf(location);
                    updateForecastView(position, models);
                    return;
                }

                location = reply.getString(FetchTodayWeatherIntentService.FETCH_WEATHER, null);
                if(location != null){
                    WeatherModel model = TodayWeatherContainer.getInstance().getWeatherModel(location);
                    int position = WeatherApp.getLatLngList().indexOf(location);
                    updateTodayView(position, model);

                    return;
                }
            }


            switch (msg.what){
                case UPDATE:
                    updateForecastView(mPosition, (List) msg.obj);
                    break;

                case UPDATE_FORECAST:
                    ForecastInfo info = (ForecastInfo) msg.obj;
                    updateForecastView(info.position, info.mList);
                    break;
                default:
                    if( msg.obj != null) {

                        int length = addToAdapter((LoadingPair) msg.obj);
                        postFinishedListener.done();
                        if (length - 1 == mPosition) {
                            new LoadPageAsync().execute();
                        }
                    }

                    break;
            }

        }
    };

    public int addToAdapter(LoadingPair loadingPair){
        ViewPager vp = (ViewPager) findViewById(R.id.city_viewpager);
        WeatherPageAdapter wpa = (WeatherPageAdapter) vp.getAdapter();
        wpa.add(WeatherFragment.newInstance(loadingPair.mModels, loadingPair.model));
        return wpa.getCount();
    }
    public boolean updateForecastView(int position, List<WeatherModel> weatherModels){
        Logger.d(TAG, "updateForecastView");
        ViewPager vp = (ViewPager) findViewById(R.id.city_viewpager);
        WeatherPageAdapter wpa = (WeatherPageAdapter) vp.getAdapter();

        if(position >= wpa.getCount()){
            return false;
        }
        WeatherFragment fragment = (WeatherFragment) wpa.getItem(position);
        if(fragment == null){
            return false;
        }
        fragment.setForecastView(weatherModels);
        return true;
    }
    public boolean updateTodayView(int position, WeatherModel model){
        ViewPager vp = (ViewPager) findViewById(R.id.city_viewpager);
        WeatherPageAdapter wpa = (WeatherPageAdapter) vp.getAdapter();

        if(position >= wpa.getCount()){
            return false;
        }
        WeatherFragment fragment = (WeatherFragment) wpa.getItem(position);
        if(fragment == null){
            return false;
        }
        fragment.setTodayView(model);
        return true;
    }

    public int addToAdapter(WeatherFragment fragment){
        ViewPager vp = (ViewPager) findViewById(R.id.city_viewpager);
        WeatherPageAdapter wpa = (WeatherPageAdapter) vp.getAdapter();
        wpa.add(fragment);
        return wpa.getCount();
    }

    class LoadingPair{
        public List<WeatherModel> mModels;
        public WeatherModel model;
    }

    class ForecastInfo{
        public int position;
        public List<WeatherModel> mList;
    }

    class LoadPageAsync extends AsyncTask<Void, Void, Void>{

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void Void){

            setCurrentItem(mPosition);
            mPosition = -1;
        }
    }
}