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

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by pR0 on 18-10-2016.
 */
public class TransferUtilityService extends Service implements RestClient.ResultReadyCallback {

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
