package edu.uml.cs.isense.riverwalk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.content.CursorLoader;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import edu.uml.cs.isense.comm.API;
import edu.uml.cs.isense.credentials.CredentialManager;
import edu.uml.cs.isense.dfm.DataFieldManager;
import edu.uml.cs.isense.proj.ProjectManager;
import edu.uml.cs.isense.queue.QDataSet.Type;
import edu.uml.cs.isense.queue.QueueLayout;
import edu.uml.cs.isense.queue.UploadQueue;
import edu.uml.cs.isense.riverwalk.dialogs.About;
import edu.uml.cs.isense.riverwalk.dialogs.CameraPreview;
import edu.uml.cs.isense.riverwalk.dialogs.Description;
import edu.uml.cs.isense.riverwalk.dialogs.Help;
import edu.uml.cs.isense.riverwalk.dialogs.NoGps;
import edu.uml.cs.isense.supplements.OrientationManager;
import edu.uml.cs.isense.waffle.Waffle;

public class Pictures extends AppCompatActivity implements LocationListener {
	private static final int CAMERA_PIC_REQUESTED = 101;
	private static final int LOGIN_REQUESTED = 102;
	private static final int NO_GPS_REQUESTED = 103;
	private static final int PROJECT_REQUESTED = 104;
	private static final int QUEUE_UPLOAD_REQUESTED = 105;
	private static final int DESCRIPTION_REQUESTED = 106;
	private static final int SELECT_PICTURE_REQUESTED = 107;

	private final String DEFAULT_PROJ_DEV = "248";
	private final String DEFAULT_PROJ_LIVE = "259";

	private MediaPlayer mMediaPlayer;

	public static boolean continuous = false;
	public static int continuousInterval = 1;
	private boolean recording = false;

	private static final int TIMER_LOOP = 1000;

	private LocationManager mLocationManager;
	private LocationManager mRoughLocManager;
	private Location loc;

	private Uri imageUri;

	public static API api;
	public static UploadQueue uq;

	private static boolean showGpsDialog = true;

	private EditText name;
	private TextView projectLabel;
	private Timer mTimer = null;
	private Handler mHandler;
	private TextView latLong;
	private TextView queueCount;
	private static int waitingCounter = 0;

	/* Action Bar */
	private static int actionBarTapCount = 0;
	private static boolean useDev = false;

	public static Context mContext;

	private Waffle w;
	private File picture;
	public static Button takePicture;
	public static Button addPicture;
	static boolean useMenu = true;

	// private ProgressDialog dia;
	private DataFieldManager dfm;

	public static final int MEDIA_TYPE_IMAGE = 1;
	private static Camera mCamera;
	private CameraPreview mPreview;
	private FrameLayout preview;

    Boolean validPicture = false;

	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mContext = this;

		useMenu = true;

		mMediaPlayer = MediaPlayer.create(this, R.raw.beep);

		w = new Waffle(mContext);

		api = API.getInstance();
		api.useDev(useDev);

		CredentialManager.login(mContext, api);

		uq = new UploadQueue("generalpictures", mContext, api);

		String projId = ProjectManager.getProject(mContext);
		if (projId.equals("-1")) {
			setDefaultProject();
		} else {
			projectLabel = (TextView) findViewById(R.id.projectLabel);
			projectLabel.setText(getResources().getString(R.string.projectLabel)
					+ projId);
		}

		//Initialize DataFieldManager object
		initDfm();

		mHandler = new Handler();

		name = (EditText) findViewById(R.id.name);

		latLong = (TextView) findViewById(R.id.myLocation);
		queueCount = (TextView) findViewById(R.id.queueCountLabel);

		preview = (FrameLayout) findViewById(R.id.camera_preview);
		preview.getLayoutParams().height = 0;

		addPicture = (Button) findViewById(R.id.addPicture);
		takePicture = (Button) findViewById(R.id.takePicture);

		if (continuous == true) {
			takePicture.setText(R.string.takePicContinuous);
			addPicture.setVisibility(View.GONE);
		}

		takePicture.setOnClickListener(new OnClickListener() {

			// Push take picture button
			@SuppressLint("NewApi")
			@Override
			public void onClick(View v) {
				// Check that a group name was entered, and a project was
				// selected
				if (name.getText().length() == 0) {
					name.setError("Enter a name");
					return;
				} else {
					name.setError(null);
				}

				String projectNum = ProjectManager.getProject(mContext);
				if (projectNum.equals("-1")) {
					w.make("Please select an project first.",
							Waffle.LENGTH_LONG, Waffle.IMAGE_X);
					return;
				}

				// take a single picture when continuous mode is not active
				if (continuous == false) {
					String state = Environment.getExternalStorageState();
					if (Environment.MEDIA_MOUNTED.equals(state)) {

						ContentValues values = new ContentValues();

						imageUri = getContentResolver().insert(
								MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
								values);

						Intent intent = new Intent(
								MediaStore.ACTION_IMAGE_CAPTURE);
						intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
						intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
						startActivityForResult(intent, CAMERA_PIC_REQUESTED);

					} else {
						w.make("Cannot write to external storage.",
								Waffle.LENGTH_LONG, Waffle.IMAGE_X);
					}

					OrientationManager.enableRotation(Pictures.this);

				// Continuously take pictures
				} else if (continuous == true) {

					mMediaPlayer.setLooping(false);
					mMediaPlayer.start();

					if (recording == false) {
						// disable menu
						useMenu = false;
						if (android.os.Build.VERSION.SDK_INT >= 11)
							invalidateOptionsMenu();
						int dps = 176;

						final float scale = getResources().getDisplayMetrics().density;
						int pixels = (int) (dps * scale + 0.5f);
						preview.getLayoutParams().height = pixels;

						takePicture.setBackgroundResource(R.drawable.button_rsense_green);
						takePicture.setTextColor(0xFF000000);
						takePicture.setText("Recording Press to Stop");

						recording = true;

						safeCameraOpen(0);

						preview.setVisibility(View.VISIBLE);
						mPreview = new CameraPreview(mContext, mCamera);

						if (mPreview.getHolder() != null) {
							Log.d("Main", "mPreview is " + mPreview.getHolder());
						}

						preview.addView(mPreview);

						new continuouslytakephotos().execute();

						// Stop continuously taking pictures
					} else {
						Pictures.takePicture.setText(R.string.takePicContinuous);
						Pictures.takePicture.setTextColor(0xFF0066FF);
						Pictures.takePicture.setBackgroundResource(R.drawable.button_rsense);
						recording = false;
					}
				}
			}
		});


		/* Add a Picture to queue from gallery */
		addPicture.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				if (name.getText().length() == 0) {
					name.setError("Enter a name");
					return;
				} else {
					name.setError(null);
				}

				Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
//              intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(
						Intent.createChooser(intent, "Select Picture"),
						SELECT_PICTURE_REQUESTED);

			}

		});

	}

	/**
	 * Here we store the file url as it will be null after returning from camera
	 * app
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
	    super.onSaveInstanceState(outState);

	    // save file url in bundle as it will be null on scren orientation
	    // changes
	    outState.putParcelable("image_uri", imageUri);
	}

	/*
	 * Here we restore the fileUri again
	 */
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
	    super.onRestoreInstanceState(savedInstanceState);

	    // get the file url
	    imageUri = savedInstanceState.getParcelable("image_uri");
	}


	private void setDefaultProject(){
		/*if no project set or using a default project set the correct default project based on live or dev mode*/
	     if (api.isUsingDevMode() == true){
	     	ProjectManager.setProject(mContext, DEFAULT_PROJ_DEV);
	     } else if (api.isUsingDevMode() == false) {
		     ProjectManager.setProject(mContext, DEFAULT_PROJ_LIVE);
	     }

		projectLabel = (TextView) findViewById(R.id.projectLabel);
		String project = ProjectManager.getProject(mContext);
		projectLabel.setText(getResources().getString(R.string.projectLabel)
				+ project);
	}

	// continuously take pictures in AsyncTask (a seperate thread)
	private class continuouslytakephotos extends AsyncTask<Void, Void, Boolean> {
		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			OrientationManager.disableRotation(Pictures.this);
		}

		@Override
		protected Boolean doInBackground(Void... params) {

			// this method will be running on background thread so don't update
			// UI from here
			// do your long running http tasks here,you dont want to pass
			// argument and u can access the parent class' variable url over
			// here

			while (recording) {

				// sleep for interval as long as recording is equal to true
				for (int i = 0; i < continuousInterval && recording == true; i++) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						Log.e(getString(R.string.app_name),
								"failed to sleep while continuously taking pictures");
						e.printStackTrace();
					}
				}

				// do not take one last picture when user pushes to stop
				// recording
				if (recording == false) {
					return null;
				}

				String state = Environment.getExternalStorageState();
				if (Environment.MEDIA_MOUNTED.equals(state)) {


                    try {
                        mCamera.takePicture(null, null, mPicture); // takes
                                                                    // a
                                                                    // picture
                    } catch (Exception e) {
                        Log.d("CameraMain", "Failed taking picture");
                        e.printStackTrace();
                    }

                    if (picture != null) {
                        validPicture = true;
                    } else {
                        validPicture = false;
                    }

                    if (validPicture) {
                        String projId = ProjectManager.getProject(mContext);

                        String dataSetName;
                        if ( Description.photo_description == null) {
                            dataSetName = name.getText().toString();
                        } else {
                            dataSetName = name.getText().toString() + " Description: " + Description.photo_description;
                        }

                        String description = DateFormat.getDateTimeInstance().format(new Date());

                        JSONArray dataPoint = dfm.recordDataPoint();
                        JSONArray dataSet = new JSONArray();
                        dataSet.put(dataPoint);

                        //add image and data to upload queue
                        uq.addToQueue(dataSetName, description, Type.BOTH, dataSet, picture, projId, null, false);
                        uq.buildQueueFromFile();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                w.make("Picture saved!", Waffle.LENGTH_SHORT,
                                        Waffle.IMAGE_CHECK);
                                queueCount.setText(getResources().getString(
                                        R.string.queueCount)
                                        + uq.queueSize());
                                mCamera.stopPreview();
                                mCamera.startPreview();
                            }
                        });
                    }

				} else {
					return null;
				}
			}
			return null;
		}

		@SuppressLint("NewApi")
		@Override
		protected void onPostExecute(Boolean result) {// this method will be
														// running on UI thread
			super.onPostExecute(result);
			// enable menu
			useMenu = true;
			if (android.os.Build.VERSION.SDK_INT >= 11)
				invalidateOptionsMenu();

			Pictures.takePicture.setText(R.string.takePicContinuous);
			Pictures.takePicture.setTextColor(0xFF0066FF);
			Pictures.takePicture.setBackgroundResource(R.drawable.button_rsense);

			OrientationManager.enableRotation(Pictures.this);
			preview.removeView(mPreview);
			preview.setVisibility(View.GONE);

			recording = false;

			Log.d("CameraMain",
					"Camera in onPostExecute is:" + mCamera.toString());
			mCamera.release();
			mCamera = null;

			OrientationManager.enableRotation(Pictures.this);
		}
	}

	private boolean safeCameraOpen(int id) {
		boolean qOpened = false;
		try {
			if (mCamera != null) {
				mCamera.stopPreview();
				mCamera.release();
				mCamera = null;
			}

			Log.d("CameraMain",
					"Number of cameras " + Camera.getNumberOfCameras());
			Camera.CameraInfo c = new Camera.CameraInfo();

			Log.d("CameraMain", "Camera info cameras " + c.toString());
			mCamera = Camera.open(id);

			Display display = getWindowManager().getDefaultDisplay();
			int rotation = display.getRotation();

			if (rotation == Surface.ROTATION_0) {
				mCamera.setDisplayOrientation(90);
			} else if (rotation == Surface.ROTATION_90) {
				mCamera.setDisplayOrientation(0);
			} else if (rotation == Surface.ROTATION_270) {
				mCamera.setDisplayOrientation(180);
			}

			Log.d("CameraMain", "Camera is: " + mCamera.toString());
			qOpened = (mCamera != null);
		} catch (Exception e) {
			Log.e(getString(R.string.app_name), "failed to open Camera");
			e.printStackTrace();
		}

		return qOpened;
	}

	// called automatically after picture is taken to save picture data
	private final PictureCallback mPicture = new PictureCallback() {

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {

			picture = getOutputMediaFile(MEDIA_TYPE_IMAGE);

			if (picture == null) {
				Log.d("CameraMain", "picture is null");
				return;
			}

			try {
				FileOutputStream fos = new FileOutputStream(picture);
				fos.write(data);
				fos.close();
			} catch (IOException e) {
				Log.e("onPictureTaken in main", "failed to save picture");
			}

		}
	};

	/** Create a File for saving an image or video */
	@SuppressLint("SimpleDateFormat")
	private static File getOutputMediaFile(int type) {
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		File mediaStorageDir = new File(
				Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
				"MyCameraApp");
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				Log.d("MyCameraApp", "failed to create directory");
				return null;
			}
		}

		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
				.format(new Date());
		File mediaFile;
		if (type == MEDIA_TYPE_IMAGE) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator
					+ "IMG_" + timeStamp + ".jpg");
		} else {
			return null;
		}

		return mediaFile;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	/**
	 * Turns the action bar menu on and off.
	 *
	 * @return Whether or not the menu was prepared successfully.
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {

		if (!useMenu) {
			menu.getItem(0).setEnabled(false);
			menu.getItem(1).setEnabled(false);
			menu.getItem(2).setEnabled(false);
			menu.getItem(3).setEnabled(false);
			menu.getItem(4).setEnabled(false);

		}

		else {
			menu.getItem(0).setEnabled(true);
			menu.getItem(1).setEnabled(true);
			menu.getItem(2).setEnabled(true);
			menu.getItem(3).setEnabled(true);
			menu.getItem(4).setEnabled(true);


		}
		return true;
	}

	// menu
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.MENU_ITEM_UPLOAD:
			manageUploadQueue();
			return true;

		case R.id.MENU_ITEM_BROWSE:
			Intent iproject = new Intent(mContext,
					ProjectManager.class);
			iproject.putExtra("constrictFields", false);
			startActivityForResult(iproject, PROJECT_REQUESTED);
			return true;

		case R.id.MENU_ITEM_LOGIN:
			startActivityForResult(new Intent(getApplicationContext(),
					CredentialManager.class), LOGIN_REQUESTED);
			return true;

		case R.id.MENU_ITEM_CONTINUOUS:
            startActivity(new Intent(this, Continuous.class));
            return true;

		case R.id.MENU_ITEM_ABOUT:
			Intent about = new Intent(getApplicationContext(), About.class);
			startActivity(about);
			return true;

		case R.id.MENU_ITEM_HELP:
			Intent help = new Intent(getApplicationContext(), Help.class);
			startActivity(help);
			return true;

		case android.R.id.home:
			CountDownTimer cdt = null;

			// Give user 10 seconds to switch dev/prod mode
			if (actionBarTapCount == 0) {
				cdt = new CountDownTimer(5000, 5000) {
					@Override
					public void onTick(long millisUntilFinished) {
					}

					@Override
					public void onFinish() {
						actionBarTapCount = 0;
					}
				}.start();
			}

			String other = (useDev) ? "production" : "dev";

			switch (++actionBarTapCount) {
			case 5:
				w.make(getResources().getString(R.string.two_more_taps) + other
						+ getResources().getString(R.string.mode_type));
				break;
			case 6:
				w.make(getResources().getString(R.string.one_more_tap) + other
						+ getResources().getString(R.string.mode_type));
				break;
			case 7:
				w.make(getResources().getString(R.string.now_in_mode) + other
						+ getResources().getString(R.string.mode_type));
				useDev = !useDev;

				if (cdt != null)
					cdt.cancel();

				if (api.getCurrentUser() != null) {
					Runnable r = new Runnable() {
						@Override
						public void run() {
							api.deleteSession();
							api.useDev(useDev);
							runOnUiThread(new Runnable(){
							    @Override
								public void run(){
							    	setDefaultProject();
							    }
							});
						}
					};
					new Thread(r).start();
				} else {
					api.useDev(useDev);
					setDefaultProject();
				}
				CredentialManager.login(this, api);
				actionBarTapCount = 0;



				break;
			}

			return true;

		default:
			return false;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (recording == true) {
			if (mCamera == null) {
				safeCameraOpen(0);
			}
			preview.setVisibility(View.VISIBLE);
			mPreview = new CameraPreview(mContext, mCamera);
			preview.addView(mPreview);

		}

		CredentialManager.login(this, api);

		// Rebuilds uploadQueue from saved info
		uq.buildQueueFromFile();
		queueCount.setText(getResources().getString(R.string.queueCount)
				+ uq.queueSize());
	}

	// uploads the data if logged in and queue is not empty
	private void manageUploadQueue() {

		if (uq.emptyQueue()) {
			w.make("No data to upload.", Waffle.IMAGE_X);
			return;
		}
		Intent i = new Intent().setClass(mContext, QueueLayout.class);
		i.putExtra(QueueLayout.PARENT_NAME, uq.getParentName());
		startActivityForResult(i, QUEUE_UPLOAD_REQUESTED);

	}

	// onStart initialize location manager
	@Override
	protected void onStart() {
		super.onStart();

		initLocManager();

		if (mTimer == null)
			waitingForGPS();
	}

	public static File convertImageUriToFile(Uri imageUri, Context c) {
			String[] proj = { MediaStore.Images.Media.DATA,
					MediaStore.Images.Media._ID };

			String selection = null;
			String[] selectionArgs = null;
			String sortOrder = null;

            CursorLoader cursorLoader = new CursorLoader(c, imageUri,
                    proj, selection, selectionArgs, sortOrder);

            Cursor cursor = cursorLoader.loadInBackground();

			int file_ColumnIndex = cursor
					.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			if (cursor.moveToFirst()) {
                File file = null;
                try {
                    file = new File(cursor.getString(file_ColumnIndex));
                } catch (Exception e) {
                    System.out.println(e.toString());
                }
                return file;
			}
			return null;
	}
	/**
	 * Sets up data field manager
	 */
	private void initDfm() {
		//get projId from saved prefs
		String projectInput = ProjectManager.getProject(mContext);

		dfm = new DataFieldManager(Integer.parseInt(projectInput), api,
				mContext);

		/*Enable fields that app is capable of recording*/
		LinkedList<String> acceptedFields = new LinkedList<String>();
		acceptedFields.add(0, "Time");
		acceptedFields.add(1, "Latitude");
		acceptedFields.add(2, "Longitude");
		dfm.setEnabledFields(acceptedFields);

		/*Checks to see what fields entered above exist on this particular project*/
		dfm.setProjID(Integer.parseInt(projectInput));
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) { // passes
																					// in
																					// a
																					// request
																					// code
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == CAMERA_PIC_REQUESTED) { // request to takes picture

			if (resultCode == RESULT_OK) {
				picture = convertImageUriToFile(imageUri, mContext);

				Intent iDesc = new Intent(Pictures.this, Description.class);
				startActivityForResult(iDesc, DESCRIPTION_REQUESTED);

			}

		} else if (requestCode == PROJECT_REQUESTED) { // obtains data fields
															// from project on
															// isense
			if (resultCode == Activity.RESULT_OK) {
				String projIdString = ProjectManager.getProject(mContext);

				projectLabel.setText(getResources().getString(
						R.string.projectLabel)
						+ projIdString);

				dfm.setProjID(Integer.parseInt(projIdString));

				dfm.getOrder();
			}
		} else if (requestCode == LOGIN_REQUESTED) {

		} else if (requestCode == NO_GPS_REQUESTED) { // asks the user if they
														// would like to enable
														// gps
			showGpsDialog = true;
			if (resultCode == RESULT_OK) {
				startActivity(new Intent(
						Settings.ACTION_LOCATION_SOURCE_SETTINGS));
			}

		} else if (requestCode == DESCRIPTION_REQUESTED) {
			uq.buildQueueFromFile();
			queueCount.setText(getResources().getString(R.string.queueCount)
					+ uq.queueSize());

			/*Records current time stamp and location data*/
			JSONArray dataPoint = dfm.recordDataPoint();
			JSONArray dataSet = new JSONArray();
			dataSet.put(dataPoint);
			Log.e("Pictures", dataSet.toString());

			String projId = ProjectManager.getProject(mContext);

			String dataSetName = name.getText().toString() + " " + Description.photo_description;
			String description = DateFormat.getDateTimeInstance().format(new Date());

			//add image and data to upload queue
			uq.addToQueue(dataSetName, description, Type.BOTH, dataSet, picture, projId, null, false);

		} else if (requestCode == SELECT_PICTURE_REQUESTED) {
			if (resultCode == Activity.RESULT_OK) {

                Uri selectedImageUri = data.getData();

                /*gets data from picture */
                JSONArray dataPoint = dfm.getDataFromPic(selectedImageUri);
                JSONArray dataSet = new JSONArray();
                dataSet.put(dataPoint);

                /* turns image uri to file to be uploaded */
        		File picture = convertImageUriToFile(selectedImageUri, mContext);

    			String projId = ProjectManager.getProject(mContext);

    			/* add picture and data to queue */
    			String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());
    			String dataSetName = name.getText().toString();
    			String description = currentDateTimeString;


    			//add image and data to upload queue
				uq.addToQueue(dataSetName, description, Type.BOTH, dataSet, picture, projId, null, false);

			}
		}
	}

	@Override
	public void onLocationChanged(Location location) {
		dfm.updateLoc(location);
	}

	@Override
	public void onProviderDisabled(String provider) {
	}

	@Override
	public void onProviderEnabled(String provider) {

	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	// initialize location listener to get a pair of coordinates
	private void initLocManager() {
		Criteria c = new Criteria();
		c.setAccuracy(Criteria.ACCURACY_FINE);

		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		mRoughLocManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

		if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
				&& mRoughLocManager
						.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {

			mLocationManager.requestLocationUpdates(
					mLocationManager.getBestProvider(c, true), 0, 0, Pictures.this);
			mRoughLocManager.requestLocationUpdates(
					LocationManager.NETWORK_PROVIDER, 0, 0, Pictures.this);

		} else {
			if (showGpsDialog) {
				Intent iNoGps = new Intent(mContext, NoGps.class);
				startActivityForResult(iNoGps, NO_GPS_REQUESTED);
				showGpsDialog = false;
			}
		}

		loc = new Location(mLocationManager.getBestProvider(c, true));
	}

	@Override
	protected void onStop() {
		super.onStop();

		if (mLocationManager != null)
			mLocationManager.removeUpdates(Pictures.this);

		if (mRoughLocManager != null)
			mRoughLocManager.removeUpdates(Pictures.this);

		if (mTimer != null)
			mTimer.cancel();
		mTimer = null;
	}

	// no gps signal
	private void waitingForGPS() {
		mTimer = new Timer();
		mTimer.scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {
				mHandler.post(new Runnable() {
					@Override
					public void run() {

						if (loc.getLatitude() != 0)
							latLong.setText("Lat: " + loc.getLatitude()
									+ "\nLong: " + loc.getLongitude());
						else {
							switch (waitingCounter % 4) {
								case (0):
									latLong.setText(R.string.noLocation0);
									break;
								case (1):
									latLong.setText(R.string.noLocation1);
									break;
								case (2):
									latLong.setText(R.string.noLocation2);
									break;
								case (3):
									latLong.setText(R.string.noLocation3);
									break;
							}
							waitingCounter++;
						}
					}
				});
			}
		}, 0, TIMER_LOOP);
	}

	@Override
	protected void onPause() {
		if (recording == true) {
			recording = false;
			preview.removeView(mPreview);
			preview.setVisibility(View.INVISIBLE);
		}

		super.onPause();
	}

	@SuppressLint("UseValueOf")
	private Float convertToDegree(String stringDMS){
		 Float result = null;
		 String[] DMS = stringDMS.split(",", 3);

		 String[] stringD = DMS[0].split("/", 2);
		    Double D0 = new Double(stringD[0]);
		    Double D1 = new Double(stringD[1]);
		    Double FloatD = D0/D1;

		 String[] stringM = DMS[1].split("/", 2);
		 Double M0 = new Double(stringM[0]);
		 Double M1 = new Double(stringM[1]);
		 Double FloatM = M0/M1;

		 String[] stringS = DMS[2].split("/", 2);
		 Double S0 = new Double(stringS[0]);
		 Double S1 = new Double(stringS[1]);
		 Double FloatS = S0/S1;

		    result = new Float(FloatD + (FloatM/60) + (FloatS/3600));

		 return result;
		};


}