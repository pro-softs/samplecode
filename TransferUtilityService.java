package com.app.ojam.Constants;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.app.ojam.Activities.HomeScreenActivity;
import com.app.ojam.Activities.SingleJamActivity;
import com.app.ojam.Objects.Jam;
import com.app.ojam.Objects.Pic;
import com.app.ojam.Objects.Profile;
import com.app.ojam.Objects.User;
import com.app.ojam.R;
import com.app.ojam.Retrofit.RestClient;
import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.google.firebase.iid.FirebaseInstanceId;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.app.ojam.Constants.MyFirebaseMessagingService.NOTIFICATION_CHANNEL_ID;

/**
 * Created by pR0 on 18-10-2016.
 */
public class TransferUtilityService extends Service implements RestClient.ResultReadyCallback {

    private static String BUCKET_NAME = "ojamdata";

    LinkedHashMap<Integer, Jam> uploader;
    LinkedHashMap<Integer, String> status;
    LinkedHashMap<Integer, Float> progress;

    LocalBroadcastManager broadcastManager;

    boolean isRunning = false;

    PrefManager prefManager = new PrefManager(Application.ctx);

    private final IBinder tranferBind = new TransferBinder();
    boolean frst = false, sec = false, third = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void resultReady(String result) {
        if (result.contains("gcm")) {
            if (result.contains("error"))
                prefManager.setGcmUpdated(false);
            else
                prefManager.setGcmUpdated(true);
        }
    }

    @Override
    public void resultReady(Jam jam) {

    }

    @Override
    public void resultReady(User user) {

    }

    @Override
    public void resultReady(List<Jam> jams) {

    }

    @Override
    public void resultReady(Profile profile) {

    }

    public class TransferBinder extends Binder {
        public TransferUtilityService getService() {
            return TransferUtilityService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return tranferBind;
    }

    //release resources when unbind
    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    AmazonS3Client s3;
    TransferUtility transferUtility;
    RestClient restClient;
    public static final String PATH = Environment.getExternalStorageDirectory().getAbsolutePath()
            + "/android/data/com.app.ojam";

    @Override
    public void onCreate() {

        super.onCreate();
        uploader = new LinkedHashMap<>();
        status = new LinkedHashMap<>();
        progress = new LinkedHashMap<>();

        prefManager = new PrefManager(Application.ctx);

        restClient = RestClient.getInstance();

        broadcastManager = LocalBroadcastManager.getInstance(this);

        credentialsProvider();
        setTransferUtility();
    }

    public int jamPost(Jam jam) throws IOException {
        Log.e("TransferUtiityService", "start saving");
        if (jam.getJammers().size() == 1)
            return prepareUpload(jam, jam.getMusic_path() + "/first.mp4");
        else if (jam.getJammers().size() == 3)
            return prepareUpload(jam, jam.getMusic_path() + "/third.mp4");
        else if (jam.getJammers().size() == 2)
            return prepareUpload(jam, jam.getMusic_path() + "/sec.mp4");
        else
            return -1;
    }

    public int jamPostRetry(Jam jam, int id) throws IOException {
        Log.e("TransferUtiityService", "retrying jam");

        if (jam.getJammers().size() == 1)
            return startAgain(jam, id, jam.getMusic_path() + "/first.mp4");
        else if (jam.getJammers().size() == 3)
            return startAgain(jam, id, jam.getMusic_path() + "/third.mp4");
        else if (jam.getJammers().size() == 2)
            return startAgain(jam, id, jam.getMusic_path() + "/sec.mp4");
        else
            return -1;
    }

    public void justSave(final Jam jam, final int id) {
        if (jam.getJammers().size() == 1) {
            Intent i = new Intent("local_stat");
            i.putExtra("id", jam.getMusic_path());
            i.putExtra("status", "uploaded_s3");
            i.putExtra("progress", 100.0f);
            broadcastManager.sendBroadcast(i);

            restClient.startJam(jam, new RestClient.ResultReadyCallback() {
                @Override
                public void resultReady(String result) {
                    if (result.equals("saved")) {
                        Intent i = new Intent("stat");
                        i.putExtra("id", jam.getMusic_path());
                        i.putExtra("status", "saved");

                        if (status.containsKey(id))
                            status.remove(id);
                        if (uploader.containsValue(id))
                            uploader.remove(id);
                        if (progress.containsKey(id))
                            progress.remove(id);

                        broadcastManager.sendBroadcast(i);

                        Log.e("saved", "jam - " + jam.getName());
                    } else if (result.equals("not_saved")) {
                        Intent i = new Intent("stat");
                        i.putExtra("id", id);
                        i.putExtra("status", "remove");

                        if (status.containsKey(id))
                            status.remove(id);
                        if (uploader.containsValue(id))
                            uploader.remove(id);
                        if (progress.containsKey(id))
                            progress.remove(id);

                        broadcastManager.sendBroadcast(i);

                        Log.e("not_saved", "jam - " + jam.getName());
                    } else {
                        Intent i = new Intent("local_stat");
                        i.putExtra("id", jam.getMusic_path());
                        i.putExtra("status", "failed");

                        status.put(id, "failed");
                        broadcastManager.sendBroadcast(i);

                        Log.e("failed saving", "jam - " + jam.getName());
                    }
                }

                @Override
                public void resultReady(Jam jam) {

                }

                @Override
                public void resultReady(User user) {

                }

                @Override
                public void resultReady(List<Jam> jams) {

                }

                @Override
                public void resultReady(Profile profile) {

                }
            });
        } else if (jam.getJammers().size() == 2) {
            restClient.jamSec(jam.getJammers().get(1).getId(), jam.get_id(), jam.getMusic_path(),
                    new RestClient.ResultReadyCallback() {

                @Override
                public void resultReady(String result) {
                    if (result.equals("saved")) {

                        Intent i = new Intent("stat");
                        i.putExtra("id", id);
                        i.putExtra("status", "saved");

                        if (status.containsKey(id))
                            status.remove(id);
                        if (uploader.containsValue(id))
                            uploader.remove(id);
                        if (progress.containsKey(id))
                            progress.remove(id);

                        broadcastManager.sendBroadcast(i);

                        Log.e("saved", "jam - " + jam.getName());
                    } else if (result.equals("not_saved")) {
                        Intent i = new Intent("stat");
                        i.putExtra("id", id);
                        i.putExtra("status", "remove");

                        if (status.containsKey(id))
                            status.remove(id);
                        if (uploader.containsValue(id))
                            uploader.remove(id);
                        if (progress.containsKey(id))
                            progress.remove(id);

                        broadcastManager.sendBroadcast(i);
                        Log.e("not_saved", "jam - " + jam.getName());
                    } else {
                        Intent i = new Intent("stat");
                        i.putExtra("id", id);
                        i.putExtra("status", "failed");

                        broadcastManager.sendBroadcast(i);

                        Log.e("failed saving", "jam - " + jam.getName());
                    }
                }

                @Override
                public void resultReady(Jam jam) {

                }

                @Override
                public void resultReady(User user) {

                }

                @Override
                public void resultReady(List<Jam> jams) {

                }

                @Override
                public void resultReady(Profile profile) {

                }
            });
        } else if (jam.getJammers().size() == 3) {
            restClient.jamThird(jam.getJammers().get(2).getId(), jam.get_id(), jam.getMusic_path(), new RestClient.ResultReadyCallback() {

                @Override
                public void resultReady(String result) {
                    if (result.equals("saved")) {
                        Intent i = new Intent("stat");
                        i.putExtra("id", id);
                        i.putExtra("status", "saved");

                        if (status.containsKey(id))
                            status.remove(id);
                        if (uploader.containsValue(id))
                            uploader.remove(id);
                        if (progress.containsKey(id))
                            progress.remove(id);

                        broadcastManager.sendBroadcast(i);
                        Log.e("saved", "jam - " + jam.getName());
                    } else if (result.equals("not_saved")) {
                        Intent i = new Intent("stat");
                        i.putExtra("id", id);
                        i.putExtra("status", "remove");

                        if (status.containsKey(id))
                            status.remove(id);
                        if (uploader.containsValue(id))
                            uploader.remove(id);
                        if (progress.containsKey(id))
                            progress.remove(id);
                        broadcastManager.sendBroadcast(i);
                        Log.e("not_saved", "jam - " + jam.getName());
                    } else {
                        Intent i = new Intent("stat");
                        i.putExtra("id", id);
                        i.putExtra("status", "failed");

                        broadcastManager.sendBroadcast(i);
                        Log.e("failed saving", "jam - " + jam.getName());
                    }
                }

                @Override
                public void resultReady(Jam jam) {

                }

                @Override
                public void resultReady(User user) {

                }

                @Override
                public void resultReady(List<Jam> jams) {

                }

                @Override
                public void resultReady(Profile profile) {

                }
            });
        }
    }

    public void credentialsProvider() {
        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                Application.ctx, "us-east-1:4f917d0f-34d3-41fd-909c-3baa2e84157c",
                Regions.US_EAST_1
        );

        Map<String, String> logins = new HashMap<String, String>();

        Log.e("fb_key_here", prefManager.getToken());

        prefManager = new PrefManager(Application.ctx);

        if (!prefManager.getToken().isEmpty()) {
            logins.put("graph.facebook.com", prefManager.getToken());
            credentialsProvider.setLogins(logins);
            setAmazonS3Client(credentialsProvider);
        } else {
            Log.e("error", "no fb key erer er erer ");
        }
    }

    public void setAmazonS3Client(CognitoCachingCredentialsProvider credentialsProvider) {
        s3 = new AmazonS3Client(credentialsProvider);
        s3.setRegion(Region.getRegion(Regions.US_EAST_1));
    }

    public void setTransferUtility() {
        transferUtility = new TransferUtility(s3, Application.ctx);
    }

    public TransferObserver setFileToUplaod(String keyName, String fileName) {

        TransferObserver transferObserver = transferUtility.upload(
                BUCKET_NAME,      //The bucket to upload to
                keyName,     //The key for the uploaded object
                new File(fileName)      // The file where the data to upload exists
        );
        Log.e("upload file", fileName);

        return transferObserver;
    }

    public void transferObserverListener(final TransferObserver transferObserver, final Jam jam, final boolean create) {
        transferObserver.setTransferListener(new TransferListener() {

            @Override
            public void onStateChanged(final int id, final TransferState state) {
                Log.e("state changed_audio", state.name());

                if (state.equals(TransferState.COMPLETED)) {
                    Intent i = new Intent("stat");
                    i.putExtra("id", id);
                    i.putExtra("status", "uploaded_s3");
                    i.putExtra("progress", 100.0f);

                    status.put(id, "uploaded_s3");
                    progress.put(id, 100.0f);

                    broadcastManager.sendBroadcast(i);


                    if (create) {
                        restClient.startJam(jam, new RestClient.ResultReadyCallback() {
                            @Override
                            public void resultReady(String result) {
                                if (result.equals("saved")) {
                                    Log.e("saved", "jam - " + jam.getName());

                                    Intent i = new Intent("local_stat");
                                    i.putExtra("id", id);
                                    i.putExtra("status", "saved");
                                    i.putExtra("progress", 100.0f);
                                    status.put(id, "saved");

                                    if (status.containsKey(id))
                                        status.remove(id);
                                    if (uploader.containsValue(id))
                                        uploader.remove(id);
                                    if (progress.containsKey(id))
                                        progress.remove(id);


                                    final File f2 = new File(PATH + "/create/" + jam.getMusic_path() + ".mp4");
                                    f2.delete();

                                    broadcastManager.sendBroadcast(i);
                                    //showNotification("Jam Posted", "Your Jam '" + jam.getName() + "' has been successfully posted.");
                                } else {
                                    Log.e("failed saving", "jam - " + jam.getName());

                                    Intent i = new Intent("local_stat");
                                    i.putExtra("id", id);
                                    i.putExtra("status", "failed");
                                    i.putExtra("progress", 100.0f);
                                    status.put(id, "failed");

                                    //showNotification("Jam Posted", "Your Jam '" + jam.getName() + "' was not posted successfully.");
                                    broadcastManager.sendBroadcast(i);
                                }
                            }

                            @Override
                            public void resultReady(Jam jam) {

                            }

                            @Override
                            public void resultReady(User user) {

                            }

                            @Override
                            public void resultReady(List<Jam> jams) {

                            }

                            @Override
                            public void resultReady(Profile profile) {

                            }
                        });
                    } else {
                        if (jam.getJammers().size() == 2) {
                            Log.e("uploading_sec", "hreer");

                            restClient.jamSec(jam.getJammers().get(1).getId(), jam.get_id(), jam.getMusic_path(), new RestClient.ResultReadyCallback() {

                                @Override
                                public void resultReady(String result) {
                                    if (result.equals("saved")) {
                                        Log.e("saved", "jam - " + jam.getName());

                                        Intent i = new Intent("stat");
                                        i.putExtra("id", id);
                                        i.putExtra("progress", 100.0f);
                                        i.putExtra("status", "saved");

                                        if (status.containsKey(id))
                                            status.remove(id);
                                        if (uploader.containsValue(id))
                                            uploader.remove(id);
                                        if (progress.containsKey(id))
                                            progress.remove(id);

                                        final File f2 = new File(PATH + "/mixing/" + jam.getMusic_path() + ".mp4");

                                        f2.delete();

                                        broadcastManager.sendBroadcast(i);
                                        //showNotification("Jam Posted", "Your Jam '" + jam.getName() + "' has been successfully posted.");
                                    } else if (result.equals("not_saved")) {
                                        Intent i = new Intent("stat");
                                        i.putExtra("id", id);

                                        i.putExtra("progress", 100.0f);
                                        i.putExtra("status", "remove");

                                        if (status.containsKey(id))
                                            status.remove(id);
                                        if (uploader.containsKey(id))
                                            uploader.remove(id);
                                        if (progress.containsKey(id))
                                            progress.remove(id);

                                        final File f2 = new File(PATH + "/mixing/" + jam.getMusic_path() + ".mp4");

                                        f2.delete();

                                        broadcastManager.sendBroadcast(i);
                                        Log.e("not_saved", "jam - " + jam.getName());
                                    } else {
                                        Intent i = new Intent("stat");
                                        i.putExtra("id", id);

                                        i.putExtra("progress", 100.0f);
                                        i.putExtra("status", "failed");
                                        status.put(id, "failed");

                                        broadcastManager.sendBroadcast(i);
                                        //showNotification("Jam save failed", "Your Jam '" + jam.getName() + "' was not posted successfully.");
                                        Log.e("failed saving", "jam - " + jam.getName());
                                    }
                                }

                                @Override
                                public void resultReady(Jam jam) {

                                }

                                @Override
                                public void resultReady(User user) {

                                }

                                @Override
                                public void resultReady(List<Jam> jams) {

                                }

                                @Override
                                public void resultReady(Profile profile) {

                                }
                            });
                        } else if (jam.getJammers().size() == 3) {
                            restClient.jamThird(jam.getJammers().get(2).getId(), jam.get_id(), jam.getMusic_path(), new RestClient.ResultReadyCallback() {

                                @Override
                                public void resultReady(String result) {
                                    if (result.contains("saved")) {
                                        Log.e("saved", "jam - " + jam.getName());

                                        Intent i = new Intent("stat");
                                        i.putExtra("id", id);

                                        i.putExtra("progress", 100.0f);
                                        i.putExtra("status", "saved");

                                        if (status.containsKey(id))
                                            status.remove(id);
                                        if (uploader.containsKey(id))
                                            uploader.remove(id);
                                        if (progress.containsKey(id))
                                            progress.remove(id);

                                        final File f2 = new File(PATH + "/mixing/" + jam.getMusic_path() + ".mp4");

                                        f2.delete();
                                        broadcastManager.sendBroadcast(i);
//                                        showNotification("Jam Posted", "Your Jam '" +
                                        //                                              jam.getName() + "' has benn successfully posted.", );
                                    } else if (result.equals("not_saved")) {
                                        if (status.containsKey(id))
                                            status.remove(id);
                                        if (uploader.containsKey(id))
                                            uploader.remove(id);
                                        if (progress.containsKey(id))
                                            progress.remove(id);

                                        Intent i = new Intent("stat");
                                        i.putExtra("id", id);
                                        i.putExtra("status", "remove");

                                        final File f2 = new File(PATH + "/mixing/" + jam.getMusic_path() + ".mp4");

                                        f2.delete();
                                        broadcastManager.sendBroadcast(i);


                                        Log.e("not_saved", "jam - " + jam.getName());
                                    } else {
                                        Intent i = new Intent("stat");
                                        i.putExtra("id", id);
                                        i.putExtra("status", "failed");
                                        status.put(id, "failed");

                                        i.putExtra("progress", 100.0f);

                                        broadcastManager.sendBroadcast(i);
                                        showNotification("Jam save failed", "Your Jam '" +
                                                jam.getName() + "' was not posted successfully.", "");

                                        Log.e("failed saving", "jam - " + jam.getName());
                                    }
                                }

                                @Override
                                public void resultReady(Jam jam) {

                                }

                                @Override
                                public void resultReady(User user) {

                                }

                                @Override
                                public void resultReady(List<Jam> jams) {

                                }

                                @Override
                                public void resultReady(Profile profile) {

                                }
                            });
                        }
                    }
                } else if (state.equals(TransferState.FAILED)) {
                    Intent i = new Intent("local_stat");
                    i.putExtra("id", jam.getMusic_path());
                    i.putExtra("status", "failed_s3");
                    status.put(id, "failed_s3");
                    progress.put(id, 0f);

                    broadcastManager.sendBroadcast(i);
                    showNotification("Jam save failed", "Your Jam '" +
                            jam.getName() + "' was not posted successfully.", "");
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                Intent i = new Intent();
                i.setAction("stat");
                i.putExtra("id", id);
                i.putExtra("status", "uploading");

                float percentage;

                if (bytesTotal == 0)
                    i.putExtra("progress", 0.0f);
                else {
                    percentage = (((float) bytesCurrent / bytesTotal) * 100);
                    Log.e("uploading", percentage + "");

                    i.putExtra("progress", percentage);
                    progress.put(id, percentage);
                }
                status.put(id, "uploading");

                broadcastManager.sendBroadcast(i);
            }

            @Override
            public void onError(int id, Exception ex) {
                Log.e("err_audio", ex.getMessage() + " " + id);

                Intent i = new Intent();
                i.setAction("stat");
                i.putExtra("id", id);
                i.putExtra("status", "failed_s3");
                status.put(id, "failed_s3");
                progress.put(id, 0.0f);

                broadcastManager.sendBroadcast(i);
            }
        });
    }

    public int prepareUpload(final Jam jam, String name) throws IOException {

        TransferObserver upload;

        if (jam.getJammers().size() == 1) {
            upload = setFileToUplaod(name, PATH + "/create/" + jam.getMusic_path() + "/first.mp4");
            transferObserverListener(upload, jam, true);
        } else
            upload = setFileToUplaod(name, PATH + "/mixing/" + jam.getMusic_path() + ".mp4");

        uploader.put(upload.getId(), jam);
        status.put(upload.getId(), "uploading");
        progress.put(upload.getId(), 0.0f);

        return upload.getId();
    }

    public void startUpload(int id) {
        Jam jam = uploader.get(id);

        Log.e("In transfer service", id + "");

        if (jam.getJammers().size() == 1)
            transferObserverListener(transferUtility.getTransferById(id), jam, true);
        else
            transferObserverListener(transferUtility.getTransferById(id), jam, false);
    }

    public int startAgain(Jam jam, int id, String name) {

        TransferObserver upload;

        if (jam.getJammers().size() == 1)
            upload = setFileToUplaod(name, PATH + "/create/" + jam.getMusic_path() + ".mp4");
        else
            upload = setFileToUplaod(name, PATH + "/mixing/" + jam.getMusic_path() + ".mp4");

        uploader.remove(id);
        status.remove(id);
        progress.remove(id);

        uploader.put(upload.getId(), jam);
        status.put(upload.getId(), "uploading");
        progress.put(upload.getId(), 0.0f);

        return upload.getId();
    }

    public void download(final String toDownload) {
        final File download = new File(PATH + "/share_final.mp4");
        TransferObserver downloader = transferUtility.download("ojamdata", toDownload, download);
        downloader.setTransferListener(new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {
                if (state.equals(TransferState.COMPLETED)) {

                    Intent i = new Intent();
                    i.setAction("downloaded");
                    i.putExtra("success", true);
                    broadcastManager.sendBroadcast(i);

                    Log.e("doewnloaded", "completed");
                    String[] cmd = new String[]{"-i", PATH + "/share.mp4", "-i", PATH + "/logo.png", "-filter_complex",
                            "overlay=x=10:y=(main_h - overlay_h) - 10",
                            PATH + "/share_final.mp4"};
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {

                float percentage = 0.0f;

                Intent i = new Intent();
                i.setAction("downloading");

                if (bytesTotal == 0)
                    i.putExtra("progress", 0.0f);
                else {
                    percentage = (((float) bytesCurrent / bytesTotal) * 100);
                    i.putExtra("progress", percentage);
                }

                Log.e("download_share", toDownload + "  " + percentage + "");
                broadcastManager.sendBroadcast(i);
            }

            @Override
            public void onError(int id, Exception ex) {

                Log.e("error_share", ex.getMessage());
                Intent i = new Intent("downloaded");
                i.putExtra("success", false);
                broadcastManager.sendBroadcast(i);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        prefManager.setCurUploading("");
        prefManager.setUploadPerc(-1);
    }

    public LinkedHashMap getJams() {
        return uploader;
    }

    public LinkedHashMap getStatus() {
        return status;
    }

    public LinkedHashMap getProgress() {
        return progress;
    }

    public Object getElementByIndex(LinkedHashMap map, int index) {

        if (map.keySet().toArray().length != 0) {
            Object o = (map.keySet().toArray())[index];

            if (o != null)
                if (map.get(o) != null)
                    return map.get(o);
        }

        return new Object();
    }

    public void showNotification(String title, String message, String id) {
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        Intent intent = new Intent(this, SingleJamActivity.class);
        intent.putExtra("id", id);

        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.logo);

        if (!id.isEmpty()) {
            PendingIntent pIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(),
                    intent, 0);

            intent.putExtra("share", true);
            PendingIntent shareintent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(),
                    intent, 0);

            NotificationCompat.Action action = new NotificationCompat.Action.Builder
                    (R.drawable.icon_dark_share, "Invite", shareintent).build();

            NotificationCompat.Builder nb = new NotificationCompat.Builder(this)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(R.drawable.logo_small)
                    .setLargeIcon(Bitmap.createScaledBitmap(bitmap,
                    128, 128, false))
                    .setContentIntent(pIntent)
                    .setSound(alarmSound)
                    .addAction(action)
                    .setVibrate(new long[]{1000, 1000, 1000, 1000, 1000})
                    .setAutoCancel(true);

            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            stopForeground(true);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                int importance = NotificationManager.IMPORTANCE_HIGH;
                NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "NOTIFICATION_CHANNEL_NAME", importance);
                notificationChannel.enableLights(true);
                notificationChannel.setLightColor(Color.RED);
                notificationChannel.enableVibration(true);
                notificationChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
                assert notificationManager != null;
                nb.setChannelId(NOTIFICATION_CHANNEL_ID);
                notificationManager.createNotificationChannel(notificationChannel);
            }

            Notification n = nb.build();
            n.flags |= Notification.FLAG_AUTO_CANCEL;

            notificationManager.notify(0, n);
        } else {
            Notification.Builder nb = new Notification.Builder(this)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(R.drawable.logo_small)
                    .setLargeIcon(Bitmap.createScaledBitmap(bitmap,
                            128, 128, false))
                    .setSound(alarmSound)
                    .setVibrate(new long[]{1000, 1000, 1000, 1000, 1000})
                    .setAutoCancel(true);

            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.cancelAll();
            stopForeground(true);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                int importance = NotificationManager.IMPORTANCE_HIGH;
                NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "NOTIFICATION_CHANNEL_NAME", importance);
                notificationChannel.enableLights(true);
                notificationChannel.setLightColor(Color.RED);
                notificationChannel.enableVibration(true);
                notificationChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
                nb.setChannelId(NOTIFICATION_CHANNEL_ID);
                notificationManager.createNotificationChannel(notificationChannel);
            }

            Notification n = nb.build();
            n.flags |= Notification.FLAG_AUTO_CANCEL;

            notificationManager.notify(0, n);
        }
    }

    public Jam getJamById(int id) {
        return uploader.get(id);
    }


    public String getStatusById(int id) {
        return status.get(id);
    }

    public float getProgressById(int id) {
        return progress.get(id);
    }

    public void uploadAll(final String path, final String title, final List<String> tags, final String folderName,
                          final boolean three) {

        Intent i = new Intent("local_stat");
        i.putExtra("id", folderName);
        i.putExtra("status", "uploading");

        broadcastManager.sendBroadcast(i);

        TransferObserver observer;

        if (three)
            observer = setFileToUplaod(folderName + "/merged.mp4", path + "/post.mp4");
        else
            observer = setFileToUplaod(folderName + "/merged.mp4", path + "/temp_post.mp4");

        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.logo);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        Notification.Builder notificationBuilder = new Notification.Builder(TransferUtilityService.this) //
                .setContentTitle(title)
                .setContentText("Uploading your Jam")
                .setSmallIcon(R.drawable.ic_upload)
                .setLargeIcon(Bitmap.createScaledBitmap(bitmap,
                        128, 128, false))
                .setAutoCancel(true)
                .setProgress(100, 0, false)
                .setPriority(Notification.PRIORITY_MAX);

        Notification notification = notificationBuilder.build();

        manager.cancelAll();
        manager.notify(0, notification);

        observer.setTransferListener(new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {
                if (state == TransferState.COMPLETED) {
                    Log.e("zSTATE", "COMPLWTED");
                    saveSelfJam(path, title, tags, folderName, three);
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                Log.e("1", bytesCurrent + "");

                float percentage = 0.0f;

                if (bytesTotal == 0)
                    percentage = 0.0f;
                else {
                    percentage = (((float) bytesCurrent / bytesTotal) * 100);
                }

                Intent i = new Intent("local_stat");
                i.putExtra("id", folderName);
                i.putExtra("status", "upload");

                prefManager.setUploadPerc((int) percentage);
                broadcastManager.sendBroadcast(i);
                notificationBuilder.setProgress(100, (int) percentage, false);
                Notification notification = notificationBuilder.build();
                manager.notify(0, notification);
            }

            @Override
            public void onError(int id, Exception ex) {
                Log.e("1", ex.toString());
                Intent i = new Intent("local_stat");
                i.putExtra("id", folderName);
                i.putExtra("status", "failed_with_s3");
                prefManager.setUploadPerc(-2);
                prefManager.setCurUploading("");

                showNotification("Jam Post Failed", "Your Jam '" +
                        title + "' couldn't be posted.", "");

                broadcastManager.sendBroadcast(i);
            }
        });
    }

    private void saveSelfJam(String path, final String title, List<String> tags,
                             final String folder, boolean three) {

        Jam newJ = new Jam();
        newJ.setTags(tags);
        newJ.setCompleted(true);
        newJ.setName(title);
        newJ.setMusic_path(folder);

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        String date = simpleDateFormat.format(new Date());
        Log.e("date of creation", date);

        newJ.setCreated(date);

        List<Pic> pics = new ArrayList<>();

        pics.add(new Pic(prefManager.getId(), prefManager.getPic(), prefManager.getUserName()));
        pics.add(new Pic(prefManager.getId(), prefManager.getPic(), prefManager.getUserName()));
        pics.add(new Pic(prefManager.getId(), prefManager.getPic(), prefManager.getUserName()));

        newJ.setJammers(pics);

        Intent i = new Intent("local_stat");
        i.putExtra("id", folder);
        i.putExtra("status", "uploading");

        broadcastManager.sendBroadcast(i);
        restClient.selfStart(newJ, new RestClient.ResultReadyCallback() {
                @Override
            public void resultReady(String result) {

                Log.e("RESULT", result);
                if (result.contains("saved")) {
                    Log.e("all jam", "saved");

                    String[] ids = result.split("_");

                    File f = new File(PATH + "/create/" + folder);

                    if (f.exists())
                        deleteDirectory(f);

                    Intent i = new Intent("local_stat");
                    i.putExtra("id", folder);
                    i.putExtra("status", "success");

                    Intent i2 = new Intent("local_count");
                    i2.putExtra("inc", true);

                    broadcastManager.sendBroadcast(i);
                    broadcastManager.sendBroadcast(i2);

                    prefManager.setCurUploading("");

                    showNotification("Jam Posted", "Your Jam '" + title + "' has been successfully posted.", ids[1]);
                } else {
                    Intent i = new Intent("local_stat");
                    i.putExtra("id", folder);
                    i.putExtra("status", "failed_without_s3");

                    prefManager.setUploadPerc(-3);
                    prefManager.setCurUploading("");

                    broadcastManager.sendBroadcast(i);
                    showNotification("Jam Post Failed", "Your Jam '" + title + "' couldn't be posted.", "");
                }
            }

            @Override
            public void resultReady(Jam jam) {

            }

            @Override
            public void resultReady(User user) {

            }

            @Override
            public void resultReady(List<Jam> jams) {

            }

            @Override
            public void resultReady(Profile profile) {

            }
        });
    }

    public void mergeOne(final String path, final String title, final List<String> tags, final String folder) {
        Intent i = new Intent("local_stat");
        i.putExtra("id", folder);
        i.putExtra("status", "merging");

        broadcastManager.sendBroadcast(i);
        prefManager.setCurUploading(folder);
        prefManager.setUploadPerc(-1);

        String cmd[];

        File f = new File(path + "/first_a.wav");
        String pathStr = path + "/first_a.wav";

        if (!f.exists()) {
            pathStr = path + "/first_a.m4a";
        }

            cmd = new String[]{"-i", path + "/first.mp4", "-itsoffset", "0.3", "-i", pathStr,
                    "-y", "-filter_complex",
                    "[0:v]scale='if(gt(iw,ih),400,ih*400/iw)':'if(gt(iw,ih),400,ih*400/iw)'[temp];" +
                            "[temp]crop=400:400[v];" +
                            "[1]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=mono,volume=1[a]",
                    "-acodec", "aac", "-vcodec",
                    "libx264", "-map", "[v]",
                    "-map","[a]", "-crf", "23",
                    "-video_track_timescale", "45",
                    "-f", "mp4", path + "/post.mp4"};

        FFmpeg ffmpeg = FFmpeg.getInstance(this);

        try {
            // to execute "ffmpeg -version" command you just need to pass "-version"
            ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {

                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onStart() {
                    Log.e("start", "conveting");

                    isRunning = true;
                    Notification notification = new Notification.Builder(TransferUtilityService.this) //
                            .setContentTitle(title)
                            .setContentText("Processing your Jam")
                            .setSmallIcon(R.drawable.logo)
                            .setColor(ContextCompat.getColor(TransferUtilityService.this, R.color.colorPrimary))
                            .setAutoCancel(false)
                            .setProgress(0, 0, true)
                            .setPriority(Notification.PRIORITY_MAX)
                            .build();
                    showNotification("Post " + title, "Your Jam '" + title + "' is being prepared.", "");
                }

                @Override
                public void onProgress(String message) {
                    Log.e("message", message);

                    Intent i = new Intent("local_stat");
                    i.putExtra("id", folder);
                    i.putExtra("status", "merging");

                    //broadcastManager.sendBroadcast(i);
                }

                @Override
                public void onFailure(String message) {
                    Log.e("error", message);
                    Intent i = new Intent("local_stat");
                    i.putExtra("id", folder);
                    i.putExtra("status", "failed");

                    broadcastManager.sendBroadcast(i);
                    showNotification("Jam Post Failed", "Your Jam '" +
                            title + "' couldn't be posted.", "");
                    prefManager.setUploadPerc(-1);
                    prefManager.setCurUploading("");
                }

                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onSuccess(String message) {
                    Log.e("done", message);

                    uploadAll(path, title, tags, folder, true);
                }

                @Override
                public void onFinish() {

                    isRunning = false;
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            // Handle if FFmpeg is already running
            Log.e("Already running", "ffmpeg");
        }

    }

    public void seperateAudioVideo(String inputFile, String finalPathV, String finalPathA) {
        FFmpeg ffmpeg = FFmpeg.getInstance(this);
        String cmd[] = {"-i", inputFile, "-map", "0:0", "-vcodec", "copy", finalPathV,
                "-map", "0:1", "-acodec", "copy", finalPathA};

        try {
            // to execute "ffmpeg -version" command you just need to pass "-version"
            ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {

                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onStart() {
                    Log.e("start", "conveting");

                    isRunning = true;
               }

                @Override
                public void onProgress(String message) {
                    Log.e("message", message);
                }

                @Override
                public void onFailure(String message) {
                    Log.e("error", message);
                }

                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onSuccess(String message) {
                    if(new File(inputFile).exists())
                        new File(inputFile).delete();

                    Intent i = new Intent();
                    i.setAction("separated");
                    broadcastManager.sendBroadcast(i);

                    Log.e("done", message);
                }

                @Override
                public void onFinish() {

                    isRunning = false;
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            // Handle if FFmpeg is already running
            Log.e("Already running", "ffmpeg");
        }

    }

    public void merge(final String path, final String title, final List<String> tags, final String folder,
                      final int delay) {
        copyLogo();

        Intent i = new Intent("local_stat");
        i.putExtra("id", folder);
        i.putExtra("status", "merging");

        broadcastManager.sendBroadcast(i);
        prefManager.setCurUploading(folder);
        prefManager.setUploadPerc(-1);

        String cmd[];

        File f = new File(path + "/third_post.wav");
        String pathStr = path + "/third_post.wav";

        if (!f.exists()) {
            pathStr = path + "/third_post.m4a";
        }

        if (delay > 0) {
            cmd = new String[]{"-i", path + "/first.mp4", "-i", path + "/sec.mp4",
                    "-i", path + "/third.mp4", "-i", pathStr,
                    "-y", "-filter_complex",
                    "[1:v]scale=200:ih*200/iw[temp];" +
                            "[temp]crop=200:200[top_right];" +
                            "[2:v]scale=200:ih*200/iw[temp1];" +
                            "[temp1]crop=200:200[bottom_right];" +
                            "[top_right][bottom_right]vstack[right];" +
                            "[0:v]scale=400:ih*400/iw[temp2]" +
                            ";[temp2]crop=200:400[left];" +
                            "[left][right]hstack[v];" +
                            "[3:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=mono,volume=1[a]",
                    "-acodec", "aac", "-vcodec",
                    "libx264", "-map", "[v]",
                    "-map","[a]", "-crf", "23",
                    "-f", "mp4", path + "/post.mp4"};
        } else {
            cmd = new String[]{"-i", path + "/first.mp4", "-i", path + "/sec.mp4",
                    "-i", path + "/third.mp4", "-i", pathStr,
                    "-y", "-filter_complex",
                    "[1:v]scale=200:ih*200/iw[temp];" +
                            "[temp]crop=200:200[top_right];" +
                            "[2:v]scale=200:ih*200/iw[temp1];" +
                            "[temp1]crop=200:200[bottom_right];" +
                            "[top_right][bottom_right]vstack[right];" +
                            "[0:v]scale=400:ih*400/iw[temp2]" +
                            ";[temp2]crop=200:400[left];" +
                            "[left][right]hstack[v];" +
                            "[3:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=mono,volume=1[a]",
                    "-acodec", "aac", "-vcodec",
                    "libx264", "-map", "[v]",
                    "-map","[a]", "-crf", "23",
                    "-f", "mp4", path + "/post.mp4"};
        }

        FFmpeg ffmpeg = FFmpeg.getInstance(this);

        try {
            // to execute "ffmpeg -version" command you just need to pass "-version"
            ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {

                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onStart() {
                    Log.e("start", "conveting");

                    isRunning = true;
                    Notification notification = new Notification.Builder(TransferUtilityService.this) //
                            .setContentTitle(title)
                            .setContentText("Processing your Jam")
                            .setSmallIcon(R.drawable.logo)
                            .setColor(ContextCompat.getColor(TransferUtilityService.this, R.color.colorPrimary))
                            .setAutoCancel(false)
                            .setProgress(0, 0, true)
                            .setPriority(Notification.PRIORITY_MAX)
                            .build();
                    showNotification("Post " + title, "Your Jam '" + title + "' is being prepared.", "");
                }

                @Override
                public void onProgress(String message) {
                    Log.e("message", message);

                    Intent i = new Intent("local_stat");
                    i.putExtra("id", folder);
                    i.putExtra("status", "merging");

                    //broadcastManager.sendBroadcast(i);
                }

                @Override
                public void onFailure(String message) {
                    Log.e("error", message);
                    Intent i = new Intent("local_stat");
                    i.putExtra("id", folder);
                    i.putExtra("status", "failed");

                    broadcastManager.sendBroadcast(i);
                    showNotification("Jam Post Failed", "Your Jam '" +
                            title + "' couldn't be posted.", "");
                    prefManager.setUploadPerc(-1);
                    prefManager.setCurUploading("");
                }

                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onSuccess(String message) {
                    Log.e("done", message);

                    uploadAll(path, title, tags, folder, true);
                }

                @Override
                public void onFinish() {

                    isRunning = false;
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            // Handle if FFmpeg is already running
            Log.e("Already running", "ffmpeg");
        }
    }

    public void mergeTwo(final String path, final String title, final List<String> tags,
                         final String folder, final boolean post, final int delay) {
        Intent i = new Intent("local_stat");
        i.putExtra("id", folder);
        i.putExtra("status", "merging");

        broadcastManager.sendBroadcast(i);
        Log.e("delay arg", "" + delay);

        String cmd[];

        if (!post) {
            File f = new File(path + "/temp_post.m4a");

            if (f.exists())
                f.delete();

            if (delay > 0) {
                cmd = new String[]{"-i", path + "/first_a.m4a",
                        "-i", path + "/sec_a.m4a", "-filter_complex",
                        "[1:a]adelay=" + delay + "[a1];" +
                                "[0:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=mono,volume=1[a_0];"
                                + "[a1]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=mono,volume=1[a_1];" +
                                "[a_0][a_1]amerge=inputs=2[a]",
                        "-acodec", "aac", "-strict", "experimental", "-map", "[a]", "-ac", "1", path + "/temp_post.m4a"};
            } else {
                cmd = new String[]{"-i", path + "/first_a.m4a",
                        "-i", path + "/sec_a.m4a", "-filter_complex",
                        "[0:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=mono,volume=1[a_0];"
                                + "[1:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=mono,volume=1[a_1];" +
                                "[a_0][a_1]amerge=inputs=2[a]",
                        "-acodec", "aac", "-strict", "experimental", "-map", "[a]", "-ac", "1", path + "/temp_post.m4a"};
            }
        } else {
            prefManager.setCurUploading(folder);
            prefManager.setUploadPerc(-1);

            copyLogo();
            File f = new File(path + "/temp_post.wav");
            String pathStr = path + "/temp_post.wav";

            if (!f.exists()) {
                pathStr = path + "/temp_post.m4a";
            }

            if (delay > 0) {
                cmd = new String[]{"-i", path + "/first.mp4", "-i", path + "/sec.mp4", "-i", pathStr,
                        "-shortest",
                        "-y", "-filter_complex",
                        "[0:v]scale=400:ih*400/iw[temp1];" +
                                "[temp1]crop=200:400[left];" +
                                "[1:v]scale=400:ih*400/iw[temp2];" +
                                "[temp2]crop=200:400[right];" +
                                "[left][right]hstack[v];" +
                                "[2:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=mono,volume=1[a]",
                        "-acodec", "aac", "-strict", "experimental", "-vcodec",
                        "libx264", "-map", "[v]",
                        "-map","[a]", "-crf", "23",
                        "-f", "mp4",
                        path + "/temp_post.mp4"};
            } else {
                cmd = new String[]{"-i", path + "/first.mp4", "-i", path + "/sec.mp4", "-i", pathStr,
                        "-shortest",
                        "-y", "-filter_complex",
                        "[0:v]scale=400:ih*400/iw[temp1];" +
                                "[temp1]crop=200:400[left];" +
                                "[1:v]scale=400:ih*400/iw[temp2];" +
                                "[temp2]crop=200:400[right];" +
                                "[left][right]hstack[v];" +
                                "[2:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=mono,volume=1[a]",
                        "-acodec", "aac", "-strict", "experimental", "-vcodec",
                        "libx264", "-map", "[v]",
                        "-map","[a]", "-crf", "23",
                        "-f", "mp4",
                        path + "/temp_post.mp4"};
            }
        }

        FFmpeg ffmpeg = FFmpeg.getInstance(this);

        try {
            // to execute "ffmpeg -version" command you just need to pass "-version"
            ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {

                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onStart() {
                    isRunning = true;
                    Log.e("start", "conveting");

                    if (post) {
                        showNotification("Post " + title, "Your Jam '" + title + "' is being prepared.", "");
                    }
                }

                @Override
                public void onProgress(String message) {
                    Log.e("message", message);

                    Intent i = new Intent("local_stat");
                    i.putExtra("id", folder);
                    i.putExtra("status", "merging");

                    //broadcastManager.sendBroadcast(i);
                }

                @Override
                public void onFailure(String message) {
                    Log.e("error", message);
                    Intent i = new Intent("local_stat");
                    i.putExtra("id", folder);
                    i.putExtra("status", "failed_merge");

                    isRunning = false;

                    broadcastManager.sendBroadcast(i);
                    if (post) {
                        showNotification("Jam Post Failed", "Your Jam '" +
                                title + "' couldn't be posted.", "");
                        prefManager.setUploadPerc(-3);
                        prefManager.setCurUploading("");
                    } else {
                        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                        manager.cancelAll();
                    }
                }

                @Override
                public void onSuccess(String message) {
                    Log.e("done", message);

                    isRunning = false;
                    if (!post) {
                        Intent i = new Intent("local_stat");
                        i.putExtra("id", folder);
                        i.putExtra("status", "merged");

                        broadcastManager.sendBroadcast(i);
                    } else
                        uploadAll(path, title, tags, folder, false);
                }

                @Override
                public void onFinish() {
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            // Handle if FFmpeg is already running
            Log.e("Already running", "ffmpeg");
        }
    }

    public void mergeTwoFinal(final String path, final String folder, final int delay) {
        Intent i = new Intent("local_stat_third");
        i.putExtra("id", folder);
        i.putExtra("status", "merging");

        broadcastManager.sendBroadcast(i);

        String cmd[];
        if (delay > 0) {
            cmd = new String[]{"-i", path + "/temp_post.m4a",
                    "-i", path + "/third_a.m4a", "-filter_complex",
                    "[1:a]adelay=" + delay + "[a1];" +
                            "[0:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=mono,volume=1[a_0];"
                            + "[a1]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=mono,volume=1[a_1];" +
                            "[a_0][a_1]amerge=inputs=2[a]",
                    "-acodec", "aac", "-strict", "experimental", "-map", "[a]", "-ac", "1", path + "/third_post.m4a"};
        } else {
            cmd = new String[]{"-i", path + "/temp_post.m4a",
                    "-i", path + "/third_a.m4a", "-filter_complex",
                    "[0:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=mono,volume=1[a_0];"
                            + "[1:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=mono,volume=1[a_1];" +
                            "[a_0][a_1]amerge=inputs=2[a]",
                    "-acodec", "aac", "-strict", "experimental", "-map", "[a]", "-ac", "1", path + "/third_post.m4a"};
        }

        FFmpeg ffmpeg = FFmpeg.getInstance(this);

        try {
            // to execute "ffmpeg -version" command you just need to pass "-version"
            ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {

                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onStart() {
                    isRunning = true;
                    Log.e("start", "conveting");
                }

                @Override
                public void onProgress(String message) {
                    Log.e("message", message);

                    Intent i = new Intent("local_stat_third");
                    i.putExtra("id", folder);
                    i.putExtra("status", "merging");

                    //broadcastManager.sendBroadcast(i);
                }

                @Override
                public void onFailure(String message) {
                    Log.e("error", message);
                    Intent i = new Intent("local_stat_third");
                    i.putExtra("id", folder);
                    i.putExtra("status", "failed_merge");

                    isRunning = false;

                    broadcastManager.sendBroadcast(i);
                    NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    manager.cancelAll();
                }

                @Override
                public void onSuccess(String message) {
                    Log.e("done", message);

                    isRunning = false;
                    Intent i = new Intent("local_stat_third");
                    i.putExtra("id", folder);
                    i.putExtra("status", "merged");

                    broadcastManager.sendBroadcast(i);
                }

                @Override
                public void onFinish() {
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            // Handle if FFmpeg is already running
            Log.e("Already running", "ffmpeg");
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public static boolean deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            if (files == null) {
                return true;
            }
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        return (path.delete());
    }

    private void copyLogo() {
        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.logo);

        try {
            File file = new File(PATH, "logo.png");
            FileOutputStream outStream = new FileOutputStream(file);
            bm.compress(Bitmap.CompressFormat.PNG, 100, outStream);
            outStream.flush();
            outStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}