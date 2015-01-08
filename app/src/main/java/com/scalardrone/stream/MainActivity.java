package com.scalardrone.stream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.rtsp.RtspClient;
import net.majorkernelpanic.streaming.video.VideoQuality;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends Activity implements
        RtspClient.Callback,
        Session.Callback,
        SurfaceHolder.Callback{

    public final static String TAG = MainActivity.class.getName();
    private Button mButtonSave;
	private Button mButtonVideo;
	private ImageButton mButtonStart;
	private ImageButton mButtonFlash;
	private ImageButton mButtonCamera;
	private ImageButton mButtonSettings;
	private RadioGroup mRadioGroup;
	private FrameLayout mLayoutVideoSettings;
	private FrameLayout mLayoutServerSettings;
	private SurfaceView mSurfaceView;
	private TextView mTextBitrate;
	private EditText mEditTextURI;
	private EditText mEditTextPassword;
	private EditText mEditTextUsername;
	private ProgressBar mProgressBar;
	private Session mSession;
	private RtspClient mClient;
    private  WifiLock lock;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);

        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        lock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LockTag");
        lock.acquire();


        mTextBitrate = (TextView) findViewById(R.id.bitrate);


        mSurfaceView = (SurfaceView) findViewById(R.id.surface);

        mSession = SessionBuilder.getInstance()
                .setCallback(this)
                .setSurfaceView(mSurfaceView)
                .setPreviewOrientation(0)
                .setContext(getApplicationContext())
                .setAudioEncoder(SessionBuilder.AUDIO_AAC)
                .setAudioQuality(new AudioQuality(16000, 128000))
                .setVideoEncoder(SessionBuilder.VIDEO_H264)
                .setVideoQuality(new VideoQuality(320,240,30,800000))
//                .setVideoQuality(new VideoQuality(640, 360, 30,2000000))
//                .setVideoQuality(new VideoQuality(320, 240, 15, 500000))
//                  .setVideoQuality(new VideoQuality(352,288,15,500000))
//                   .setVideoQuality(new VideoQuality(640, 480,30, 800000))
                .build();


        // Configures the RTSP client
		mClient = new RtspClient();
		mClient.setSession(mSession);
		mClient.setCallback(this);
        mSurfaceView.getHolder().addCallback(this);

        // init
        toggleStream();

    }

    @Override
	public void onDestroy(){
		super.onDestroy();
		mClient.release();
mSession.stop();
		mSession.release();
		mSurfaceView.getHolder().removeCallback(this);

        lock.release();
	}

    // Connects/disconnects to the RTSP server and starts/stops the stream
	public void toggleStream() {
//		mProgressBar.setVisibility(View.VISIBLE);
		if (!mClient.isStreaming()) {
			String ip,port,path;

			// We save the content user inputs in Shared Preferences
			SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
			Editor editor = mPrefs.edit();
;
            editor.putString("uri", "rtsp://192.168.1.35:1935/live/test.stream");
			editor.putString("password", "your_passwrod");
			editor.putString("username", "your_username");
			editor.commit();

			// We parse the URI written in the Editext
			Pattern uri = Pattern.compile("rtsp://(.+):(\\d*)/(.+)");
			Matcher m = uri.matcher("rtsp://192.168.1.35:1935/live/test.stream"); m.find();
			ip = m.group(1);
			port = m.group(2);
			path = m.group(3);
            path = "live/".concat(new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()));
Log.d(TAG, "******************** " + path + " **********************");

			mClient.setCredentials("your_username", "your_passwrod");
			mClient.setServerAddress(ip, Integer.parseInt(port));
			mClient.setStreamPath("/"+path);
			mClient.startStream();

		} else {
			// Stops the stream and disconnects from the RTSP server
			mClient.stopStream();
		}
	}

    @Override
	public void onBitrateUpdate(long bitrate) {
		mTextBitrate.setText(""+bitrate/1000+" kbps");
        Log.d(TAG, "onBitrateUpdte(" + bitrate+ ")");
	}

    private void logError(final String msg) {
        final String error = (msg == null) ? "Error unknown" : msg;
        // Displays a popup to report the eror to the user
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(msg).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {}
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
	public void onSessionError(int reason, int streamType, Exception e) {
//		mProgressBar.setVisibility(View.GONE);
		switch (reason) {
		case Session.ERROR_CAMERA_ALREADY_IN_USE:
			break;
		case Session.ERROR_INVALID_SURFACE:
			break;
		case Session.ERROR_STORAGE_NOT_READY:
			break;
		case Session.ERROR_CONFIGURATION_NOT_SUPPORTED:
			VideoQuality quality = mSession.getVideoTrack().getVideoQuality();
			logError("The following settings are not supported on this phone: "+
			quality.toString()+" "+
			"("+e.getMessage()+")");
			e.printStackTrace();
			return;
		case Session.ERROR_OTHER:
			break;
		}

		if (e != null) {
			logError(e.getMessage());
			e.printStackTrace();
		}
	}

    @Override
	public void onRtspUpdate(int message, Exception e) {
		switch (message) {
		case RtspClient.ERROR_CONNECTION_FAILED:
		case RtspClient.ERROR_WRONG_CREDENTIALS:

			logError(e.getMessage());
			e.printStackTrace();
			break;
		}
	}

    public void onPreviewStarted() {
        Log.d(TAG, "Preview started.");
    }

    @Override
    public void onSessionConfigured() {
        Log.d(TAG,"Preview configured.");
        // Once the stream is configured, you can get a SDP formated session description
        // that you can send to the receiver of the stream.
        // For example, to receive the stream in VLC, store the session description in a .sdp file
        // and open it with VLC while streming.
        Log.d(TAG, mSession.getSessionDescription());
        mSession.start();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // Starts the preview of the Camera
        mSession.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Stops the streaming session
        mSession.stop();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }


    @Override
    public void onSessionStarted() {

    }

    @Override
    public void onSessionStopped() {

    }


}
