package de.digisocken.rss_o_tweet;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.util.Xml;
import android.widget.RemoteViews;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.models.Tweet;
import com.twitter.sdk.android.tweetui.SearchTimeline;
import com.twitter.sdk.android.tweetui.TimelineResult;
import com.twitter.sdk.android.tweetui.UserTimeline;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class WidgetUpdateService extends Service {
    SharedPreferences mPreferences;
    String[] urls;
    static String[] blacklist;
    static String _regexAll;
    static String _regexTo;

    public static String fixUml(String inp) {
        // windows2152 instead of utf8?!
        inp = inp.replace("\303\237", "ß");
        inp = inp.replace("\303\244", "ä");
        inp = inp.replace("\303\266", "ö");
        inp = inp.replace("\303\274", "ü");
        inp = inp.replace("\303\204", "Ä");
        inp = inp.replace("\303\226", "Ö");
        inp = inp.replace("\303\234", "Ü");
        return inp;
    }

    public static String extractTitles(String resp) {
        String back = "";
        resp = fixUml(resp);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            ByteArrayInputStream is = new ByteArrayInputStream(resp.getBytes("UTF-8"));
            Document doc = db.parse(is);
            doc.getDocumentElement().normalize();

            NodeList nodeList = doc.getElementsByTagName("item");
            if (nodeList.getLength() < 1) {
                nodeList = doc.getElementsByTagName("entry");
            }

            Node n;
            String ti;
            for (int i = 0; i < nodeList.getLength(); i++) {
                n = nodeList.item(i);
                boolean ff = true;
                ti = FeedContract.extract(n, "title");
                if ((_regexAll.isEmpty() && _regexTo.isEmpty()) == false) ti = ti.replaceAll(_regexAll, _regexTo);
                for (String bl: blacklist) {
                    if (ti.contains(bl)) ff = false;
                }
                if (ff) back += "<b>"+ Integer.toString(i+1) + ")</b> " + ti + "<br>";
            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.d(RssOTweet.TAG, "widget parse error");
        }

        return back;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        mPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        urls = mPreferences.getString("rss_url", RssOTweet.urls).split(" ");
        _regexAll = mPreferences.getString("regexAll", RssOTweet.Config.DEFAULT_regexAll);
        _regexTo = mPreferences.getString("regexTo", RssOTweet.Config.DEFAULT_regexTo);
        blacklist = new String[]{};
        String nos = mPreferences.getString("blacklist", "");
        if (!nos.equals("")) blacklist = nos.split(",");

        RemoteViews views = new RemoteViews(getPackageName(), R.layout.main_widget);

        views.setTextViewText(R.id.wFeedtitles, "...");
        views.setTextViewText(R.id.wTextApp, getString(R.string.app_name) + " - Feed #no1");


        // Push update for this widget to the home screen
        ComponentName thisWidget = new ComponentName(WidgetUpdateService.this, MyWidgetProvider.class);
        AppWidgetManager manager = AppWidgetManager.getInstance(WidgetUpdateService.this);
        manager.updateAppWidget(thisWidget, views);

        RequestQueue queue = Volley.newRequestQueue(this);
        String query = urls[0];

        if (!query.equals("")) {
            if (query.startsWith("#")) {

                query = query.replace("#","");
                SearchTimeline searchTimeline = new SearchTimeline.Builder()
                        .query(query)
                        .maxItemsPerRequest(RssOTweet.Config.DEFAULT_TWITTER_MAX)
                        .build();
                searchTimeline.next(null, new Callback<TimelineResult<Tweet>>(){

                    @Override
                    public void success(Result<TimelineResult<Tweet>> result) {
                        String lines = "";
                        RemoteViews views = new RemoteViews(getPackageName(), R.layout.main_widget);
                        Date dNow = new Date();
                        SimpleDateFormat ft = new SimpleDateFormat("HH:mm");
                        views.setTextViewText(R.id.wTime, ft.format(dNow));
                        for(Tweet tweet : result.data.items) {
                            lines += tweet.text + "\n";
                        }
                        views.setTextViewText(R.id.wFeedtitles, lines);

                        ComponentName thisWidget = new ComponentName(WidgetUpdateService.this, MyWidgetProvider.class);
                        AppWidgetManager manager = AppWidgetManager.getInstance(WidgetUpdateService.this);
                        manager.updateAppWidget(thisWidget, views);
                    }

                    @Override
                    public void failure(TwitterException exception) {
                        exception.printStackTrace();
                    }
                });

            } else if (query.startsWith("@")) {

                query = query.replace("@","");
                UserTimeline userTimeline = new UserTimeline.Builder()
                        .screenName(query)
                        .maxItemsPerRequest(RssOTweet.Config.DEFAULT_TWITTER_MAX)
                        .build();
                userTimeline.next(null, new Callback<TimelineResult<Tweet>>(){

                    @Override
                    public void success(Result<TimelineResult<Tweet>> result) {
                        String lines = "";
                        RemoteViews views = new RemoteViews(getPackageName(), R.layout.main_widget);
                        Date dNow = new Date();
                        SimpleDateFormat ft = new SimpleDateFormat("HH:mm");
                        views.setTextViewText(R.id.wTime, ft.format(dNow));
                        for(Tweet tweet : result.data.items) {
                            // no retweets
                            if (tweet.text.startsWith("RT @")) continue;
                            lines += tweet.text + "\n";
                        }
                        views.setTextViewText(R.id.wFeedtitles, lines);

                        ComponentName thisWidget = new ComponentName(WidgetUpdateService.this, MyWidgetProvider.class);
                        AppWidgetManager manager = AppWidgetManager.getInstance(WidgetUpdateService.this);
                        manager.updateAppWidget(thisWidget, views);
                    }

                    @Override
                    public void failure(TwitterException exception) {
                        exception.printStackTrace();
                    }
                });

            } else {

                StringRequest stringRequest = new StringRequest(
                        Request.Method.GET, query,

                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {
                                RemoteViews views = new RemoteViews(getPackageName(), R.layout.main_widget);

                                Date dNow = new Date();
                                SimpleDateFormat ft = new SimpleDateFormat("HH:mm");
                                views.setTextViewText(R.id.wTime, ft.format(dNow));
                                Spanned sp;

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    sp = Html.fromHtml(extractTitles(response), Html.FROM_HTML_MODE_COMPACT);
                                } else {
                                    sp = Html.fromHtml(extractTitles(response));
                                }

                                views.setTextViewText(R.id.wFeedtitles, sp);

                                // Push update for this widget to the home screen
                                ComponentName thisWidget = new ComponentName(WidgetUpdateService.this, MyWidgetProvider.class);
                                AppWidgetManager manager = AppWidgetManager.getInstance(WidgetUpdateService.this);
                                manager.updateAppWidget(thisWidget, views);
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                RemoteViews views = new RemoteViews(getPackageName(), R.layout.main_widget);
                                views.setTextViewText(R.id.wFeedtitles, getString(R.string.noConnection));
                                ComponentName thisWidget = new ComponentName(WidgetUpdateService.this, MyWidgetProvider.class);
                                AppWidgetManager manager = AppWidgetManager.getInstance(WidgetUpdateService.this);
                                manager.updateAppWidget(thisWidget, views);
                            }
                        }
                );
                queue.add(stringRequest);

            }
        }



        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}