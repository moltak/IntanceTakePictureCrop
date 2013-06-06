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
import java.util.ArrayList;
import java.util.List;

import com.saturday9.cropimage.BitmapManager;
import com.saturday9.cropimage.HighlightView;
import com.saturday9.cropimage.ImageViewTouchBase;
import com.saturday9.cropimage.MonitoredActivity;
import com.saturday9.instancetakecrop.R;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;


/**
 * The activity can crop specific region of interest from an image.
 */
public class TakeCropActivity extends MonitoredActivity implements SurfaceHolder.Callback {

	boolean mWaitingToPick; // Whether we are wait the user to pick a face.
	boolean mSaving;  // Whether the "save" button is already clicked.

	private CropImageView mImageView;

	private Bitmap mBitmap;
	private final BitmapManager.ThreadSet mDecodingThreads = new BitmapManager.ThreadSet();
	HighlightView mCrop;

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

		if(mImageView.mHighlightViews.size() == 0) {
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

		mImageView.mHighlightViews.clear(); // Thong added for rotate
		mImageView.add(hv);

		mImageView.invalidate();
		if (mImageView.mHighlightViews.size() == 1) {
			mCrop = mImageView.mHighlightViews.get(0);
			mCrop.setFocus(true);
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

		Rect r = mCrop.getCropRect();
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
}


class CropImageView extends ImageViewTouchBase {
	ArrayList<HighlightView> mHighlightViews = new ArrayList<HighlightView>();
	HighlightView mMotionHighlightView = null;
	float mLastX, mLastY;
	int mMotionEdge;

	private Context mContext;

	@Override
	protected void onLayout(boolean changed, int left, int top,
			int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		if (mBitmapDisplayed.getBitmap() != null) {
			for (HighlightView hv : mHighlightViews) {
				hv.mMatrix.set(getImageMatrix());
				hv.invalidate();
				if (hv.isFocused()) {
					centerBasedOnHighlightView(hv);
				}
			}
		}
	}

	public CropImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.mContext = context;

	}

	@Override
	protected void zoomTo(float scale, float centerX, float centerY) {
		super.zoomTo(scale, centerX, centerY);
		for (HighlightView hv : mHighlightViews) {
			hv.mMatrix.set(getImageMatrix());
			hv.invalidate();
		}
	}

	@Override
	protected void zoomIn() {
		super.zoomIn();
		for (HighlightView hv : mHighlightViews) {
			hv.mMatrix.set(getImageMatrix());
			hv.invalidate();
		}
	}

	@Override
	protected void zoomOut() {
		super.zoomOut();
		for (HighlightView hv : mHighlightViews) {
			hv.mMatrix.set(getImageMatrix());
			hv.invalidate();
		}
	}

	@Override
	protected void postTranslate(float deltaX, float deltaY) {
		super.postTranslate(deltaX, deltaY);
		for (int i = 0; i < mHighlightViews.size(); i++) {
			HighlightView hv = mHighlightViews.get(i);
			hv.mMatrix.postTranslate(deltaX, deltaY);
			hv.invalidate();
		}
	}

	// According to the event's position, change the focus to the first
	// hitting cropping rectangle.
	private void recomputeFocus(MotionEvent event) {
		for (int i = 0; i < mHighlightViews.size(); i++) {
			HighlightView hv = mHighlightViews.get(i);
			hv.setFocus(false);
			hv.invalidate();
		}

		for (int i = 0; i < mHighlightViews.size(); i++) {
			HighlightView hv = mHighlightViews.get(i);
			int edge = hv.getHit(event.getX(), event.getY());
			if (edge != HighlightView.GROW_NONE) {
				if (!hv.hasFocus()) {
					hv.setFocus(true);
					hv.invalidate();
				}
				break;
			}
		}
		invalidate();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		TakeCropActivity cropImage = (TakeCropActivity) mContext;
		if (cropImage.mSaving) {
			return false;
		}

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			if (cropImage.mWaitingToPick) {
				recomputeFocus(event);
			} else {
				for (int i = 0; i < mHighlightViews.size(); i++) {
					HighlightView hv = mHighlightViews.get(i);
					int edge = hv.getHit(event.getX(), event.getY());
					mMotionEdge = edge;
					if (edge != HighlightView.GROW_NONE) {
						mMotionHighlightView = hv;
						mLastX = event.getX();
						mLastY = event.getY();
						mMotionHighlightView.setMode(
								(edge == HighlightView.MOVE)
								? HighlightView.ModifyMode.Move
										: HighlightView.ModifyMode.Grow);
						break;
					}
				}
			}
			break;
		case MotionEvent.ACTION_UP:
			if (cropImage.mWaitingToPick) {
				for (int i = 0; i < mHighlightViews.size(); i++) {
					HighlightView hv = mHighlightViews.get(i);
					if (hv.hasFocus()) {
						cropImage.mCrop = hv;
						for (int j = 0; j < mHighlightViews.size(); j++) {
							if (j == i) {
								continue;
							}
							mHighlightViews.get(j).setHidden(true);
						}
						centerBasedOnHighlightView(hv);
						((TakeCropActivity) mContext).mWaitingToPick = false;
						return true;
					}
				}
			} else if (mMotionHighlightView != null) {
				centerBasedOnHighlightView(mMotionHighlightView);
				mMotionHighlightView.setMode(HighlightView.ModifyMode.None);
				if(mMotionEdge != HighlightView.MOVE) {
					((TakeCropActivity)mContext).takePicture();
				}
				else {
					((TakeCropActivity)mContext).autoFocus();
				}
			}
			mMotionHighlightView = null;
			break;

		case MotionEvent.ACTION_MOVE:
			if (cropImage.mWaitingToPick) {
				recomputeFocus(event);
			} else if (mMotionHighlightView != null) {
				mMotionHighlightView.handleMotion(mMotionEdge,
						event.getX() - mLastX,
						event.getY() - mLastY);
				mLastX = event.getX();
				mLastY = event.getY();

				if (true) {
					// This section of code is optional. It has some user
					// benefit in that moving the crop rectangle against
					// the edge of the screen causes scrolling but it means
					// that the crop rectangle is no longer fixed under
					// the user's finger.
					ensureVisible(mMotionHighlightView);
				}
			}
			break;
		}

		switch (event.getAction()) {
		case MotionEvent.ACTION_UP:
			center(true, true);
			break;
		case MotionEvent.ACTION_MOVE:
			// if we're not zoomed then there's no point in even allowing
			// the user to move the image around.  This call to center puts
			// it back to the normalized location (with false meaning don't
			// animate).
			if (getScale() == 1F) {
				center(true, true);
			}
			break;
		}

		return true;
	}

	// Pan the displayed image to make sure the cropping rectangle is visible.
	private void ensureVisible(HighlightView hv) {
		Rect r = hv.getDrawRect();

		int panDeltaX1 = Math.max(0, getLeft() - r.left);
		int panDeltaX2 = Math.min(0, getRight() - r.right);

		int panDeltaY1 = Math.max(0, getTop() - r.top);
		int panDeltaY2 = Math.min(0, getBottom() - r.bottom);

		int panDeltaX = panDeltaX1 != 0 ? panDeltaX1 : panDeltaX2;
		int panDeltaY = panDeltaY1 != 0 ? panDeltaY1 : panDeltaY2;

		if (panDeltaX != 0 || panDeltaY != 0) {
			panBy(panDeltaX, panDeltaY);
		}
	}

	// If the cropping rectangle's size changed significantly, change the
	// view's center and scale according to the cropping rectangle.
	private void centerBasedOnHighlightView(HighlightView hv) {
		Rect drawRect = hv.getDrawRect();

		float width = drawRect.width();
		float height = drawRect.height();

		float thisWidth = getWidth();
		float thisHeight = getHeight();

		float z1 = thisWidth / width * .6F;
		float z2 = thisHeight / height * .6F;

		float zoom = Math.min(z1, z2);
		zoom = zoom * this.getScale();
		zoom = Math.max(1F, zoom);
		if ((Math.abs(zoom - getScale()) / zoom) > .1) {
			float [] coordinates = new float[] {hv.getCropRect().centerX(),
					hv.getCropRect().centerY()};
			getImageMatrix().mapPoints(coordinates);
			zoomTo(zoom, coordinates[0], coordinates[1], 300F);
		}

		ensureVisible(hv);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		for (int i = 0; i < mHighlightViews.size(); i++) {
			mHighlightViews.get(i).draw(canvas);
		}
	}

	public void add(HighlightView hv) {
		mHighlightViews.add(hv);
		invalidate();
	}
}