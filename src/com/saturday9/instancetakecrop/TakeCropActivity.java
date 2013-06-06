/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.saturday9.instancetakecrop;

import java.io.IOException;
import java.util.List;

import com.saturday9.cropimage.BitmapManager;
import com.saturday9.cropimage.CropImageView;
import com.saturday9.cropimage.HighlightView;
import com.saturday9.cropimage.MonitoredActivity;
import com.saturday9.instancetakecrop.R;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;


/**
 * The activity can crop specific region of interest from an image.
 */
public class TakeCropActivity extends MonitoredActivity implements SurfaceHolder.Callback {

	private boolean mWaitingToPick; // Whether we are wait the user to pick a face.
	private boolean mSaving;  // Whether the "save" button is already clicked.

	private CropImageView mImageView;

	private Bitmap mBitmap;
	private final BitmapManager.ThreadSet mDecodingThreads = new BitmapManager.ThreadSet();
	private HighlightView mCrop;

	private SurfaceView mSurfaceView;
	private Camera mCamera;
	private int mCameraDegree;

	private SoundPool mCameraSound;
	private int mTakeBeep, mFocusBeep;
	private ProgressDialog mProgressDialog;
	private Rect mCropRect, mWillTransformRect;
	private boolean mImageCropping;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.takecrop);

		mImageView = (CropImageView) findViewById(R.id.image);
		mSurfaceView = (SurfaceView)findViewById(R.id.surfaceview);
		mSurfaceView.getHolder().addCallback(this);

		mCameraSound = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
		mTakeBeep = mCameraSound.load(getBaseContext(), R.raw.camera_shutter_01, 1);
		mFocusBeep = mCameraSound.load(getBaseContext(), R.raw.camera_focus_beep, 1);
		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		mProgressDialog.setMessage("please wait");
		mProgressDialog.setCancelable(false);

		mImageCropping = true;
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);

		if(mImageView.getHighlightViews().size() == 0) {
			mImageView.setImageBitmapResetBase(mBitmap, true);
			makeDefaultHighlightView();
		}
	}

	private void makeDefaultHighlightView() {
		HighlightView hv = new HighlightView(mImageView);

		View view = findViewById(R.id.layout_surface);
		int width = view.getWidth(), height = view.getHeight();

		Rect imageRect = new Rect(0, 0, width, height);

		// make the default size about 4/5 of the width or height
		int cropWidth = Math.min(width, height) * 4 / 5;
		int cropHeight = cropWidth;

		int x = (width - cropWidth) / 2;
		int y = (height - cropHeight) / 2;

		RectF cropRect = new RectF(x, y, x + cropWidth, y + cropHeight);
		Matrix mImageMatrix = mImageView.getImageMatrix();
		hv.setup(mImageMatrix, imageRect, cropRect, false, false);

		mImageView.getHighlightViews().clear(); // Thong added for rotate
		mImageView.add(hv);

		mImageView.invalidate();
		if (mImageView.getHighlightViews().size() == 1) {
			setCrop(mImageView.getHighlightViews().get(0));
			getCrop().setFocus(true);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		BitmapManager.instance().cancelThreadDecoding(mDecodingThreads);

		if(mCamera != null) {
			mCamera.release();
			mCamera = null;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if(mBitmap != null) mBitmap.recycle();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		setCameraPreview(holder);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if(mCamera != null) {
			mCamera.startPreview();

			Camera.Parameters cameraParams = mCamera.getParameters();
			cameraParams.setJpegQuality(100);
			Size largestSize = getMiddleImageSize(cameraParams.getSupportedPictureSizes());
			cameraParams.setPictureSize(largestSize.width, largestSize.height);
			mCamera.setParameters(cameraParams);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if(mCamera != null) {
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}

	private Size getMiddleImageSize(List<Size> sizes) {
		int listLen = sizes.size();
		try {
			if (listLen % 2 == 0) {
				return sizes.get(sizes.size() / 2);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return sizes.get(0);
	}

	// set camera preview
	private void setCameraPreview(SurfaceHolder holder) {
		try {
			mCamera = Camera.open(Camera.getNumberOfCameras() > 1 ? 0 : 1);
			mCameraDegree = setCameraDisplayOrientation();
			mCamera.setDisplayOrientation(mCameraDegree);

			// set viewholder
			mCamera.setPreviewDisplay(holder);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private int setCameraDisplayOrientation() {
		Camera.CameraInfo info = new Camera.CameraInfo();
		Camera.getCameraInfo(Camera.getNumberOfCameras() > 1 ? 0 : 1, info);
		int rotation = getWindowManager().getDefaultDisplay().getRotation();
		int degrees = 0;
		switch (rotation) {
		case Surface.ROTATION_0: degrees = 0; break;
		case Surface.ROTATION_90: degrees = 90; break;
		case Surface.ROTATION_180: degrees = 180; break;
		case Surface.ROTATION_270: degrees = 270; break;
		}

		int result;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			result = (info.orientation + degrees) % 360;
			result = (360 - result) % 360;  // compensate the mirror
		} else {  // back-facing
			result = (info.orientation - degrees + 360) % 360;
		}
		return result;
	}

	public void takePicture() {
		if(mImageCropping == false) {
			return;
		}
		mImageCropping = false;

		Rect r = getCrop().getCropRect();
		View layoutSurface = findViewById(R.id.layout_surface);
		mCropRect = new Rect(r.left, r.top, r.right, r.bottom);
		mWillTransformRect = new Rect(layoutSurface.getLeft(), layoutSurface.getTop(), layoutSurface.getRight(), layoutSurface.getBottom());
		mCamera.autoFocus(new AutoFocusCallback() {
			@Override
			public void onAutoFocus(boolean success, Camera camera) {
				if(success) {
					playTakeSound();
					mProgressDialog.show();
					mCamera.takePicture(null, null, null, new TakePictureClass());
				}
			}
		});
	}

	public void autoFocus() {
		mCamera.autoFocus(new AutoFocusCallback() {
			@Override
			public void onAutoFocus(boolean success, Camera camera) {
				if(success) {
					playFocusSound();
				}
			}
		});
	}

	private class TakePictureClass implements PictureCallback, Runnable {
		private byte[] data;

		@Override
		public void onPictureTaken(final byte[] data, Camera camera) {
			this.data = data;
			new Thread(this).start();
		}

		@Override
		public void run() {
			try {
				final String saved_img = HelperClass.writeBitmap(data, mCameraDegree, mCropRect, mWillTransformRect);
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mProgressDialog.dismiss();
						mImageCropping = true;
						sendSavedImg(saved_img);
					}
				});
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void sendSavedImg(final String savedImg) {
			Intent intent = new Intent(TakeCropActivity.this, ImageConfirm.class);
			intent.putExtra("saved_img", savedImg);
			startActivity(intent);
		}
	}

	private void playTakeSound() {
		mCameraSound.play(mTakeBeep, 1f, 1f, 0, 0, 1f);
	}

	private void playFocusSound() {
		mCameraSound.play(mFocusBeep, 1f, 1f, 0, 0, 1f);
	}

	public boolean isSaving() {
		return mSaving;
	}

	public boolean isWaitingToPick() {
		return mWaitingToPick;
	}

	public void setWaitingToPick(boolean mWaitingToPick) {
		this.mWaitingToPick = mWaitingToPick;
	}

	public HighlightView getCrop() {
		return mCrop;
	}

	public void setCrop(HighlightView mCrop) {
		this.mCrop = mCrop;
	}
}