package com.saturday9.instancetakecrop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Environment;

public class HelperClass {

	public static String writeBitmap(byte[] data) throws IOException {
		File file = new File(Environment.getExternalStorageDirectory() + "/bookclip/");
		file.mkdir();
		String bitmapPath = file.getPath() + "/" + System.currentTimeMillis() + ".jpg";
		FileOutputStream outStream = new FileOutputStream(bitmapPath);
		outStream.write(data);
		outStream.close();

		return bitmapPath;
	}

	public static String writeBitmap(byte[] data, int cameraDegree, Rect rect, Rect willTransformRect) throws IOException {
		File file = new File(Environment.getExternalStorageDirectory() + "/bookclip/");
		file.mkdir();
		String bitmapPath = file.getPath() + "/" + System.currentTimeMillis() + ".png";

		// bitmap rotation, scaling, crop
		BitmapFactory.Options option = new BitmapFactory.Options();
		option.inSampleSize = 2;
		Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, option);
		Matrix bitmapMatrix = new Matrix();
		bitmapMatrix.setRotate(cameraDegree);
		int x = rect.left, y = rect.top, width = rect.right - rect.left, height = rect.bottom - rect.top;
		Bitmap rotateBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), bitmapMatrix, false);
		// bitmap recycle
		bitmap.recycle();

		Bitmap scaledBitmap = Bitmap.createScaledBitmap(rotateBitmap, willTransformRect.right, willTransformRect.bottom - willTransformRect.top, false);
		// rotatebitmap recycle
		rotateBitmap.recycle();
		
		Bitmap cropBitmap = Bitmap.createBitmap(scaledBitmap, x, y, width, height, null, false);
		// scaledBitmap recycle
		scaledBitmap.recycle();
	
		// file write
		FileOutputStream fos = new FileOutputStream(new File(bitmapPath));
		cropBitmap.compress(CompressFormat.PNG, 100, fos);
		fos.flush();
		fos.close();
		
		// recycle
		cropBitmap.recycle();

		return bitmapPath;
	}
}
