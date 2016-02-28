/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nutrition.android;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.client.result.ResultParser;
import com.nutrition.android.camera.CameraManager;
import com.nutrition.android.result.ResultHandler;

/**
 * This activity opens the camera and does the actual scanning on a background
 * thread. It draws a viewfinder to help the user place the barcode correctly,
 * shows feedback as the image processing is happening, and then overlays the
 * results when a scan is successful.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 * @author Issame
 */
public final class CaptureActivity extends Activity implements SurfaceHolder.Callback {

	private static final String TAG = CaptureActivity.class.getSimpleName();

	private static final long BULK_MODE_SCAN_DELAY_MS = 1000L;

	public static final int HISTORY_REQUEST_CODE = 0x0000bacc;

	private CameraManager cameraManager;
	private CaptureActivityHandler handler;
	private Result savedResultToShow;
	private ViewfinderView viewfinderView;
	private TextView statusView;
	private View resultView;
	private Result lastResult;
	private boolean hasSurface;
	private Collection<BarcodeFormat> decodeFormats;
	private Map<DecodeHintType, ?> decodeHints;
	private String characterSet;
	private BeepManager beepManager;
	private AmbientLightManager ambientLightManager;

	ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	CameraManager getCameraManager() {
		return cameraManager;
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		// StrictMode.ThreadPolicy policy = new
		// StrictMode.ThreadPolicy.Builder().permitAll().build();
		// StrictMode.setThreadPolicy(policy);

		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.capture);

		hasSurface = false;
		beepManager = new BeepManager(this);
		ambientLightManager = new AmbientLightManager(this);

		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
	}

	@Override
	protected void onResume() {
		super.onResume();

		// CameraManager must be initialized here, not in onCreate(). This is
		// necessary because we don't
		// want to open the camera driver and measure the screen size if we're
		// going to show the help on
		// first launch. That led to bugs where the scanning rectangle was the
		// wrong size and partially
		// off screen.
		cameraManager = new CameraManager(getApplication());

		viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
		viewfinderView.setCameraManager(cameraManager);

		resultView = findViewById(R.id.result_view);
		statusView = (TextView) findViewById(R.id.status_view);

		handler = null;
		lastResult = null;

		resetStatusView();

		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			// The activity was paused but not stopped, so the surface still
			// exists. Therefore
			// surfaceCreated() won't be called, so init the camera here.
			initCamera(surfaceHolder);
		} else {
			// Install the callback and wait for surfaceCreated() to init the
			// camera.
			surfaceHolder.addCallback(this);
		}

		beepManager.updatePrefs();
		ambientLightManager.start(cameraManager);

		decodeFormats = null;
		characterSet = null;

	}

	@Override
	protected void onPause() {
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		ambientLightManager.stop();
		cameraManager.closeDriver();
		if (!hasSurface) {
			SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
			SurfaceHolder surfaceHolder = surfaceView.getHolder();
			surfaceHolder.removeCallback(this);
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			if(viewfinderView.getVisibility() == View.VISIBLE){
				finish();
			} else {
				restartPreviewAfterDelay(0L);
				return true;
			}
		case KeyEvent.KEYCODE_FOCUS:
		case KeyEvent.KEYCODE_CAMERA:
			// Handle these events so they don't launch the Camera app
			return true;
			// Use volume up/down to turn on light
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			cameraManager.setTorch(false);
			return true;
		case KeyEvent.KEYCODE_VOLUME_UP:
			cameraManager.setTorch(true);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (resultCode == RESULT_OK) {
			if (requestCode == HISTORY_REQUEST_CODE) {
			}
		}
	}

	private void decodeOrStoreSavedBitmap(Bitmap bitmap, Result result) {
		// Bitmap isn't used yet -- will be used soon
		if (handler == null) {
			savedResultToShow = result;
		} else {
			if (result != null) {
				savedResultToShow = result;
			}
			if (savedResultToShow != null) {
				Message message = Message.obtain(handler, R.id.decode_succeeded, savedResultToShow);
				handler.sendMessage(message);
			}
			savedResultToShow = null;
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (holder == null) {
			Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
		}
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

	}

	/**
	 * A valid barcode has been found, so give an indication of success and show
	 * the results.
	 * 
	 * @param rawResult
	 *            The contents of the barcode.
	 * @param scaleFactor
	 *            amount by which thumbnail was scaled
	 * @param barcode
	 *            A greyscale bitmap of the camera data which was decoded.
	 */
	public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
		lastResult = rawResult;
		// ResultHandler resultHandler =
		// ResultHandlerFactory.makeResultHandler(this, rawResult);
		ResultHandler resultHandler = new ResultHandler(this, ResultParser.parseResult(rawResult));

		boolean fromLiveScan = barcode != null;
		if (fromLiveScan) {
			// Then not from history, so beep/vibrate and we have an image to
			// draw on
			beepManager.playBeepSoundAndVibrate();
			//drawResultPoints(barcode, scaleFactor, rawResult);
		}

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (fromLiveScan && prefs.getBoolean(PreferencesSettings.KEY_BULK_MODE, false)) {
			Toast.makeText(getApplicationContext(), getResources().getString(R.string.msg_bulk_mode_scanned) + " (" + rawResult.getText() + ')', Toast.LENGTH_SHORT).show();
			// Wait a moment or else it will scan the same barcode continuously
			// about 3 times
			restartPreviewAfterDelay(BULK_MODE_SCAN_DELAY_MS);
		} else {
			handleDecodeInternally(rawResult, resultHandler, barcode);
		}
	}

	// Put up our own UI for how to handle the decoded contents.
	private void handleDecodeInternally(Result rawResult, ResultHandler resultHandler, Bitmap barcode) {

		statusView.setVisibility(View.GONE);
		viewfinderView.setVisibility(View.GONE);
		resultView.setVisibility(View.VISIBLE);
		resetResultView();
		
		CharSequence displayContents = resultHandler.getDisplayContents();

		Nutrition nutrition_elements = null;
		try {
			AsyncTask<String, String, Nutrition> task = new UtilTask().execute(displayContents.toString(), "");
			if (task != null) {
				nutrition_elements = task.get();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			return;
		} catch (ExecutionException e) {
			e.printStackTrace();
			return;
		}
		

		if(nutrition_elements != null){
			
			String error = nutrition_elements.getError();
			
			if("IOException".equals(error)){
				TextView info = (TextView) findViewById(R.id.information_text_view);
				info.setText(getString(R.string.msg_network_error));
				return;
			} else if("ParseException".equals(error)){
				ImageView barcodeImageView = (ImageView) findViewById(R.id.barcode_image_view);
				barcodeImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.icon));
				
				TextView info = (TextView) findViewById(R.id.information_text_view);
				info.setText(getString(R.string.msg_parse_error));
				return;
			} else if("NotFoundProduct".equals(error)){
				ImageView barcodeImageView = (ImageView) findViewById(R.id.barcode_image_view);
				barcodeImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.icon));
				
				TextView info = (TextView) findViewById(R.id.information_text_view);
				info.setText(getString(R.string.msg_not_found_product));
				return;
			} else if("EmptyProduct".equals(error)){
				ImageView barcodeImageView = (ImageView) findViewById(R.id.barcode_image_view);
				barcodeImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.icon));
				
				TextView info = (TextView) findViewById(R.id.information_text_view);
				info.setText(getString(R.string.msg_empty_product));
				return;
			} else {
				
				ImageView barcodeImageView = (ImageView) findViewById(R.id.barcode_image_view);
				Bitmap image = nutrition_elements.getImage();
				if (image == null) {
					barcodeImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.icon));
				} else {
					barcodeImageView.setImageBitmap(nutrition_elements.getImage());
				}

				TextView contentsTextView = (TextView) findViewById(R.id.product_name_text_view);
				TextView info = (TextView) findViewById(R.id.information_text_view);
				String productName = nutrition_elements.getProduct_name();
				if (productName != null && !"".equals(productName)) {
					contentsTextView.setText(productName);
					int scaledSize = Math.max(22, 32 - displayContents.length() / 4);
					contentsTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize);
					
					String data_per = nutrition_elements.getData_per();
					info.setText(getString(R.string.msg_default_information)+" "+data_per);
				}

				TextView fatTextView = (TextView) findViewById(R.id.fat_text_view);
				TextView fatLabelView = (TextView) findViewById(R.id.fat_label_view);
				View fatLevelColor = (View) findViewById(R.id.fat_level_color);
				String fat_level = nutrition_elements.getFat_level();
				if (fat_level != null && !"".equals(fat_level)) {
					String fat_gramme = nutrition_elements.getFat_weight();
					fatLabelView.setText(getString(R.string.msg_default_fat));
					if ("low".equalsIgnoreCase(fat_level)) {
						fatTextView.setText(getString(R.string.low) + " (" + fat_gramme + ")");
						fatLevelColor.setBackground(getResources().getDrawable(R.drawable.green));
					}
					if ("moderate".equalsIgnoreCase(fat_level)) {
						fatTextView.setText(getString(R.string.moderate) + " (" + fat_gramme + ")");
						fatLevelColor.setBackground(getResources().getDrawable(R.drawable.orange));
					}
					if ("high".equalsIgnoreCase(fat_level)) {
						fatTextView.setText(getString(R.string.high) + " (" + fat_gramme + ")");
						fatLevelColor.setBackground(getResources().getDrawable(R.drawable.red));
					}
				}

				TextView saturatedFatTextView = (TextView) findViewById(R.id.saturated_fat_text_view);
				TextView saturatedFatLabelView = (TextView) findViewById(R.id.saturated_fat_label_view);
				View saturatedFatLevelColor = (View) findViewById(R.id.saturated_fat_level_color);
				String saturated_fat_level = nutrition_elements.getSaturated_fat_level();
				if (saturated_fat_level != null && !"".equals(saturated_fat_level)) {
					String saturated_fat_gramme = nutrition_elements.getSaturated_fat_weight();
					saturatedFatLabelView.setText(getString(R.string.msg_default_saturated_fat));
					if ("low".equalsIgnoreCase(saturated_fat_level)) {
						saturatedFatTextView.setText(getString(R.string.low) + " (" + saturated_fat_gramme + ")");
						saturatedFatLevelColor.setBackground(getResources().getDrawable(R.drawable.green));
					}
					if ("moderate".equalsIgnoreCase(saturated_fat_level)) {
						saturatedFatTextView.setText(getString(R.string.moderate) + " (" + saturated_fat_gramme + ")");
						saturatedFatLevelColor.setBackground(getResources().getDrawable(R.drawable.orange));
					}
					if ("high".equalsIgnoreCase(saturated_fat_level)) {
						saturatedFatTextView.setText(getString(R.string.high) + " (" + saturated_fat_gramme + ")");
						saturatedFatLevelColor.setBackground(getResources().getDrawable(R.drawable.red));
					}
				}

				TextView sugarTextView = (TextView) findViewById(R.id.sugar_text_view);
				TextView sugarLabelView = (TextView) findViewById(R.id.sugar_label_view);
				View sugarLevelColor = (View) findViewById(R.id.sugar_level_color);
				String sugar_level = nutrition_elements.getSugars_level();
				if (sugar_level != null && !"".equals(sugar_level)) {
					String sugar_gramme = nutrition_elements.getSugars_weight();
					sugarLabelView.setText(getString(R.string.msg_default_sugar));
					if ("low".equalsIgnoreCase(sugar_level)) {
						sugarTextView.setText(getString(R.string.low) + " (" + sugar_gramme + ")");
						sugarLevelColor.setBackground(getResources().getDrawable(R.drawable.green));
					}
					if ("moderate".equalsIgnoreCase(sugar_level)) {
						sugarTextView.setText(getString(R.string.moderate) + " (" + sugar_gramme + ")");
						sugarLevelColor.setBackground(getResources().getDrawable(R.drawable.orange));
					}
					if ("high".equalsIgnoreCase(sugar_level)) {
						sugarTextView.setText(getString(R.string.high) + " (" + sugar_gramme + ")");
						sugarLevelColor.setBackground(getResources().getDrawable(R.drawable.red));
					}
				} 

				TextView saltTextView = (TextView) findViewById(R.id.salt_text_view);
				TextView saltLabelView = (TextView) findViewById(R.id.salt_label_view);
				View saltLevelColor = (View) findViewById(R.id.salt_level_color);
				String salt_level = nutrition_elements.getSalt_level();
				if (salt_level != null && !"".equals(salt_level)) {
					String salt_gramme = nutrition_elements.getSalt_weight();
					saltLabelView.setText(getString(R.string.msg_default_salt));
					if ("low".equalsIgnoreCase(salt_level)) {
						saltTextView.setText(getString(R.string.low) + " (" + salt_gramme + ")");
						saltLevelColor.setBackground(getResources().getDrawable(R.drawable.green));
					}
					if ("moderate".equalsIgnoreCase(salt_level)) {
						saltTextView.setText(getString(R.string.moderate) + " (" + salt_gramme + ")");
						saltLevelColor.setBackground(getResources().getDrawable(R.drawable.orange));
					}
					if ("high".equalsIgnoreCase(salt_level)) {
						saltTextView.setText(getString(R.string.high) + " (" + salt_gramme + ")");
						saltLevelColor.setBackground(getResources().getDrawable(R.drawable.red));
					}
				} 

				TextView energyTextView = (TextView) findViewById(R.id.energy_text_view);
				TextView energyLabelView = (TextView) findViewById(R.id.energy_label_view);
				String energy = nutrition_elements.getEnergy_100g();
				if (energy != null && !"".equals(energy) && !energy.contains("null")) {
					energyLabelView.setText(getString(R.string.msg_default_energy));
					energyTextView.setText(energy);
				}
				
			}
		}

	}

	private void resetResultView() {
		ImageView barcodeImageView = (ImageView) findViewById(R.id.barcode_image_view);
		barcodeImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.icon));
		
		TextView contentsTextView = (TextView) findViewById(R.id.product_name_text_view);
		contentsTextView.setText("");

		TextView fatTextView = (TextView) findViewById(R.id.fat_text_view);
		fatTextView.setText("");
		TextView fatLabelView = (TextView) findViewById(R.id.fat_label_view);
		fatLabelView.setText("");
		View fatLevelColor = (View) findViewById(R.id.fat_level_color);
		fatLevelColor.setBackground(null);
		
		TextView saturatedFatTextView = (TextView) findViewById(R.id.saturated_fat_text_view);
		TextView saturatedFatLabelView = (TextView) findViewById(R.id.saturated_fat_label_view);
		View saturatedFatLevelColor = (View) findViewById(R.id.saturated_fat_level_color);
		saturatedFatTextView.setText(""); 
		saturatedFatLabelView.setText(""); 
		saturatedFatLevelColor.setBackground(null);

		TextView sugarTextView = (TextView) findViewById(R.id.sugar_text_view);
		TextView sugarLabelView = (TextView) findViewById(R.id.sugar_label_view);
		View sugarLevelColor = (View) findViewById(R.id.sugar_level_color);				
		sugarTextView.setText("");
		sugarLabelView.setText("");
		sugarLevelColor.setBackground(null);

		TextView saltTextView = (TextView) findViewById(R.id.salt_text_view);
		TextView saltLabelView = (TextView) findViewById(R.id.salt_label_view);
		View saltLevelColor = (View) findViewById(R.id.salt_level_color);
		saltTextView.setText("");
		saltLabelView.setText("");
		saltLevelColor.setBackground(null);

		TextView energyTextView = (TextView) findViewById(R.id.energy_text_view);
		TextView energyLabelView = (TextView) findViewById(R.id.energy_label_view);
		energyTextView.setText("");
		energyLabelView.setText("");

		TextView InformationLabelView = (TextView) findViewById(R.id.information_text_view);
		InformationLabelView.setText("");
	}
	
	private void initCamera(SurfaceHolder surfaceHolder) {
		if (surfaceHolder == null) {
			throw new IllegalStateException("No SurfaceHolder provided");
		}
		if (cameraManager.isOpen()) {
			Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
			return;
		}
		try {
			cameraManager.openDriver(surfaceHolder);
			// Creating the handler starts the preview, which can also throw a
			// RuntimeException.
			if (handler == null) {
				handler = new CaptureActivityHandler(this, decodeFormats, decodeHints, characterSet, cameraManager);
			}
			decodeOrStoreSavedBitmap(null, null);
		} catch (IOException ioe) {
			Log.w(TAG, ioe);
			displayFrameworkBugMessageAndExit();
		} catch (RuntimeException e) {
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			Log.w(TAG, "Unexpected error initializing camera", e);
			displayFrameworkBugMessageAndExit();
		}
	}

	private void displayFrameworkBugMessageAndExit() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.app_name));
		builder.setMessage(getString(R.string.msg_camera_framework_bug));
		builder.show();
	}

	public void restartPreviewAfterDelay(long delayMS) {
		if (handler != null) {
			handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
		}
		resetStatusView();
	}

	private void resetStatusView() {
		resultView.setVisibility(View.GONE);
		statusView.setText(R.string.msg_default_status);
		statusView.setVisibility(View.VISIBLE);
		viewfinderView.setVisibility(View.VISIBLE);
		lastResult = null;
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    getMenuInflater().inflate(R.menu.capture, menu);
	    return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) 
	{
	   switch (item.getItemId()) 
	   {
	     case R.id.about:
			Toast.makeText(getApplicationContext(), getResources().getString(R.string.about_text), Toast.LENGTH_LONG).show();
	     default:
	        return super.onOptionsItemSelected(item);
	   }
	}
	
}
